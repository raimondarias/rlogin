package com.raimondarias.rlogin.paper.command;

import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class LogoutCommand implements CommandExecutor {

    private final RLoginPaperPlugin plugin;

    public LogoutCommand(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo un jugador puede usar este comando.");
            return true;
        }
        if (!plugin.authSessions().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.messages().get("logout.not-logged-in"));
            return true;
        }
        plugin.authSessions().forget(player.getUniqueId());
        plugin.sessionService().forget(player.getUniqueId());
        player.sendMessage(plugin.messages().get("logout.success"));
        plugin.limboService().freeze(player);
        return true;
    }
}
