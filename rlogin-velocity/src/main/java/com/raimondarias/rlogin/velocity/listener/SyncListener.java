package com.raimondarias.rlogin.velocity.listener;

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
 * Mantiene, durante toda la conexión de un jugador al proxy, si ya está
 * autenticado — para no volver a pedirle /login al cambiar de backend
 * dentro de la misma red, ni aunque sea no-premium.
 *
 * <p>Un jugador premium entra ya "confiado" (Velocity lo verificó vía
 * Modern Forwarding). Un jugador no-premium se marca como confiado en
 * cuanto un backend avisa por el canal {@code rlogin:sync} de que hizo
 * login o registro correctamente.</p>
 */
public final class SyncListener {

    private final ProxyServer server;
    private final PreLoginListener preLoginListener;

    public SyncListener(ProxyServer server, PreLoginListener preLoginListener) {
        this.server = server;
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
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        SyncMessage message = SyncMessage.decode(event.getData());
        if (message.type() == SyncMessage.Type.AUTHENTICATED) {
            preLoginListener.trustedThisSession().add(message.uuid());
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        preLoginListener.trustedThisSession().remove(uuid);
    }
}
