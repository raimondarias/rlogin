package com.raimondarias.rlogin.paper.command;

import com.raimondarias.rlogin.api.AuthReason;
import com.raimondarias.rlogin.common.auth.AccountService;
import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import com.raimondarias.rlogin.paper.spawn.SpawnManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class LoginCommand implements CommandExecutor {

    private final RLoginPaperPlugin plugin;

    public LoginCommand(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().get("general.player-only"));
            return true;
        }
        if (plugin.authSessions().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.messages().get("login.already-logged-in"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.messages().get("login.usage"));
            return true;
        }
        String password = args[0];
        String totp = args.length > 1 ? args[1] : null;
        attemptLogin(plugin, player, password, totp);
        return true;
    }

    public static void attemptLogin(RLoginPaperPlugin plugin, Player player, String password, String totp) {
        String ip = ipOf(player);
        plugin.accountService().login(player.getUniqueId(), password, totp, ip)
                .thenAccept(outcome -> plugin.scheduler().runForPlayer(player,
                        () -> handleOutcome(plugin, player, outcome, ip)));
    }

    public static void handleOutcome(RLoginPaperPlugin plugin, Player player, AccountService.LoginOutcome outcome, String ip) {
        switch (outcome.result()) {
            case SUCCESS -> {
                if (plugin.config().deviceMemoryEnabled()) {
                    // First login from this address: the password proved the account,
                    // the /confirm step proves the device before it is trusted.
                    UUID uuid = player.getUniqueId();
                    plugin.storage().isKnownIp(uuid, ip).thenAccept(known ->
                            plugin.scheduler().runForPlayer(player, () -> {
                                if (known) {
                                    completeLogin(plugin, player, ip, AuthReason.PASSWORD);
                                } else {
                                    long expiry = Instant.now().plusSeconds(
                                                    plugin.config().deviceMemoryConfirmTimeoutSeconds())
                                            .toEpochMilli();
                                    plugin.authSessions().requireDeviceConfirmation(uuid, expiry);
                                    player.sendMessage(plugin.messages().get("device.new-device"));
                                }
                            }));
                } else {
                    completeLogin(plugin, player, ip, AuthReason.PASSWORD);
                }
            }
            case WRONG_PASSWORD -> player.sendMessage(plugin.messages().get("login.wrong-password",
                    Map.of("attempts", String.valueOf(outcome.attemptsLeft()))));
            case WRONG_TOTP -> player.sendMessage(plugin.messages().get("login.wrong-totp"));
            case NOT_REGISTERED -> player.sendMessage(plugin.messages().get("login.not-registered"));
            case LOCKED -> player.sendMessage(plugin.messages().get("security.locked-out",
                    Map.of("seconds", String.valueOf(outcome.lockedSecondsRemaining()))));
            case NEEDS_TOTP -> player.sendMessage(plugin.messages().get("login.need-totp"));
            case PREMIUM_NO_PASSWORD -> player.sendMessage(plugin.messages().get("premium.info-premium"));
        }
    }

    public static String ipOf(Player player) {
        return player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
    }

    /**
     * Everything a successful unlock does, in one place — used by /login on
     * a known device, /confirm, and /session code redemption, so the three
     * never drift apart. Must run on the player's own thread.
     */
    public static void completeLogin(RLoginPaperPlugin plugin, Player player, String ip, AuthReason reason) {
        plugin.authSessions().markAuthenticated(player.getUniqueId(), reason);
        plugin.fireAuthenticated(player, reason, true);
        player.sendMessage(plugin.messages().get("login.success", Map.of("player", player.getName())));
        plugin.sessionService().remember(player.getUniqueId(), ip, plugin.getServer().getName());
        plugin.notifyProxyAuthenticated(player);
        plugin.spawnManager().teleportForRole(player, SpawnManager.Role.LOGIN);
    }
}
