package com.raimondarias.rlogin.paper.listener;

import com.raimondarias.rlogin.common.sync.SyncMessage;
import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Recibe el aviso {@code TRUSTED} de Velocity: el proxy ya considera
 * autenticado a este jugador (premium, o porque hizo login en otro backend
 * de la misma red), así que este servidor puede saltarse el login sin
 * volver a preguntarle.
 */
public final class SyncMessageListener implements PluginMessageListener {

    private final RLoginPaperPlugin plugin;

    public SyncMessageListener(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        SyncMessage decoded = SyncMessage.decode(message);
        if (decoded.type() == SyncMessage.Type.TRUSTED) {
            plugin.authSessions().markAuthenticated(decoded.uuid());
        }
    }
}
