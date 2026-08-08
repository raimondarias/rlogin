package com.raimondarias.rlogin.paper.command;

import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PremiumInfoCommand implements CommandExecutor {

    private final RLoginPaperPlugin plugin;

    public PremiumInfoCommand(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo un jugador puede usar este comando.");
            return true;
        }
        plugin.accountService().find(player.getUniqueId()).thenAccept(opt ->
                plugin.scheduler().runForPlayer(player, () -> {
                    boolean premium = opt.isPresent() && opt.get().premium();
                    player.sendMessage(plugin.messages().get(premium ? "premium.info-premium" : "premium.info-cracked"));
                }));
        return true;
    }
}
