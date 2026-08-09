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
            sender.sendMessage(plugin.messages().get("general.player-only"));
            return true;
        }
        if (!plugin.config().totpEnabled()) {
            player.sendMessage(plugin.messages().get("totp.server-disabled"));
            return true;
        }
        if (!plugin.authSessions().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.messages().get("limbo.action-blocked"));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(plugin.messages().get("totp.usage"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "enable" -> enable(player);
            case "confirm" -> confirm(player, args);
            case "disable" -> disable(player);
            default -> player.sendMessage(plugin.messages().get("totp.usage"));
        }
        return true;
    }

    /**
     * Refuses to start over for someone who already finished setting 2FA up.
     * Without that check, a second {@code /2fa enable} silently replaces the
     * secret and every code their authenticator app produces stops working,
     * with nothing on screen to explain why.
     */
    private void enable(Player player) {
        plugin.accountService().find(player.getUniqueId()).thenAccept(existing -> {
            if (existing.isPresent() && existing.get().totpEnabled()) {
                plugin.scheduler().runForPlayer(player, () ->
                        player.sendMessage(plugin.messages().get("totp.already-enabled")));
                return;
            }
            plugin.accountService().beginTotpSetup(player.getUniqueId()).thenAccept(secret ->
                    plugin.scheduler().runForPlayer(player, () -> {
                        player.sendMessage(plugin.messages().get("totp.setup-secret", Map.of("secret", secret)));
                        player.sendMessage(Totp.buildOtpAuthUri(plugin.config().totpIssuer(), player.getName(), secret));
                    }));
        });
    }

    private void confirm(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.messages().get("totp.confirm-usage"));
            return;
        }
        plugin.accountService().confirmTotp(player.getUniqueId(), args[1]).thenAccept(ok ->
                plugin.scheduler().runForPlayer(player, () -> player.sendMessage(
                        // A wrong code is a wrong code, not a usage mistake — saying "here's how to
                        // type it" to someone who typed it correctly but stale is just confusing.
                        plugin.messages().get(ok ? "totp.enabled" : "login.wrong-totp"))));
    }

    private void disable(Player player) {
        plugin.accountService().find(player.getUniqueId()).thenAccept(existing -> {
            if (existing.isEmpty() || !existing.get().totpEnabled()) {
                plugin.scheduler().runForPlayer(player, () ->
                        player.sendMessage(plugin.messages().get("totp.not-enabled")));
                return;
            }
            plugin.accountService().disableTotp(player.getUniqueId()).thenRun(() ->
                    plugin.scheduler().runForPlayer(player, () ->
                            player.sendMessage(plugin.messages().get("totp.disabled"))));
        });
    }
}
