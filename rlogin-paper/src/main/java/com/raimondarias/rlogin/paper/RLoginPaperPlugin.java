package com.raimondarias.rlogin.paper;

import com.raimondarias.rlogin.api.db.Storage;
import com.raimondarias.rlogin.common.auth.AccountService;
import com.raimondarias.rlogin.common.auth.PremiumChecker;
import com.raimondarias.rlogin.common.auth.SessionService;
import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.raimondarias.rlogin.common.db.StorageFactory;
import com.raimondarias.rlogin.common.i18n.Messages;
import com.raimondarias.rlogin.common.migrate.ImporterRegistry;
import com.raimondarias.rlogin.common.migrate.MigrationService;
import com.raimondarias.rlogin.common.security.PremiumNameGuard;
import com.raimondarias.rlogin.common.security.SensitiveCommands;
import com.raimondarias.rlogin.common.sync.SyncMessage;
import com.raimondarias.rlogin.common.update.UpdateChecker;
import com.raimondarias.rlogin.paper.bedrock.FloodgateSupport;
import com.raimondarias.rlogin.paper.command.ChangePasswordCommand;
import com.raimondarias.rlogin.paper.command.LoginCommand;
import com.raimondarias.rlogin.paper.command.LogoutCommand;
import com.raimondarias.rlogin.paper.command.PremiumInfoCommand;
import com.raimondarias.rlogin.paper.command.RLoginAdminCommand;
import com.raimondarias.rlogin.paper.command.RegisterCommand;
import com.raimondarias.rlogin.paper.command.TotpCommand;
import com.raimondarias.rlogin.paper.hybrid.HybridAuthListener;
import com.raimondarias.rlogin.paper.hybrid.HybridVerificationTracker;
import com.raimondarias.rlogin.paper.listener.FreezeListener;
import com.raimondarias.rlogin.paper.listener.JoinListener;
import com.raimondarias.rlogin.paper.listener.SyncMessageListener;
import com.raimondarias.rlogin.paper.metrics.MetricsService;
import com.raimondarias.rlogin.paper.scheduler.SchedulerAdapter;
import com.raimondarias.rlogin.paper.security.CommandAuditListener;
import com.raimondarias.rlogin.paper.security.CommandLogFilter;
import com.raimondarias.rlogin.paper.spawn.SpawnManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for the Paper/Folia side of rLogin. This is where the
 * database and all account logic lives; Velocity (if present) only decides
 * online/offline-mode per connection, nothing more.
 */
public final class RLoginPaperPlugin extends JavaPlugin {

    private static final String SYNC_CHANNEL = "rlogin:sync";

    private RLoginConfig config;
    private Messages messages;
    private Storage storage;
    private PremiumChecker premiumChecker;
    private AccountService accountService;
    private SessionService sessionService;
    private ImporterRegistry importerRegistry;
    private MigrationService migrationService;
    private FloodgateSupport floodgate;
    private SchedulerAdapter scheduler;
    private LimboService limboService;
    private SpawnManager spawnManager;
    private final AuthSessionManager authSessions = new AuthSessionManager();
    private final HybridVerificationTracker hybridVerificationTracker = new HybridVerificationTracker();
    private HybridAuthListener hybridAuthListener;

    private SchedulerAdapter.CancellableTask sessionCleanupTask;

    @Override
    public void onEnable() {
        if (!loadConfigAndServices()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getMessenger().registerOutgoingPluginChannel(this, SYNC_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, SYNC_CHANNEL, new SyncMessageListener(this));

        // Before anything else can log a command: the server writes passwords to the console
        // in the clear, and it does so before any event a plugin could cancel.
        hidePasswordsInLogs();

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);

        // Optional: only actually does anything if premium.standalone-hybrid-mode is enabled
        // AND the PacketEvents plugin is installed — no-ops (with a log warning) otherwise.
        this.hybridAuthListener = HybridAuthListener.setUpIfEnabled(this, premiumChecker, hybridVerificationTracker);

        registerCommands();

        checkForUpdates();
        MetricsService.startIfEnabled(this);

        // Periodic cleanup of expired "remember me" sessions: every 30 minutes, on an async thread.
        this.sessionCleanupTask = scheduler.runAsyncTimer(20L * 60, 20L * 60 * 30,
                () -> storage.purgeExpiredSessions(Instant.now()));

        getLogger().info("rLogin ready. Folia: " + SchedulerAdapter.isFolia()
                + " | Database: " + config.databaseType()
                + " | Premium auto-login: " + (config.premiumAutoLogin() ? "enabled" : "disabled")
                + " | Floodgate: " + (floodgate.isAvailable() ? "detected" : "not installed"));
    }

    @Override
    public void onDisable() {
        if (sessionCleanupTask != null) {
            sessionCleanupTask.cancel();
        }
        if (hybridAuthListener != null) {
            hybridAuthListener.shutdown();
        }
        if (premiumChecker != null) {
            premiumChecker.shutdown();
        }
        if (migrationService != null) {
            migrationService.shutdown();
        }
        if (storage != null) {
            storage.close();
        }
    }

    private boolean loadConfigAndServices() {
        try {
            this.config = RLoginConfig.load(getDataFolder().toPath());
        } catch (Exception e) {
            getLogger().severe("Could not load rLogin's configuration: " + e.getMessage());
            return false;
        }
        this.messages = Messages.load(getDataFolder().toPath(), config.language());
        this.storage = StorageFactory.create(config, getDataFolder().toPath());
        this.storage.init().join();

        this.premiumChecker = new PremiumChecker(config);
        PremiumNameGuard premiumNameGuard = new PremiumNameGuard(config, premiumChecker);
        this.accountService = new AccountService(storage, config, premiumNameGuard);
        this.sessionService = new SessionService(storage, config);
        this.importerRegistry = new ImporterRegistry();
        this.migrationService = new MigrationService(storage);
        this.floodgate = new FloodgateSupport();
        this.scheduler = new SchedulerAdapter(this);
        this.limboService = new LimboService(this);
        this.spawnManager = new SpawnManager(getDataFolder());
        return true;
    }

    private void registerCommands() {
        getCommand("login").setExecutor(new LoginCommand(this));
        getCommand("register").setExecutor(new RegisterCommand(this));
        getCommand("changepassword").setExecutor(new ChangePasswordCommand(this));
        getCommand("logout").setExecutor(new LogoutCommand(this));
        getCommand("2fa").setExecutor(new TotpCommand(this));
        getCommand("premium").setExecutor(new PremiumInfoCommand(this));

        RLoginAdminCommand adminCommand = new RLoginAdminCommand(this);
        getCommand("rlogin").setExecutor(adminCommand);
        getCommand("rlogin").setTabCompleter(adminCommand);
    }

    /**
     * Reports a newer release once, at startup. Runs on the HTTP client's own
     * threads and swallows every failure, so a server with no outbound network
     * (or GitHub having a bad day) never notices this ran.
     */
    private void checkForUpdates() {
        if (!config.updateCheckerEnabled()) {
            return;
        }
        new UpdateChecker(getPluginMeta().getVersion()).check().thenAccept(update -> update.ifPresent(found ->
                getLogger().info("rLogin " + found.latestVersion() + " is available (you're on "
                        + found.currentVersion() + "): " + found.url())));
    }

    /**
     * Keeps {@code /login} and {@code /register} arguments out of the console
     * and {@code logs/latest.log}. See {@link CommandLogFilter} for why this
     * can't be done by cancelling an event.
     *
     * <p>Deliberately not configurable. A switch for this would only ever be
     * useful to someone who wants the passwords — and since players reuse
     * passwords across servers, that is a switch for harvesting credentials
     * that work elsewhere, not a preference. The stored hash is bcrypt and
     * can't be reversed ({@code PasswordHasher}); the plaintext passing
     * through a command must not become the way around that.</p>
     */
    private void hidePasswordsInLogs() {
        SensitiveCommands sensitiveCommands = sensitiveCommands();
        if (CommandLogFilter.install(sensitiveCommands)) {
            getServer().getPluginManager().registerEvents(
                    new CommandAuditListener(getLogger(), sensitiveCommands), this);
        } else {
            getLogger().warning("Could not hook this server's logging (not Log4j2?), so passwords typed "
                    + "into /login and /register will appear in the console. Consider setting "
                    + "commands.log: false in spigot.yml.");
        }
    }

    /**
     * Built from the labels the server actually registered rather than a
     * hardcoded list, so renaming a command or adding an alias in plugin.yml
     * can never quietly leave its password visible in the log.
     */
    private SensitiveCommands sensitiveCommands() {
        List<String> passwordLabels = new ArrayList<>();
        for (String name : SensitiveCommands.PASSWORD_COMMANDS) {
            addLabels(passwordLabels, name);
        }
        List<String> rootLabels = new ArrayList<>();
        for (String name : SensitiveCommands.ROOT_COMMANDS) {
            addLabels(rootLabels, name);
        }
        return SensitiveCommands.of(passwordLabels, rootLabels);
    }

    private void addLabels(List<String> into, String commandName) {
        into.add(commandName);
        PluginCommand command = getCommand(commandName);
        if (command != null) {
            into.addAll(command.getAliases());
        }
    }

    /** Tells Velocity (if present) this player just authenticated, so it trusts them on other backends too. */
    public void notifyProxyAuthenticated(Player player) {
        player.sendPluginMessage(this, SYNC_CHANNEL,
                new SyncMessage(SyncMessage.Type.AUTHENTICATED, player.getUniqueId()).encode());
    }

    public void reload() {
        loadConfigAndServices();
    }

    // --- getters used by listeners/commands ---
    public RLoginConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public AccountService accountService() {
        return accountService;
    }

    public SessionService sessionService() {
        return sessionService;
    }

    public PremiumChecker premiumChecker() {
        return premiumChecker;
    }

    public ImporterRegistry importerRegistry() {
        return importerRegistry;
    }

    public MigrationService migrationService() {
        return migrationService;
    }

    public FloodgateSupport floodgate() {
        return floodgate;
    }

    public SchedulerAdapter scheduler() {
        return scheduler;
    }

    public LimboService limboService() {
        return limboService;
    }

    public SpawnManager spawnManager() {
        return spawnManager;
    }

    public AuthSessionManager authSessions() {
        return authSessions;
    }

    public HybridVerificationTracker hybridVerificationTracker() {
        return hybridVerificationTracker;
    }
}
