package com.raimondarias.rlogin.paper.command;

import com.raimondarias.rlogin.api.AuthReason;
import com.raimondarias.rlogin.api.RLoginAccount;
import com.raimondarias.rlogin.common.update.UpdateChecker;
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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code /rlogin ...} — router to the same commands as their short aliases
 * ({@code /login}, {@code /register}...) plus admin subcommands
 * ({@code rlogin.admin}), including spawn point management.
 */
public final class RLoginAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBCOMMANDS =
            List.of("login", "register", "changepassword", "logout", "2fa", "premium", "version");
    private static final List<String> ADMIN_SUBCOMMANDS =
            List.of("reload", "unregister", "forcelogin", "migrate", "changeuuid", "info", "lang", "spawn");
    private static final List<String> SPAWN_ACTIONS = List.of("set", "remove", "teleport", "list");
    /** The four moments rLogin can move a player at; each one is its own spawn. */
    private static final List<String> SPAWN_ROLES = List.of("join", "firstjoin", "login", "register");

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
            sender.sendMessage(plugin.messages().get("admin.help", Map.of("commands",
                    String.join(", ", sender.hasPermission("rlogin.admin")
                            ? Stream.concat(PLAYER_SUBCOMMANDS.stream(), ADMIN_SUBCOMMANDS.stream()).toList()
                            : PLAYER_SUBCOMMANDS))));
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
            case "changeuuid" -> adminOnly(sender, () -> changeUuid(sender, rest));
            case "version" -> {
                version(sender);
                yield true;
            }
            case "info" -> adminOnly(sender, () -> info(sender, rest));
            case "spawn" -> adminOnly(sender, () -> spawn(sender, rest));
            case "lang" -> adminOnly(sender, () -> sender.sendMessage(
                    plugin.messages().get("admin.lang-hint")));
            default -> {
                sender.sendMessage(plugin.messages().get("admin.unknown-subcommand", Map.of("sub", sub)));
                yield true;
            }
        };
    }

    /**
     * What is running, and whether it is the current release.
     *
     * <p>Deliberately open to every player, not just staff: "which version are
     * you on?" is the first question in every bug report, and needing an
     * operator to answer it is why so many reports guess.</p>
     *
     * <p>The update check runs fresh rather than reporting what startup found.
     * A server that has been up for weeks was told about the releases that
     * existed the day it started.</p>
     */
    private void version(CommandSender sender) {
        String current = plugin.getPluginMeta().getVersion();
        sender.sendMessage(plugin.messages().get("admin.version",
                Map.of("version", current, "platform", plugin.topology().name()
                        .toLowerCase(java.util.Locale.ROOT).replace('_', '-'))));

        if (!plugin.config().updateCheckerEnabled()) {
            return;
        }
        new UpdateChecker(current).check().thenAccept(result -> plugin.scheduler().runAsync(() -> {
            switch (result.status()) {
                case OUTDATED -> sender.sendMessage(plugin.messages().get("admin.version-outdated",
                        Map.of("latest", result.latestVersion(), "url", result.url())));
                case UP_TO_DATE -> sender.sendMessage(plugin.messages().get("admin.version-latest"));
                case AHEAD -> sender.sendMessage(plugin.messages().get("admin.version-ahead",
                        Map.of("latest", result.latestVersion())));
                case UNKNOWN -> sender.sendMessage(plugin.messages().get("admin.version-unknown"));
            }
        }));
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
            sender.sendMessage(plugin.messages().get("admin.usage-unregister"));
            return;
        }
        UUID uuid = resolveUuid(args[0]);
        plugin.accountService().unregister(uuid).thenRun(() ->
                sender.sendMessage(plugin.messages().get("admin.unregistered", Map.of("player", args[0]))));
    }

    private void forceLogin(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.messages().get("admin.usage-forcelogin"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.messages().get("admin.player-not-found"));
            return;
        }
        plugin.accountService().forceLogin(target.getUniqueId(), LoginCommand.ipOf(target)).thenAccept(account ->
                plugin.scheduler().runForPlayer(target, () -> {
                    plugin.authSessions().markAuthenticated(target.getUniqueId(), AuthReason.FORCED_BY_ADMIN);
                    plugin.fireAuthenticated(target, AuthReason.FORCED_BY_ADMIN, true);
                    target.sendMessage(plugin.messages().get("login.success", Map.of("player", target.getName())));
                    sender.sendMessage(plugin.messages().get("admin.force-logged-in", Map.of("player", target.getName())));
                    plugin.notifyProxyAuthenticated(target);
                }));
    }

    private void migrate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().get("admin.usage-migrate"));
            return;
        }
        var importer = plugin.importerRegistry().get(args[0]);
        if (importer.isEmpty()) {
            sender.sendMessage(plugin.messages().get("admin.unknown-importer", Map.of("importer", args[0])));
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

    /**
     * {@code /rlogin changeuuid <from> <to>} — carries an account's
     * credentials over to a different identity.
     *
     * <p>Exists for the one collision standalone hybrid mode makes possible:
     * a cracked player registered a name, its real premium owner later
     * claimed it, and because the owner arrives with their real Mojang UUID
     * the two are now separate accounts by design. This is how the cracked
     * player keeps their password and 2FA on whatever they play as now.</p>
     *
     * <p>Both arguments take a UUID or a name; what each one resolved to is
     * echoed back before anything is written, because getting this wrong
     * silently would be much worse than an extra line of output.</p>
     */
    private void changeUuid(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().get("admin.changeuuid-usage"));
            sender.sendMessage(plugin.messages().get("admin.changeuuid-scope"));
            return;
        }
        // Bukkit's own name->UUID lookup has to happen here, on the command thread,
        // not inside the async database callbacks below.
        UUID fromFallback = looksLikeUuid(args[0]) ? null : resolveUuid(args[0]);
        UUID toFallback = looksLikeUuid(args[1]) ? null : resolveUuid(args[1]);
        String newUsername = looksLikeUuid(args[1]) ? null : args[1];

        resolveIdentity(args[0], fromFallback).thenCombine(resolveIdentity(args[1], toFallback), IdentityPair::new)
                .thenCompose(pair -> {
                    sender.sendMessage(plugin.messages().get("admin.changeuuid-resolved",
                            Map.of("input", args[0], "uuid", pair.from().toString())));
                    sender.sendMessage(plugin.messages().get("admin.changeuuid-resolved",
                            Map.of("input", args[1], "uuid", pair.to().toString())));
                    return plugin.accountService().changeIdentity(pair.from(), pair.to(), newUsername);
                })
                .whenComplete((result, error) -> {
                    if (error != null) {
                        sender.sendMessage(plugin.messages().get("admin.changeuuid-failed",
                                Map.of("error", String.valueOf(error.getMessage()))));
                        return;
                    }
                    sender.sendMessage(switch (result) {
                        case SUCCESS -> plugin.messages().get("admin.changeuuid-success");
                        case SOURCE_NOT_FOUND -> plugin.messages().get("admin.changeuuid-not-found",
                                Map.of("input", args[0]));
                        case TARGET_ALREADY_EXISTS -> plugin.messages().get("admin.changeuuid-target-exists");
                        case SAME_IDENTITY -> plugin.messages().get("admin.changeuuid-same");
                    });
                });
    }

    private record IdentityPair(UUID from, UUID to) {
    }

    /** rLogin's own record for that name wins over Bukkit's guess, which can't see accounts nobody has joined with. */
    private CompletableFuture<UUID> resolveIdentity(String value, UUID bukkitFallback) {
        if (bukkitFallback == null) {
            return CompletableFuture.completedFuture(UUID.fromString(value));
        }
        return plugin.accountService().findByUsername(value)
                .thenApply(existing -> existing.map(RLoginAccount::uuid).orElse(bukkitFallback));
    }

    private static boolean looksLikeUuid(String value) {
        return value.length() == 36 && value.indexOf('-') == 8;
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.messages().get("admin.usage-info"));
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
    /**
     * {@code /rlogin spawn set|remove <moment>} and {@code /rlogin spawn list}.
     *
     * <p>The moment <em>is</em> the spawn — there are no spawn names to
     * invent, remember or keep in sync. {@code set} always uses where the
     * sender is standing, so there is nothing to type but which moment it's
     * for.</p>
     */
    private void spawn(CommandSender sender, String[] args) {
        SpawnManager spawns = plugin.spawnManager();
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

        switch (action) {
            case "set" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.messages().get("general.player-only"));
                    return;
                }
                SpawnManager.Role role = parseRole(sender, args, "set");
                if (role == null) {
                    return;
                }
                spawns.set(role, player.getLocation());
                sender.sendMessage(plugin.messages().get("spawn.set", Map.of(
                        "role", role.key(),
                        "location", spawns.get(role).orElseThrow().describe())));
            }
            case "remove" -> {
                SpawnManager.Role role = parseRole(sender, args, "remove");
                if (role == null) {
                    return;
                }
                sender.sendMessage(plugin.messages().get(
                        spawns.remove(role) ? "spawn.removed" : "spawn.not-removed",
                        Map.of("role", role.key())));
            }
            case "teleport", "tp" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.messages().get("general.player-only"));
                    return;
                }
                SpawnManager.Role role = parseRole(sender, args, "teleport");
                if (role == null) {
                    return;
                }
                teleportToSpawn(player, spawns, role);
            }
            case "list" -> {
                sender.sendMessage(plugin.messages().get("spawn.header"));
                String notSet = plugin.messages().get("spawn.not-set");
                for (SpawnManager.Role role : SpawnManager.Role.values()) {
                    sender.sendMessage(plugin.messages().get("spawn.entry", Map.of(
                            "role", role.key(),
                            "location", spawns.get(role)
                                    .map(SpawnManager.SpawnPoint::describe).orElse(notSet))));
                }
                sender.sendMessage(plugin.messages().get("spawn.roles-help"));
            }
            default -> {
                sendSpawnUsage(sender);
            }
        }
    }

    /**
     * Sends the admin to a spawn so they can see it for themselves. Every
     * way this can fail is reported separately — "not set" and "the world
     * isn't loaded" look identical to a player who just doesn't move, and
     * telling them apart is the entire reason to have this command.
     */
    private void teleportToSpawn(Player player, SpawnManager spawns, SpawnManager.Role role) {
        var point = spawns.get(role);
        if (point.isEmpty()) {
            player.sendMessage(plugin.messages().get("spawn.teleport-not-set", Map.of("role", role.key())));
            return;
        }
        var location = point.get().toLocation();
        if (location.isEmpty()) {
            player.sendMessage(plugin.messages().get("spawn.teleport-world-missing",
                    Map.of("role", role.key(), "world", point.get().world())));
            return;
        }
        player.teleportAsync(location.get());
        player.sendMessage(plugin.messages().get("spawn.teleported",
                Map.of("role", role.key(), "location", point.get().describe())));
    }

    private void sendSpawnUsage(CommandSender sender) {
        sender.sendMessage(plugin.messages().get("spawn.usage",
                Map.of("roles", String.join("|", SPAWN_ROLES))));
        sender.sendMessage(plugin.messages().get("spawn.roles-help"));
    }

    /** Null (with the usage already sent) when the moment is missing or misspelled. */
    private SpawnManager.Role parseRole(CommandSender sender, String[] args, String action) {
        if (args.length < 2) {
            sendSpawnUsage(sender);
            return null;
        }
        return SpawnManager.Role.parse(args[1]).orElseGet(() -> {
            sender.sendMessage(plugin.messages().get("spawn.unknown-role",
                    Map.of("role", args[1], "roles", String.join(", ", SPAWN_ROLES))));
            return null;
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
        // "set" and "remove" both take one of the four moments; "list" takes nothing.
        if (args.length == 3 && args[0].equalsIgnoreCase("spawn")
                && List.of("set", "remove", "teleport", "tp").contains(args[1].toLowerCase(Locale.ROOT))
                && sender.hasPermission("rlogin.admin")) {
            return SPAWN_ROLES.stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
