package com.raimondarias.rlogin.paper.command;

import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ChangePasswordCommand implements CommandExecutor {

    private final RLoginPaperPlugin plugin;

    public ChangePasswordCommand(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo un jugador puede usar este comando.");
            return true;
        }
        if (!plugin.authSessions().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.messages().get("limbo.action-blocked"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.messages().get("changepassword.usage"));
            return true;
        }
        plugin.accountService().changePassword(player.getUniqueId(), args[0], args[1])
                .thenAccept(ok -> plugin.scheduler().runForPlayer(player, () -> player.sendMessage(
                        plugin.messages().get(ok ? "changepassword.success" : "changepassword.wrong-password"))));
        return true;
    }
}
