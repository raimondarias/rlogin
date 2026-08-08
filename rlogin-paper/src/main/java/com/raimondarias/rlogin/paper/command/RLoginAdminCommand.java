package com.raimondarias.rlogin.paper.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import com.raimondarias.rlogin.paper.RLoginPaperPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /rlogin ...} — router hacia los mismos comandos que sus alias
 * cortos ({@code /login}, {@code /register}...) más los subcomandos de
 * administración ({@code rlogin.admin}).
 */
public final class RLoginAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBCOMMANDS =
            List.of("login", "register", "changepassword", "logout", "2fa", "premium");
    private static final List<String> ADMIN_SUBCOMMANDS =
            List.of("reload", "unregister", "forcelogin", "migrate", "info", "lang");

    private final RLoginPaperPlugin plugin;
    private final LoginCommand loginCommand;
    private final RegisterCommand registerCommand;
    private final ChangePasswordCommand changePasswordCommand;
    private final LogoutCommand logoutCommand;
    private final TotpCommand totpCommand;
    private final PremiumInfoCommand premiumInfoCommand;

    public RLoginAdminCommand(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
        this.loginCommand = new LoginCommand(plugin);
        this.registerCommand = new RegisterCommand(plugin);
        this.changePasswordCommand = new ChangePasswordCommand(plugin);
        this.logoutCommand = new LogoutCommand(plugin);
        this.totpCommand = new TotpCommand(plugin);
        this.premiumInfoCommand = new PremiumInfoCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("rLogin — /rlogin <" + String.join("|", PLAYER_SUBCOMMANDS)
                    + (sender.hasPermission("rlogin.admin") ? "|" + String.join("|", ADMIN_SUBCOMMANDS) : "") + ">");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        return switch (sub) {
            case "login" -> loginCommand.onCommand(sender, command, label, rest);
            case "register" -> registerCommand.onCommand(sender, command, label, rest);
            case "changepassword" -> changePasswordCommand.onCommand(sender, command, label, rest);
            case "logout" -> logoutCommand.onCommand(sender, command, label, rest);
            case "2fa" -> totpCommand.onCommand(sender, command, label, rest);
            case "premium" -> premiumInfoCommand.onCommand(sender, command, label, rest);
            case "reload" -> adminOnly(sender, () -> {
                plugin.reload();
                sender.sendMessage(plugin.messages().get("admin.reloaded"));
            });
            case "unregister" -> adminOnly(sender, () -> unregister(sender, rest));
            case "forcelogin" -> adminOnly(sender, () -> forceLogin(sender, rest));
            case "migrate" -> adminOnly(sender, () -> migrate(sender, rest));
            case "info" -> adminOnly(sender, () -> info(sender, rest));
            case "lang" -> adminOnly(sender, () -> sender.sendMessage(
                    "Cambia general.language en config.yml y ejecuta /rlogin reload."));
            default -> {
                sender.sendMessage("Subcomando desconocido: " + sub);
                yield true;
            }
        };
    }

    private boolean adminOnly(CommandSender sender, Runnable action) {
        if (!sender.hasPermission("rlogin.admin")) {
            sender.sendMessage(plugin.messages().get("admin.no-permission"));
            return true;
        }
        action.run();
        return true;
    }

    private void unregister(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("/rlogin unregister <jugador>");
            return;
        }
        UUID uuid = resolveUuid(args[0]);
        plugin.accountService().unregister(uuid).thenRun(() ->
                sender.sendMessage(plugin.messages().get("admin.unregistered", Map.of("player", args[0]))));
    }

    private void forceLogin(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("/rlogin forcelogin <jugador>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.messages().get("admin.player-not-found"));
            return;
        }
        plugin.accountService().forceLogin(target.getUniqueId(), LoginCommand.ipOf(target)).thenAccept(account ->
                plugin.scheduler().runForPlayer(target, () -> {
                    plugin.authSessions().markAuthenticated(target.getUniqueId());
                    target.sendMessage(plugin.messages().get("login.success", Map.of("player", target.getName())));
                    sender.sendMessage(plugin.messages().get("admin.force-logged-in", Map.of("player", target.getName())));
                    plugin.notifyProxyAuthenticated(target);
                }));
    }

    private void migrate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("/rlogin migrate <authme|nlogin|jpremium> <ruta-o-jdbc>");
            return;
        }
        var importer = plugin.importerRegistry().get(args[0]);
        if (importer.isEmpty()) {
            sender.sendMessage("Importador desconocido: " + args[0]);
            return;
        }
        sender.sendMessage(plugin.messages().get("admin.migration-started", Map.of("plugin", importer.get().displayName())));
        String source = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        plugin.migrationService().importFrom(importer.get(), source).whenComplete((result, error) -> {
            if (error != null) {
                sender.sendMessage(plugin.messages().get("admin.migration-failed", Map.of("error", String.valueOf(error.getMessage()))));
            } else {
                sender.sendMessage(plugin.messages().get("admin.migration-done", Map.of("count", String.valueOf(result.imported()))));
            }
        });
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("/rlogin info <jugador>");
            return;
        }
        UUID uuid = resolveUuid(args[0]);
        plugin.accountService().find(uuid).thenAccept(opt -> {
            if (opt.isEmpty()) {
                sender.sendMessage(plugin.messages().get("admin.player-not-found"));
                return;
            }
            var account = opt.get();
            sender.sendMessage(plugin.messages().get("admin.info", Map.of(
                    "player", account.username(),
                    "premium", String.valueOf(account.premium()),
                    "totp", String.valueOf(account.totpEnabled()),
                    "last-login", String.valueOf(account.lastLoginAt()))));
        });
    }

    private UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.getUniqueId();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = sender.hasPermission("rlogin.admin")
                ? java.util.stream.Stream.concat(PLAYER_SUBCOMMANDS.stream(), ADMIN_SUBCOMMANDS.stream()).toList()
                : PLAYER_SUBCOMMANDS;
        return options.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }
}
