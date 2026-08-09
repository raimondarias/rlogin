package com.raimondarias.rlogin.paper.listener;

import com.raimondarias.rlogin.api.AuthReason;
import com.raimondarias.rlogin.common.auth.UuidType;
import com.raimondarias.rlogin.common.util.OfflineUuid;
import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import com.raimondarias.rlogin.paper.spawn.SpawnManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Determines whether a joining player is premium and starts (or skips)
 * freezing, then applies the {@code join}/{@code firstjoin} spawn if one is
 * configured.
 *
 * <p>Premium detection is deliberately simple and uniform: the real UUID is
 * compared against the one the server itself would generate for that same
 * name in offline mode ({@link OfflineUuid}). If they don't match, somebody
 * (Velocity via Modern Forwarding, or the server itself running in
 * online-mode: true) already verified that account against Mojang — no need
 * to repeat that check here, nor to sync anything between processes.</p>
 */
public final class JoinListener implements Listener {

    private final RLoginPaperPlugin plugin;

    public JoinListener(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String username = event.getName();
        UUID uuid = event.getUniqueId();
        String ip = event.getAddress() != null ? event.getAddress().getHostAddress() : "unknown";

        boolean floodgatePremium = plugin.config().floodgateAutoLogin()
                && plugin.floodgate().isAvailable()
                && username.startsWith(plugin.config().floodgatePrefix())
                && plugin.floodgate().isFloodgatePlayer(uuid);

        // Standalone premium verification: HybridAuthListener already
        // cryptographically verified this connection against Mojang at the packet level, before
        // Bukkit ever fired this event, and normally also gave it the real Mojang UUID — in
        // which case the offline-UUID check below already says "premium" on its own. This flag
        // covers the builds where that UUID couldn't be applied. The tracker itself always
        // exists (cheap, no PacketEvents dependency); it's simply never populated when the
        // feature is off or PacketEvents isn't installed.
        boolean hybridVerified = plugin.hybridVerificationTracker().consumeIfVerified(username);

        boolean premium = floodgatePremium || hybridVerified
                || (uuidCanProvePremium() && !OfflineUuid.isOffline(uuid, username));

        if (premium) {
            AuthReason reason = premiumReason(floodgatePremium, hybridVerified);
            debug(username + " (" + uuid + ") let in without a password: " + reason);
            plugin.accountService().upsertPremium(uuid, username, ip).join();
            plugin.authSessions().markAuthenticated(uuid, reason);
            return;
        }

        boolean remembered = plugin.sessionService().isRemembered(uuid, ip).join()
                && !usesTwoFactor(uuid);
        debug(username + " (" + uuid + ") needs a password; remembered session from " + ip + ": " + remembered);
        if (remembered) {
            plugin.authSessions().markAuthenticated(uuid, AuthReason.REMEMBERED_SESSION);
            plugin.sessionService().remember(uuid, ip, plugin.getServer().getName());
        }
        // If neither premium nor remembered, the player stays pending: FreezeListener and
        // the /login, /register commands take it from here once they enter the world.
    }

    /**
     * Whether this account opted into a second factor.
     *
     * <p>"Remember me" trusts an address, and an address is shared: everyone
     * behind the same NAT looks identical, and on a cracked server the UUID
     * comes from the name, so a neighbour typing your name would inherit
     * your session. Somebody who went to the trouble of setting up 2FA has
     * said plainly that an address is not good enough for them, so the
     * shortcut is never applied to their account.</p>
     */
    private boolean usesTwoFactor(UUID uuid) {
        return plugin.accountService().find(uuid).join()
                .map(account -> account.totpEnabled())
                .orElse(false);
    }

    private void debug(String message) {
        if (plugin.config().debug()) {
            plugin.getLogger().info("[debug] " + message);
        }
    }

    /**
     * Whether "this UUID isn't the offline one for this name" still means
     * somebody verified the account against Mojang.
     *
     * <p>It does in every case but one: {@link UuidType#RANDOM} assigns a
     * non-offline UUID to <em>everyone</em>, cracked players included, so
     * under that mode the comparison proves nothing and would hand a free
     * login to anyone who connects. There, only the cryptographic proof
     * recorded by {@code HybridVerificationTracker} counts.</p>
     */
    private boolean uuidCanProvePremium() {
        return !plugin.topology().needsOwnVerification()
                || plugin.config().uuidType() != UuidType.RANDOM;
    }

    /**
     * Which flavour of "already verified, don't ask for a password" this is.
     * Only used to report it; all three skip the login prompt identically.
     * The last branch covers both a Velocity-forwarded connection and a
     * server running {@code online-mode: true} — indistinguishable from here,
     * and the player is told the same thing either way.
     */
    private static AuthReason premiumReason(boolean floodgate, boolean hybridVerified) {
        if (floodgate) {
            return AuthReason.FLOODGATE;
        }
        return hybridVerified ? AuthReason.PREMIUM_MOJANG_API : AuthReason.PREMIUM_FORWARDED;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        SpawnManager.Role joinRole = player.hasPlayedBefore() ? SpawnManager.Role.JOIN : SpawnManager.Role.FIRSTJOIN;
        plugin.spawnManager().teleportForRole(player, joinRole);
        // No spawn assigned for this role -> nothing happens, player stays where they
        // last disconnected, which is Bukkit's own default behavior anyway.

        if (plugin.authSessions().isAuthenticated(uuid)) {
            announceAutoLogin(player, plugin.authSessions().reasonFor(uuid));
            return;
        }
        if (player.hasPermission("rlogin.bypass")) {
            plugin.authSessions().markAuthenticated(uuid, AuthReason.BYPASS_PERMISSION);
            return;
        }
        plugin.limboService().freeze(player);
    }

    /**
     * Tells a player who wasn't asked for a password why. Being let straight
     * in with no explanation is indistinguishable from rLogin not being
     * installed, and the two silent paths are silent for different reasons,
     * so they say different things.
     *
     * <p>Nothing is said for {@link AuthReason#PASSWORD} (the command already
     * confirmed it), {@link AuthReason#FORCED_BY_ADMIN} (same) or
     * {@link AuthReason#BYPASS_PERMISSION} (that's for NPCs and bots, which
     * have nobody to tell).</p>
     */
    private void announceAutoLogin(Player player, AuthReason reason) {
        if (reason == null) {
            return;
        }
        if (reason.isAutomaticPremium()) {
            player.sendMessage(plugin.messages().get("premium.auto-login-message"));
        } else if (reason == AuthReason.REMEMBERED_SESSION) {
            player.sendMessage(plugin.messages().get("login.session-restored",
                    Map.of("player", player.getName())));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.authSessions().forget(event.getPlayer().getUniqueId());
    }
}
