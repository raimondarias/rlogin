package com.raimondarias.rlogin.paper.command;

import com.raimondarias.rlogin.api.AuthReason;
import com.raimondarias.rlogin.common.auth.RecoveryService;
import com.raimondarias.rlogin.common.security.PasswordPolicy;
import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import com.raimondarias.rlogin.paper.spawn.SpawnManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * {@code /recover <code> <new password>} — spends a one-time code to set a
 * new password and clear any second factor.
 *
 * <p>Usable while frozen, because a player who cannot log in is exactly who
 * needs it. Deliberately not usable once authenticated: someone already in
 * their account should change their password with {@code /changepassword},
 * which asks for the current one, rather than burning a code they may need
 * later.</p>
 */
public final class RecoverCommand implements CommandExecutor {

    private final RLoginPaperPlugin plugin;

    public RecoverCommand(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().get("general.player-only"));
            return true;
        }
        if (!plugin.recoveryService().isEnabled()) {
            player.sendMessage(plugin.messages().get("recovery.disabled"));
            return true;
        }
        if (plugin.authSessions().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.messages().get("recovery.already-logged-in"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.messages().get("recovery.usage"));
            return true;
        }

        String code = args[0];
        String newPassword = args[1];
        plugin.recoveryService().recover(player.getUniqueId(), LoginCommand.ipOf(player), code, newPassword)
                .thenAccept(outcome -> plugin.scheduler().runForPlayer(player, () -> handle(player, outcome)));
        return true;
    }

    private void handle(Player player, RecoveryService.RecoverOutcome outcome) {
        switch (outcome.result()) {
            case SUCCESS -> {
                plugin.authSessions().markAuthenticated(player.getUniqueId(), AuthReason.PASSWORD);
                plugin.fireAuthenticated(player, AuthReason.PASSWORD, true);
                player.sendMessage(plugin.messages().get("recovery.success",
                        Map.of("remaining", String.valueOf(outcome.codesRemaining()))));
                if (outcome.codesRemaining() == 0) {
                    player.sendMessage(plugin.messages().get("recovery.none-left"));
                }
                plugin.notifyProxyAuthenticated(player);
                plugin.spawnManager().teleportForRole(player, SpawnManager.Role.LOGIN);
            }
            case DISABLED -> player.sendMessage(plugin.messages().get("recovery.disabled"));
            case NOT_REGISTERED -> player.sendMessage(plugin.messages().get("login.not-registered"));
            case NO_CODES -> player.sendMessage(plugin.messages().get("recovery.no-codes"));
            case WRONG_CODE -> player.sendMessage(plugin.messages().get("recovery.wrong-code"));
            case THROTTLED -> player.sendMessage(plugin.messages().get("recovery.throttled",
                    Map.of("seconds", String.valueOf(outcome.lockedSecondsRemaining()))));
            case PASSWORD_REJECTED -> player.sendMessage(passwordRejection(outcome.passwordVerdict()));
        }
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

    /**
     * Shows a freshly issued set once, with the warning that there is no
     * second showing. Sent as separate lines so the codes survive being
     * copied out of chat.
     */
    public static void presentCodes(RLoginPaperPlugin plugin, Player player, List<String> codes) {
        if (codes.isEmpty()) {
            return;
        }
        player.sendMessage(plugin.messages().get("recovery.issued-header"));
        for (String code : codes) {
            player.sendMessage(plugin.messages().get("recovery.issued-entry", Map.of("code", code)));
        }
        player.sendMessage(plugin.messages().get("recovery.issued-footer"));
    }
}
