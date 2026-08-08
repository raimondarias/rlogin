package com.raimondarias.rlogin.paper.command;

import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import com.raimondarias.rlogin.paper.spawn.SpawnManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /rlogin ...} — router to the same commands as their short aliases
 * ({@code /login}, {@code /register}...) plus admin subcommands
 * ({@code rlogin.admin}), including spawn point management.
 */
public final class RLoginAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBCOMMANDS =
            List.of("login", "register", "changepassword", "logout", "2fa", "premium");
    private static final List<String> ADMIN_SUBCOMMANDS =
            List.of("reload", "unregister", "forcelogin", "migrate", "info", "lang", "spawn");
    private static final List<String> SPAWN_ACTIONS =
            List.of("set", "list", "remove", "join", "firstjoin", "login", "register");

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
            case "spawn" -> adminOnly(sender, () -> spawn(sender, rest));
            case "lang" -> adminOnly(sender, () -> sender.sendMessage(
                    "Change general.language in config.yml and run /rlogin reload."));
            default -> {
                sender.sendMessage("Unknown subcommand: " + sub);
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
            sender.sendMessage("/rlogin unregister <player>");
            return;
        }
        UUID uuid = resolveUuid(args[0]);
        plugin.accountService().unregister(uuid).thenRun(() ->
                sender.sendMessage(plugin.messages().get("admin.unregistered", Map.of("player", args[0]))));
    }

    private void forceLogin(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("/rlogin forcelogin <player>");
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
            sender.sendMessage("/rlogin migrate <authme|nlogin|jpremium> <path-or-jdbc-url>");
            return;
        }
        var importer = plugin.importerRegistry().get(args[0]);
        if (importer.isEmpty()) {
            sender.sendMessage("Unknown importer: " + args[0]);
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
            sender.sendMessage("/rlogin info <player>");
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

    /**
     * {@code /rlogin spawn set|list|remove <name>} manages named spawn points;
     * {@code /rlogin spawn join|firstjoin|login|register [name|none]} assigns
     * which named spawn is used for that purpose (or clears it with "none",
     * or shows the current assignment when no name is given). When a role has
     * no spawn assigned, players simply stay wherever they last disconnected.
     */
    private void spawn(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("/rlogin spawn <" + String.join("|", SPAWN_ACTIONS) + "> ...");
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        SpawnManager spawns = plugin.spawnManager();

        switch (action) {
            case "set" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only a player can set a spawn point (needs a location).");
                    return;
                }
                if (args.length < 2) {
                    sender.sendMessage("/rlogin spawn set <name>");
                    return;
                }
                Location location = player.getLocation();
                spawns.set(args[1], location);
                sender.sendMessage("Spawn '" + args[1] + "' saved at your current location.");
            }
            case "list" -> {
                var names = spawns.names();
                sender.sendMessage(names.isEmpty() ? "No spawn points defined." : "Spawns: " + String.join(", ", names));
            }
            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage("/rlogin spawn remove <name>");
                    return;
                }
                boolean removed = spawns.remove(args[1]);
                sender.sendMessage(removed ? "Spawn '" + args[1] + "' removed." : "No such spawn: " + args[1]);
            }
            case "join", "firstjoin", "login", "register" -> {
                SpawnManager.Role role = SpawnManager.Role.valueOf(action.toUpperCase(Locale.ROOT));
                if (args.length < 2) {
                    var current = spawns.roleAssignment(role);
                    sender.sendMessage("rlogin." + action + " -> "
                            + current.orElse("(not set — players stay where they logged out)"));
                    return;
                }
                if (args[1].equalsIgnoreCase("none")) {
                    spawns.clearRole(role);
                    sender.sendMessage(action + " spawn cleared.");
                    return;
                }
                boolean ok = spawns.assignRole(role, args[1]);
                sender.sendMessage(ok ? action + " spawn set to '" + args[1] + "'." : "No such spawn: " + args[1]);
            }
            default -> sender.sendMessage("/rlogin spawn <" + String.join("|", SPAWN_ACTIONS) + "> ...");
        }
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
        if (args.length == 1) {
            List<String> options = sender.hasPermission("rlogin.admin")
                    ? java.util.stream.Stream.concat(PLAYER_SUBCOMMANDS.stream(), ADMIN_SUBCOMMANDS.stream()).toList()
                    : PLAYER_SUBCOMMANDS;
            return options.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn") && sender.hasPermission("rlogin.admin")) {
            return SPAWN_ACTIONS.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("spawn")
                && List.of("remove", "join", "firstjoin", "login", "register").contains(args[1].toLowerCase(Locale.ROOT))
                && sender.hasPermission("rlogin.admin")) {
            List<String> names = new ArrayList<>(plugin.spawnManager().names());
            names.add("none");
            return names.stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
