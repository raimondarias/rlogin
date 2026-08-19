package com.raimondarias.rlogin.paper.command;

import com.raimondarias.rlogin.api.AuthReason;
import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.UUID;

/**
 * Second step of a new-device login. After {@code /login} succeeds from an
 * address this account has never used before, the player stays frozen until
 * they prove control of the account again with {@code /confirm <password>
 * [2fa-code]}; only then is the device trusted (see {@code device-memory}
 * in config.yml) and the freeze lifted.
 */
public final class ConfirmCommand implements CommandExecutor {

    private final RLoginPaperPlugin plugin;

    public ConfirmCommand(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().get("general.player-only"));
            return true;
        }
        UUID uuid = player.getUniqueId();
        if (plugin.authSessions().isAuthenticated(uuid)) {
            player.sendMessage(plugin.messages().get("login.already-logged-in"));
            return true;
        }
        if (!plugin.authSessions().isAwaitingDeviceConfirmation(uuid)) {
            // Either the window never opened (nothing to confirm) or it closed;
            // either way the honest answer is the same: log in first.
            player.sendMessage(plugin.messages().get("device.not-pending"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.messages().get("device.confirm-usage"));
            return true;
        }
        String password = args[0];
        String totp = args.length > 1 ? args[1] : null;
        String ip = LoginCommand.ipOf(player);
        plugin.accountService().confirmDevice(uuid, password, totp)
                .thenAccept(ok -> plugin.scheduler().runForPlayer(player, () -> {
                    if (!ok) {
                        // The window stays open, so this reads as "try again" rather
                        // than throwing them out of the flow.
                        player.sendMessage(plugin.messages().get("device.confirmation-failed"));
                        return;
                    }
                    plugin.authSessions().clearDeviceConfirmation(uuid);
                    trustThisDevice(player, uuid, ip);
                    LoginCommand.completeLogin(plugin, player, ip, AuthReason.PASSWORD);
                    player.sendMessage(plugin.messages().get("device.confirmed"));
                }));
        return true;
    }

    private void trustThisDevice(Player player, UUID uuid, String ip) {
        plugin.storage().rememberIp(uuid, ip, Instant.now());
        plugin.storage().pruneKnownIps(uuid, plugin.config().deviceMemoryMaxKnownIps());
    }
}
