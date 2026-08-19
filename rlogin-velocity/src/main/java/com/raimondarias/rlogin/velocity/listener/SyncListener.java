package com.raimondarias.rlogin.velocity.listener;

import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.raimondarias.rlogin.common.sync.SyncMessage;
import com.raimondarias.rlogin.velocity.RLoginVelocityPlugin;
import com.velocitypowered.api.event.Subscribe;

import java.util.Optional;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;

import java.util.UUID;

/**
 * Keeps track, for the whole duration of a player's connection to the
 * proxy, of whether they're already authenticated — so they're never asked
 * to /login again when switching backends within the same network, premium
 * or not.
 *
 * <p>A premium player starts out already "trusted" (Velocity verified them
 * via Modern Forwarding). A non-premium player becomes trusted as soon as a
 * backend reports, over the {@code rlogin:sync} channel, that they logged
 * in or registered successfully.</p>
 *
 * <p>If an authentication lobby is configured ({@code lobby.auth-server} /
 * {@code lobby.default-server}), this also auto-transfers a player from the
 * auth lobby to the default server the moment they authenticate there.</p>
 */
public final class SyncListener {

    private final ProxyServer server;
    private final RLoginConfig config;
    private final PreLoginListener preLoginListener;
    private final LobbyListener lobbyListener;
    private final BackendCheck backendCheck;
    private final org.slf4j.Logger logger;
    private volatile boolean warnedUntrusted;

    public SyncListener(ProxyServer server, RLoginConfig config, PreLoginListener preLoginListener,
                         LobbyListener lobbyListener, BackendCheck backendCheck, org.slf4j.Logger logger) {
        this.backendCheck = backendCheck;
        this.server = server;
        this.config = config;
        this.preLoginListener = preLoginListener;
        this.lobbyListener = lobbyListener;
        this.logger = logger;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        if (preLoginListener.wasForcedPremium(player.getUsername())) {
            preLoginListener.trustedThisSession().add(player.getUniqueId());
        }
        preLoginListener.forgetDecision(player.getUsername());
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        // Recorded whether they are trusted yet or not: redirect.last-server needs to know
        // where they were, and by the time they log in they are already somewhere.
        player.getCurrentServer().ifPresent(connection ->
                lobbyListener.rememberServer(player, connection.getServerInfo().getName()));
        if (!preLoginListener.trustedThisSession().contains(player.getUniqueId())) {
            return;
        }
        // No previous server means they just arrived on the network; anything else is a
        // switch. The backends can't tell those apart — each one only ever sees a join —
        // so without this every hop would greet the player again.
        boolean firstServer = event.getPreviousServer() == null;
        String secret = config.syncSecret();
        if (secret.isBlank()) {
            warnUntrustedOnce();
            return;
        }
        player.getCurrentServer().ifPresent(connection ->
                connection.sendPluginMessage(RLoginVelocityPlugin.SYNC_CHANNEL,
                        new SyncMessage(SyncMessage.Type.TRUSTED, player.getUniqueId(), firstServer).encode(secret)));
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(RLoginVelocityPlugin.SYNC_CHANNEL)) {
            return;
        }
        if (!(event.getSource() instanceof ServerConnection sourceConnection)) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        String secret = config.syncSecret();
        if (secret.isBlank()) {
            warnUntrustedOnce();
            return;
        }
        Optional<SyncMessage> decoded = SyncMessage.decode(event.getData(), secret);
        if (decoded.isEmpty()) {
            warnUntrustedOnce();
            return;
        }
        SyncMessage message = decoded.get();
        if (message.type() != SyncMessage.Type.AUTHENTICATED) {
            return;
        }
        preLoginListener.trustedThisSession().add(message.uuid());
        backendCheck.heardFrom(message.uuid(), sourceConnection.getServerInfo().getName());
        // The backend just told us this player is in. Where they go from here is entirely
        // redirect:'s business, so hand it over rather than deciding anything here.
        server.getPlayer(message.uuid()).ifPresent(lobbyListener::onAuthenticated);
    }

    /**
     * A flood of forged or unsigned messages must not become a log flood:
     * say it once, and only when it matters (no secret, or a bad signature).
     */
    private void warnUntrustedOnce() {
        if (warnedUntrusted) {
            return;
        }
        warnedUntrusted = true;
        logger.warn("Ignoring an untrusted rlogin:sync message. Check that sync.secret is set to "
                + "the same value in this proxy's config.yml and in every backend's config.yml.");
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        preLoginListener.trustedThisSession().remove(uuid);
        lobbyListener.forget(uuid);
        backendCheck.playerGone(uuid, event.getPlayer().getCurrentServer()
                .map(connection -> connection.getServer()));
    }
}
