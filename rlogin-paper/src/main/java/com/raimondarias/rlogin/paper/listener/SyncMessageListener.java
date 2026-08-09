package com.raimondarias.rlogin.paper.listener;

import com.raimondarias.rlogin.api.AuthReason;
import com.raimondarias.rlogin.common.sync.SyncMessage;
import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Receives Velocity's {@code TRUSTED} notice: the proxy already considers
 * this player authenticated (premium, or because they logged in on another
 * backend of the same network), so this server can skip the login prompt
 * entirely.
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
            // The proxy doesn't say which of the two it was (premium, or a password typed on
            // another backend), and by the time this arrives the player is already in — so no
            // join message either way; reported as forwarded, which is how it reached us.
            plugin.authSessions().markAuthenticated(decoded.uuid(), AuthReason.PREMIUM_FORWARDED);
        }
    }
}
