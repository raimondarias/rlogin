package com.raimondarias.rlogin.paper.command;

import com.raimondarias.rlogin.common.auth.AccountService;
import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class RegisterCommand implements CommandExecutor {

    private final RLoginPaperPlugin plugin;

    public RegisterCommand(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo un jugador puede usar este comando.");
            return true;
        }
        if (plugin.authSessions().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.messages().get("login.already-logged-in"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.messages().get("register.usage"));
            return true;
        }
        String password = args[0];
        String confirm = args[1];
        String ip = LoginCommand.ipOf(player);

        plugin.accountService().register(player.getUniqueId(), player.getName(), password, confirm)
                .thenAccept(result -> plugin.scheduler().runForPlayer(player, () -> handleResult(player, result, ip)));
        return true;
    }

    private void handleResult(Player player, AccountService.RegisterResult result, String ip) {
        switch (result) {
            case SUCCESS -> {
                plugin.authSessions().markAuthenticated(player.getUniqueId());
                player.sendMessage(plugin.messages().get("register.success", Map.of("player", player.getName())));
                plugin.sessionService().remember(player.getUniqueId(), ip, plugin.getServer().getName());
                plugin.notifyProxyAuthenticated(player);
            }
            case ALREADY_REGISTERED -> player.sendMessage(plugin.messages().get("register.already-registered"));
            case PASSWORDS_DONT_MATCH -> player.sendMessage(plugin.messages().get("register.passwords-dont-match"));
            case INVALID_LENGTH -> player.sendMessage(plugin.messages().get("register.password-too-short",
                    Map.of("min", String.valueOf(plugin.config().passwordMinLength()))));
            case PREMIUM_PROTECTED -> player.sendMessage(plugin.messages().get("register.premium-name-protected"));
        }
    }
}
