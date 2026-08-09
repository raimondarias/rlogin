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
    private final LobbyListener lobbyListener;
    private final BackendCheck backendCheck;

    public SyncListener(ProxyServer server, RLoginConfig config, PreLoginListener preLoginListener,
                         LobbyListener lobbyListener, BackendCheck backendCheck) {
        this.backendCheck = backendCheck;
        this.server = server;
        this.config = config;
        this.preLoginListener = preLoginListener;
        this.lobbyListener = lobbyListener;
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
        backendCheck.heardFrom(message.uuid(), sourceConnection.getServerInfo().getName());
        // The backend just told us this player is in. Where they go from here is entirely
        // redirect:'s business, so hand it over rather than deciding anything here.
        server.getPlayer(message.uuid()).ifPresent(lobbyListener::onAuthenticated);
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
