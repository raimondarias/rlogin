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
import com.raimondarias.rlogin.common.sync.SyncMessage;
import com.raimondarias.rlogin.paper.bedrock.FloodgateSupport;
import com.raimondarias.rlogin.paper.command.ChangePasswordCommand;
import com.raimondarias.rlogin.paper.command.LoginCommand;
import com.raimondarias.rlogin.paper.command.LogoutCommand;
import com.raimondarias.rlogin.paper.command.PremiumInfoCommand;
import com.raimondarias.rlogin.paper.command.RLoginAdminCommand;
import com.raimondarias.rlogin.paper.command.RegisterCommand;
import com.raimondarias.rlogin.paper.command.TotpCommand;
import com.raimondarias.rlogin.paper.listener.FreezeListener;
import com.raimondarias.rlogin.paper.listener.JoinListener;
import com.raimondarias.rlogin.paper.listener.SyncMessageListener;
import com.raimondarias.rlogin.paper.scheduler.SchedulerAdapter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;

/**
 * Punto de entrada del lado Paper/Folia de rLogin. Aquí vive la base de
 * datos y toda la lógica de cuentas; Velocity (si existe) solo decide el
 * modo online/offline por conexión, nada más.
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
    private final AuthSessionManager authSessions = new AuthSessionManager();

    private SchedulerAdapter.CancellableTask sessionCleanupTask;

    @Override
    public void onEnable() {
        if (!loadConfigAndServices()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getMessenger().registerOutgoingPluginChannel(this, SYNC_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, SYNC_CHANNEL, new SyncMessageListener(this));

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);

        registerCommands();

        // Limpieza periódica de sesiones "recuérdame" caducadas: cada 30 minutos, en un hilo async.
        this.sessionCleanupTask = scheduler.runAsyncTimer(20L * 60, 20L * 60 * 30,
                () -> storage.purgeExpiredSessions(Instant.now()));

        getLogger().info("rLogin listo. Folia: " + SchedulerAdapter.isFolia()
                + " | BD: " + config.databaseType()
                + " | Auto-login premium: " + (config.premiumAutoLogin() ? "activado" : "desactivado")
                + " | Floodgate: " + (floodgate.isAvailable() ? "detectado" : "no instalado"));
    }

    @Override
    public void onDisable() {
        if (sessionCleanupTask != null) {
            sessionCleanupTask.cancel();
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
            getLogger().severe("No se pudo cargar la configuración de rLogin: " + e.getMessage());
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

    /** Avisa a Velocity (si lo hay) de que este jugador acaba de autenticarse, para que confíe en él en otros backends. */
    public void notifyProxyAuthenticated(Player player) {
        player.sendPluginMessage(this, SYNC_CHANNEL,
                new SyncMessage(SyncMessage.Type.AUTHENTICATED, player.getUniqueId()).encode());
    }

    public void reload() {
        loadConfigAndServices();
    }

    // --- getters usados por listeners/comandos ---
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

    public AuthSessionManager authSessions() {
        return authSessions;
    }
}
