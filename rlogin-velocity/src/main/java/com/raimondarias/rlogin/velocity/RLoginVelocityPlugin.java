package com.raimondarias.rlogin.velocity;

import com.google.inject.Inject;
import com.raimondarias.rlogin.api.db.Storage;
import com.raimondarias.rlogin.common.auth.PremiumChecker;
import com.raimondarias.rlogin.common.auth.SessionService;
import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.raimondarias.rlogin.common.db.StorageFactory;
import com.raimondarias.rlogin.common.i18n.Messages;
import com.raimondarias.rlogin.velocity.command.RLoginVelocityCommand;
import com.raimondarias.rlogin.velocity.listener.LobbyListener;
import com.raimondarias.rlogin.velocity.listener.PreLoginListener;
import com.raimondarias.rlogin.velocity.listener.SyncListener;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Entry point for the proxy side of rLogin. Its only "magic" responsibility
 * is deciding, in {@link PreLoginListener}, whether Velocity should force
 * the encrypted Mojang handshake (premium account) or let the connection
 * through in offline mode (cracked account, the backend will ask for
 * /login). It never persists accounts — that lives in {@code rlogin-paper},
 * which owns the database.
 */
@Plugin(
        id = "rlogin",
        name = "rLogin",
        version = "1.0.0",
        authors = {"raimondarias"},
        description = "Automatic premium auto-login + password login for cracked accounts (Paper, Velocity, Folia)"
)
public final class RLoginVelocityPlugin {

    public static final MinecraftChannelIdentifier SYNC_CHANNEL = MinecraftChannelIdentifier.create("rlogin", "sync");

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private RLoginConfig config;
    private Messages messages;
    private PremiumChecker premiumChecker;
    private SyncListener syncListener;

    /** Only non-null when {@code database.enabled: true} in velocity-config.yml AND the connection succeeded. */
    private Storage storage;
    private SessionService sessionService;

    @Inject
    public RLoginVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        if (!loadConfig()) {
            return;
        }

        server.getChannelRegistrar().register(SYNC_CHANNEL);

        PreLoginListener preLoginListener = new PreLoginListener(config, premiumChecker, logger);
        this.syncListener = new SyncListener(server, config, preLoginListener);
        LobbyListener lobbyListener = new LobbyListener(server, config, preLoginListener, sessionService, logger);

        server.getEventManager().register(this, preLoginListener);
        server.getEventManager().register(this, syncListener);
        server.getEventManager().register(this, lobbyListener);

        CommandManager commands = server.getCommandManager();
        commands.register(commands.metaBuilder("rlogin").plugin(this).build(), new RLoginVelocityCommand(this));

        logger.info("rLogin (Velocity) ready. Premium auto-login: {} | Remembered-session lobby bypass: {}",
                config.premiumAutoLogin() ? "enabled" : "disabled",
                sessionService != null ? "enabled" : "disabled");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (premiumChecker != null) {
            premiumChecker.shutdown();
        }
        closeStorage();
    }

    private boolean loadConfig() {
        try {
            this.config = RLoginConfig.load(dataDirectory, "velocity-config.yml");
        } catch (IOException e) {
            logger.error("Could not load rLogin's configuration", e);
            return false;
        }
        this.messages = Messages.load(dataDirectory, config.language());
        this.premiumChecker = new PremiumChecker(config);
        setUpOptionalDatabase();
        return true;
    }

    /**
     * The proxy never needs the database for its core job (premium
     * detection is per-connection, cross-backend trust travels over the
     * rlogin:sync channel). This only wires a read-only session lookup so
     * {@link LobbyListener} can skip the auth-lobby hop for players with a
     * still-valid "remember me" session — see velocity-config.yml's
     * {@code database} section. Any failure here is logged and swallowed:
     * it must never prevent the proxy (or auto-login) from working.
     */
    private void setUpOptionalDatabase() {
        closeStorage();
        if (!config.databaseEnabled()) {
            return;
        }
        try {
            Storage newStorage = StorageFactory.create(config, dataDirectory);
            newStorage.init().join();
            this.storage = newStorage;
            this.sessionService = new SessionService(storage, config);
        } catch (Exception e) {
            logger.warn("Could not connect to the database configured under 'database:' in velocity-config.yml "
                    + "— the auth-lobby will keep routing remembered sessions through auth-server as before. Cause: {}",
                    e.getMessage());
            this.storage = null;
            this.sessionService = null;
        }
    }

    private void closeStorage() {
        if (storage != null) {
            storage.close();
            storage = null;
        }
        sessionService = null;
    }

    public void reload() {
        loadConfig();
    }

    public ProxyServer server() {
        return server;
    }

    public Logger logger() {
        return logger;
    }

    public RLoginConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }
}
