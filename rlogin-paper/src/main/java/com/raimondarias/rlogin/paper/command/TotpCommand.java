package com.raimondarias.rlogin.paper.command;

import com.raimondarias.rlogin.common.security.Totp;
import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

public final class TotpCommand implements CommandExecutor {

    private final RLoginPaperPlugin plugin;

    public TotpCommand(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo un jugador puede usar este comando.");
            return true;
        }
        if (!plugin.config().totpEnabled()) {
            player.sendMessage(plugin.messages().get("admin.no-permission"));
            return true;
        }
        if (!plugin.authSessions().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.messages().get("limbo.action-blocked"));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("/2fa <enable|disable|confirm> [código]");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "enable" -> enable(player);
            case "confirm" -> confirm(player, args);
            case "disable" -> disable(player);
            default -> player.sendMessage("/2fa <enable|disable|confirm> [código]");
        }
        return true;
    }

    private void enable(Player player) {
        plugin.accountService().beginTotpSetup(player.getUniqueId()).thenAccept(secret ->
                plugin.scheduler().runForPlayer(player, () -> {
                    player.sendMessage(plugin.messages().get("totp.setup-secret", Map.of("secret", secret)));
                    player.sendMessage(Totp.buildOtpAuthUri(plugin.config().totpIssuer(), player.getName(), secret));
                }));
    }

    private void confirm(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.messages().get("totp.confirm-usage"));
            return;
        }
        plugin.accountService().confirmTotp(player.getUniqueId(), args[1]).thenAccept(ok ->
                plugin.scheduler().runForPlayer(player, () -> player.sendMessage(
                        plugin.messages().get(ok ? "totp.enabled" : "totp.confirm-usage"))));
    }

    private void disable(Player player) {
        plugin.accountService().disableTotp(player.getUniqueId()).thenRun(() ->
                plugin.scheduler().runForPlayer(player, () -> player.sendMessage(plugin.messages().get("totp.disabled"))));
    }
}
