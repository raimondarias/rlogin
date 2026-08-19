package com.raimondarias.rlogin.paper.command;

import com.raimondarias.rlogin.common.auth.AccountService;
import com.raimondarias.rlogin.common.security.PasswordPolicy;
import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class ChangePasswordCommand implements CommandExecutor {

    private final RLoginPaperPlugin plugin;

    public ChangePasswordCommand(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().get("general.player-only"));
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
        plugin.accountService().changePassword(player.getUniqueId(), args[0], args[1]).thenAccept(outcome -> {
            if (outcome.result() == AccountService.ChangePasswordResult.SUCCESS) {
                // Anyone riding an old "remember me" session has to prove themselves again.
                // Changing a password is exactly what you do when you suspect someone else
                // got in, so leaving their shortcut alive would defeat the point.
                plugin.sessionService().forget(player.getUniqueId());
            }
            plugin.scheduler().runForPlayer(player, () -> player.sendMessage(
                    switch (outcome.result()) {
                        case SUCCESS -> plugin.messages().get("changepassword.success");
                        case WRONG_CURRENT_PASSWORD -> plugin.messages().get("changepassword.wrong-password");
                        case PASSWORD_REJECTED -> passwordRejection(outcome.passwordVerdict());
                    }));
        });
        return true;
    }

    /** The same refusals {@code /register} gives, so the rules read as one set. */
    private String passwordRejection(PasswordPolicy.Verdict verdict) {
        return switch (verdict) {
            case TOO_COMMON -> plugin.messages().get("register.password-too-common");
            case SAME_AS_NAME -> plugin.messages().get("register.password-is-name");
            default -> plugin.messages().get("register.password-too-short",
                    Map.of("min", String.valueOf(plugin.config().passwordMinLength())));
        };
    }
}
