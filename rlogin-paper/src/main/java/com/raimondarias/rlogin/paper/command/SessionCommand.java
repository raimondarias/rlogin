package com.raimondarias.rlogin.paper.command;

import com.raimondarias.rlogin.api.AuthReason;
import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Cross-device sign-in. While authenticated, {@code /session} mints a
 * short-lived, single-use code (see {@code session.transfer-token-minutes});
 * from another device, {@code /session <code>} spends it instead of the
 * password — and, like /confirm, trusts that device from then on.
 */
public final class SessionCommand implements CommandExecutor {

    private final RLoginPaperPlugin plugin;

    public SessionCommand(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().get("general.player-only"));
            return true;
        }
        if (args.length == 0) {
            generateCode(player, player.getUniqueId());
        } else {
            redeemCode(player, player.getUniqueId(), args[0]);
        }
        return true;
    }

    /** Authenticated player: mint a code for the device they're about to use. */
    private void generateCode(Player player, UUID uuid) {
        if (!plugin.authSessions().isAuthenticated(uuid)) {
            player.sendMessage(plugin.messages().get("session.usage"));
            return;
        }
        plugin.sessionService().issueTransferToken(uuid)
                .thenAccept(token -> plugin.scheduler().runForPlayer(player, () -> {
                    if (token == null) {
                        player.sendMessage(plugin.messages().get("session.unavailable"));
                        return;
                    }
                    player.sendMessage(plugin.messages().get("session.generated", Map.of(
                            "code", token,
                            "minutes", String.valueOf(plugin.config().transferTokenMinutes()))));
                }));
    }

    /** Player in limbo: spend a code minted on a device they already trust. */
    private void redeemCode(Player player, UUID uuid, String code) {
        if (plugin.authSessions().isAuthenticated(uuid)) {
            player.sendMessage(plugin.messages().get("login.already-logged-in"));
            return;
        }
        String ip = LoginCommand.ipOf(player);
        plugin.sessionService().redeemTransferToken(uuid, code)
                .thenAccept(ok -> plugin.scheduler().runForPlayer(player, () -> {
                    if (!ok) {
                        player.sendMessage(plugin.messages().get("session.invalid"));
                        return;
                    }
                    // A spent code proves the device is theirs: it is trusted, and any
                    // open new-device confirmation for this address is satisfied by it.
                    plugin.authSessions().clearDeviceConfirmation(uuid);
                    plugin.storage().rememberIp(uuid, ip, Instant.now());
                    plugin.storage().pruneKnownIps(uuid, plugin.config().deviceMemoryMaxKnownIps());
                    LoginCommand.completeLogin(plugin, player, ip, AuthReason.SESSION_TOKEN);
                    player.sendMessage(plugin.messages().get("device.confirmed"));
                }));
    }
}
