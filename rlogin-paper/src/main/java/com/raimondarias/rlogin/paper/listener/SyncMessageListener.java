package com.raimondarias.rlogin.paper.listener;

import com.raimondarias.rlogin.api.AuthReason;
import com.raimondarias.rlogin.common.sync.SyncMessage;
import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Optional;

/**
 * Receives Velocity's {@code TRUSTED} notice: the proxy already considers
 * this player authenticated (premium, or because they logged in on another
 * backend of the same network), so this server can skip the login prompt
 * entirely.
 *
 * <p>The message is only trusted after its HMAC signature checks out under
 * {@code sync.secret}. Plugin messages travel over the player's own
 * connection and Bukkit reports every one of them with the player as the
 * sender, so without the signature a malicious client could simply declare
 * itself {@code TRUSTED} and skip the password. Any message that fails the
 * check — or arrives while no secret is configured — is ignored.</p>
 */
public final class SyncMessageListener implements PluginMessageListener {

    private final RLoginPaperPlugin plugin;
    private volatile boolean warned;

    public SyncMessageListener(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        String secret = plugin.config().syncSecret();
        if (secret.isBlank()) {
            warnOnce("rlogin:sync is not protected by a shared secret (sync.secret is empty in "
                    + "config.yml), so this backend ignores every message on that channel. Nobody "
                    + "gets the \"skip login on server switch\" shortcut, and no forged message can "
                    + "authenticate anyone. Set the same sync.secret here and on the proxy to "
                    + "re-enable it.");
            return;
        }
        Optional<SyncMessage> decoded = SyncMessage.decode(message, secret);
        if (decoded.isEmpty()) {
            warnOnce("Rejected a rlogin:sync message with an invalid signature. Check that "
                    + "sync.secret is identical in this backend's config.yml and in the proxy's.");
            return;
        }
        SyncMessage sync = decoded.get();
        if (sync.type() == SyncMessage.Type.TRUSTED) {
            // The proxy doesn't say which of the two it was (premium, or a password typed on
            // another backend), and by the time this arrives the player is already in — so no
            // join message either way; reported as forwarded, which is how it reached us.
            plugin.authSessions().markAuthenticated(sync.uuid(), AuthReason.PREMIUM_FORWARDED);
            if (!sync.firstServer()) {
                // A switch, not a new connection: an earlier backend already said hello, and
                // JoinListener is holding its greeting for exactly this answer.
                plugin.authSessions().markAlreadyGreeted(sync.uuid());
            }
        }
    }

    /** A flood of forged messages must not become a log flood: say it once. */
    private void warnOnce(String message) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().warning(message);
    }
}
