package com.raimondarias.rlogin.paper;

import com.raimondarias.rlogin.api.db.Storage;
import com.raimondarias.rlogin.common.auth.AccountService;
import com.raimondarias.rlogin.common.auth.PremiumChecker;
import com.raimondarias.rlogin.common.auth.RecoveryService;
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
import com.raimondarias.rlogin.paper.integration.LuckPermsSupport;
import com.raimondarias.rlogin.paper.command.ChangePasswordCommand;
import com.raimondarias.rlogin.paper.command.LoginCommand;
import com.raimondarias.rlogin.paper.command.LogoutCommand;
import com.raimondarias.rlogin.paper.command.PremiumInfoCommand;
import com.raimondarias.rlogin.paper.command.RLoginAdminCommand;
import com.raimondarias.rlogin.paper.api.RLoginAuthenticateEvent;
import com.raimondarias.rlogin.paper.api.RLoginRegisterEvent;
import com.raimondarias.rlogin.paper.command.RecoverCommand;
import com.raimondarias.rlogin.paper.command.RegisterCommand;
import com.raimondarias.rlogin.paper.command.TotpCommand;
import com.raimondarias.rlogin.paper.hybrid.HybridAuthListener;
import com.raimondarias.rlogin.paper.hybrid.MissingPacketEventsListener;
import com.raimondarias.rlogin.paper.hybrid.PacketEventsSupport;
import com.raimondarias.rlogin.paper.hybrid.HybridVerificationTracker;
import com.raimondarias.rlogin.paper.listener.FreezeListener;
import com.raimondarias.rlogin.paper.listener.JoinListener;
import com.raimondarias.rlogin.paper.listener.SyncMessageListener;
import com.raimondarias.rlogin.paper.metrics.MetricsService;
import com.raimondarias.rlogin.paper.scheduler.SchedulerAdapter;
import com.raimondarias.rlogin.paper.security.CommandAuditListener;
import com.raimondarias.rlogin.paper.security.CommandLogFilter;
import com.raimondarias.rlogin.paper.setup.OnlineModeConflictListener;
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
    private RecoveryService recoveryService;
    private ImporterRegistry importerRegistry;
    private MigrationService migrationService;
    private FloodgateSupport floodgate;
    private SchedulerAdapter scheduler;
    private LimboService limboService;
    private SpawnManager spawnManager;
    private final AuthSessionManager authSessions = new AuthSessionManager();
    private final HybridVerificationTracker hybridVerificationTracker = new HybridVerificationTracker();
    private HybridAuthListener hybridAuthListener;
    private ServerTopology topology;
    private LuckPermsSupport luckPerms;

    private volatile SensitiveCommands sensitiveCommands;
    private SchedulerAdapter.CancellableTask sessionCleanupTask;

    @Override
    public void onEnable() {
        if (!loadConfigAndServices()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.topology = ServerTopology.detect(this);
        this.luckPerms = new LuckPermsSupport(this);

        getServer().getMessenger().registerOutgoingPluginChannel(this, SYNC_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, SYNC_CHANNEL, new SyncMessageListener(this));

        if (config.syncSecret().isBlank()) {
            getLogger().warning("sync.secret is empty: the rlogin:sync channel is not trusted. "
                    + "Nobody gets the \"skip login on server switch\" shortcut, and a forged "
                    + "message can never authenticate anyone. Set the same sync.secret here and "
                    + "on the proxy.");
        }

        // Before anything else can log a command: the server writes passwords to the console
        // in the clear, and it does so before any event a plugin could cancel.
        hidePasswordsInLogs();

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);

        // online-mode:true on a server that promised to accept players without an account
        // is a contradiction the server wins, silently. Checked before anything else,
        // because nothing below it can matter while every connection is being refused.
        boolean onlineModeConflict = OnlineModeConflictListener.installIfConflicting(this);

        // Premium verification turns itself on exactly where it is needed: a standalone
        // online-mode:false server. There it is not optional, and neither is PacketEvents —
        // see MissingPacketEventsListener for why the alternative is worse than refusing.
        if (onlineModeConflict) {
            // Already refusing every connection; a second banner would only add noise.
        } else if (topology.needsOwnVerification() && config.authMode().verifiesWithMojang()
                && !PacketEventsSupport.isAvailable()) {
            MissingPacketEventsListener.install(this);
        } else {
            this.hybridAuthListener = HybridAuthListener.setUpIfNeeded(this, premiumChecker, hybridVerificationTracker);
        }

        registerCommands();

        MetricsService.startIfEnabled(this);

        // Periodic cleanup of expired "remember me" sessions: every 30 minutes, on an async thread.
        this.sessionCleanupTask = scheduler.runAsyncTimer(20L * 60, 20L * 60 * 30,
                () -> storage.purgeExpiredSessions(Instant.now()));

        getLogger().info("rLogin ready. Server: " + serverBrand()
                + " | Folia: " + SchedulerAdapter.isFolia()
                + " | Database: " + config.databaseType()
                + " | Setup: " + topology.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-')
                + " | Auth mode: " + config.authMode().name().toLowerCase(java.util.Locale.ROOT)
                + " | Premium auto-login: " + premiumAutoLoginStatus()
                + " | Floodgate: " + (floodgate.isAvailable() ? "detected" : "not installed")
                + (onlineModeConflict ? " | REFUSING CONNECTIONS: online-mode conflicts with auth-mode" : ""));

        // Last, so its answer reads as a footnote to the line above rather than
        // arriving before anyone knows what version is even starting.
        checkForUpdates();
    }

    @Override
    public void onDisable() {
        if (luckPerms != null) {
            luckPerms.shutdown();
        }
        if (sessionCleanupTask != null) {
            sessionCleanupTask.cancel();
        }
        if (hybridAuthListener != null) {
            hybridAuthListener.shutdown();
        }
        shutdownServices();
    }

    /** Releases the pools owned by the current services. Safe to call before anything exists. */
    private void shutdownServices() {
        if (premiumChecker != null) {
            premiumChecker.shutdown();
            premiumChecker = null;
        }
        if (migrationService != null) {
            migrationService.shutdown();
            migrationService = null;
        }
        if (storage != null) {
            storage.close();
            storage = null;
        }
    }

    /**
     * Builds (or rebuilds) everything that depends on config.yml.
     *
     * <p>Called again by {@code /rlogin reload}, which is why it starts by
     * shutting the previous set down: each of these owns a connection pool
     * or a thread pool, and replacing the field without closing the old
     * object leaks both, once per reload.</p>
     */
    private boolean loadConfigAndServices() {
        shutdownServices();
        List<String> addedSettings = new ArrayList<>();
        try {
            this.config = RLoginConfig.load(getDataFolder().toPath(), "default-config.yml", addedSettings);
        } catch (Exception e) {
            getLogger().severe("Could not load rLogin's configuration: " + e.getMessage());
            return false;
        }
        if (!addedSettings.isEmpty()) {
            getLogger().info("Added " + addedSettings.size() + " new setting(s) to your config.yml: "
                    + String.join(", ", addedSettings) + " - your existing values were kept.");
        }
        this.messages = Messages.load(getDataFolder().toPath(), config.language());
        this.storage = StorageFactory.create(config, getDataFolder().toPath());
        this.storage.init().join();

        this.premiumChecker = new PremiumChecker(config);
        PremiumNameGuard premiumNameGuard = new PremiumNameGuard(config, premiumChecker);
        this.accountService = new AccountService(storage, config, premiumNameGuard);
        this.sessionService = new SessionService(storage, config);
        this.recoveryService = new RecoveryService(storage, config,
                new com.raimondarias.rlogin.common.security.PasswordHasher(config.bcryptCost()),
                new com.raimondarias.rlogin.common.security.PasswordPolicy(config));
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
        getCommand("recover").setExecutor(new RecoverCommand(this));

        RLoginAdminCommand adminCommand = new RLoginAdminCommand(this);
        getCommand("rlogin").setExecutor(adminCommand);
        getCommand("rlogin").setTabCompleter(adminCommand);
    }

    /**
     * What the startup line says about premium auto-login. It has to be the
     * truth rather than the config value: a server that is blocking every
     * connection for a missing dependency must not print "enabled".
     */
    /**
     * The fork actually running, not just "Paper or not".
     *
     * <p>rLogin needs no code for Purpur, Pufferfish or any other Paper fork —
     * they inherit the whole API it uses, and none of them add anything about
     * logins or identities. What they do change is which build a bug report
     * came from, and "Folia: false" does not say that. A line that names the
     * fork turns a support thread into one question instead of three.</p>
     */
    private String serverBrand() {
        return getServer().getName() + " " + getServer().getMinecraftVersion();
    }

    private String premiumAutoLoginStatus() {
        if (!config.authMode().verifiesWithMojang()) {
            return "off (auth-mode: offline)";
        }
        if (!config.premiumAutoLogin()) {
            return "disabled in config";
        }
        if (topology.needsOwnVerification() && config.authMode().verifiesWithMojang()
                && !PacketEventsSupport.isAvailable()) {
            return "BLOCKED - PacketEvents missing";
        }
        return switch (topology) {
            case ONLINE_MODE -> "handled by the server (online-mode)";
            case BEHIND_PROXY -> "handled by the proxy";
            case STANDALONE_OFFLINE -> "active";
        };
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
        new UpdateChecker(getPluginMeta().getVersion()).check().thenAccept(result -> {
            switch (result.status()) {
                case OUTDATED -> getLogger().warning("rLogin " + result.latestVersion()
                        + " is available (you're on " + result.currentVersion() + "): " + result.url());
                // Said out loud on purpose: silence reads as "up to date" to anyone who
                // doesn't know the check is quiet when it succeeds, which is everyone.
                case UP_TO_DATE -> getLogger().info("You are running the latest release: "
                        + result.currentVersion());
                case AHEAD -> getLogger().info("You are running " + result.currentVersion()
                        + ", newer than the latest release (" + result.latestVersion() + ").");
                case UNKNOWN -> getLogger().info("Could not check for updates. Nothing is wrong with your server.");
            }
        });
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
        this.sensitiveCommands = sensitiveCommands();
        if (CommandLogFilter.install(sensitiveCommands)) {
            getServer().getPluginManager().registerEvents(
                    new CommandAuditListener(getLogger(), this), this);
        } else {
            getLogger().warning("Could not hook this server's logging (not Log4j2?), so passwords typed "
                    + "into /login and /register will appear in the console. Consider setting "
                    + "commands.log: false in spigot.yml.");
        }
    }

    /**
     * The command lines currently protected from the console and logs.
     * Read live by {@link CommandAuditListener}, so a reload can never leave
     * the audit masking a stale set of commands.
     */
    public SensitiveCommands sensitiveCommands() {
        return sensitiveCommands;
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

    /**
     * Tells Velocity (if present) this player just authenticated, so it
     * trusts them on other backends too. The message is signed with
     * {@code sync.secret}; without one configured it is not sent at all,
     * because the proxy would rightly refuse to trust an unsigned one.
     */
    public void notifyProxyAuthenticated(Player player) {
        String secret = config.syncSecret();
        if (secret.isBlank()) {
            return;
        }
        player.sendPluginMessage(this, SYNC_CHANNEL,
                new SyncMessage(SyncMessage.Type.AUTHENTICATED, player.getUniqueId()).encode(secret));
    }

    /**
     * Re-reads config.yml and rebuilds the services around it.
     *
     * <p>Premium verification is deliberately left alone: it is registered
     * with PacketEvents at startup and holds a live packet listener, so
     * swapping it out underneath connecting players is not something a
     * command should do. Anything under {@code premium.} therefore needs a
     * real restart, and this says so rather than letting an admin believe
     * the change took.</p>
     */
    public void reload() {
        loadConfigAndServices();
        // The audit listener reads the list live and the Log4j filter holds its
        // own copy: refresh both so a change in what rLogin registered is never
        // left behind in either.
        this.sensitiveCommands = sensitiveCommands();
        CommandLogFilter.updateCommands(sensitiveCommands);
        if (hybridAuthListener != null) {
            getLogger().info("Config reloaded. Note: premium.* settings only take effect after a full restart.");
        }
    }

    // --- getters used by listeners/commands ---
    /** How this server is set up, which decides whether rLogin must verify premium accounts itself. */
    public ServerTopology topology() {
        return topology;
    }

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

    /**
     * Announces to other plugins that this player is authenticated and can
     * finally be given things. Always called on the player's own thread, so
     * listeners may touch the world -- on Folia too.
     */
    public void fireAuthenticated(Player player, com.raimondarias.rlogin.api.AuthReason reason,
                                  boolean firstServerOfSession) {
        // Before the event: a listener that asks LuckPerms about this player should
        // get the answer that is true now, not the one from a second ago.
        luckPerms.refreshContext(player);
        getServer().getPluginManager().callEvent(
                new RLoginAuthenticateEvent(player, reason, firstServerOfSession));
    }

    public LuckPermsSupport luckPerms() {
        return luckPerms;
    }

    public RecoveryService recoveryService() {
        return recoveryService;
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
