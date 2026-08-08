package com.raimondarias.rlogin.velocity.listener;

import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.raimondarias.rlogin.common.sync.SyncMessage;
import com.raimondarias.rlogin.velocity.RLoginVelocityPlugin;
import com.velocitypowered.api.event.Subscribe;
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

    public SyncListener(ProxyServer server, RLoginConfig config, PreLoginListener preLoginListener) {
        this.server = server;
        this.config = config;
        this.preLoginListener = preLoginListener;
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
        if (!preLoginListener.trustedThisSession().contains(player.getUniqueId())) {
            return;
        }
        player.getCurrentServer().ifPresent(connection ->
                connection.sendPluginMessage(RLoginVelocityPlugin.SYNC_CHANNEL,
                        new SyncMessage(SyncMessage.Type.TRUSTED, player.getUniqueId()).encode()));
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

        SyncMessage message = SyncMessage.decode(event.getData());
        if (message.type() != SyncMessage.Type.AUTHENTICATED) {
            return;
        }
        preLoginListener.trustedThisSession().add(message.uuid());
        maybeLeaveAuthLobby(message.uuid(), sourceConnection);
    }

    /** If this login just happened on the configured auth lobby, transfer the player onward. */
    private void maybeLeaveAuthLobby(UUID uuid, ServerConnection sourceConnection) {
        String authServer = config.authLobbyServer();
        String defaultServer = config.defaultLobbyServer();
        if (authServer.isBlank() || defaultServer.isBlank()) {
            return;
        }
        if (!authServer.equalsIgnoreCase(sourceConnection.getServerInfo().getName())) {
            return;
        }
        server.getPlayer(uuid).ifPresent(player ->
                server.getServer(defaultServer).ifPresent(target ->
                        player.createConnectionRequest(target).fireAndForget()));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        preLoginListener.trustedThisSession().remove(uuid);
    }
}
