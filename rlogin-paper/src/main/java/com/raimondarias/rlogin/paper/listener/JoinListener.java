package com.raimondarias.rlogin.paper.listener;

import com.raimondarias.rlogin.common.util.OfflineUuid;
import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import com.raimondarias.rlogin.paper.spawn.SpawnManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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

        boolean premium = floodgatePremium || !OfflineUuid.isOffline(uuid, username);

        if (premium) {
            plugin.accountService().upsertPremium(uuid, username, ip).join();
            plugin.authSessions().markAuthenticated(uuid);
            return;
        }

        boolean remembered = plugin.sessionService().isRemembered(uuid, ip).join();
        if (remembered) {
            plugin.authSessions().markAuthenticated(uuid);
            plugin.sessionService().remember(uuid, ip, plugin.getServer().getName());
        }
        // If neither premium nor remembered, the player stays pending: FreezeListener and
        // the /login, /register commands take it from here once they enter the world.
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
            return;
        }
        if (player.hasPermission("rlogin.bypass")) {
            plugin.authSessions().markAuthenticated(uuid);
            return;
        }
        plugin.limboService().freeze(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.authSessions().forget(event.getPlayer().getUniqueId());
    }
}
