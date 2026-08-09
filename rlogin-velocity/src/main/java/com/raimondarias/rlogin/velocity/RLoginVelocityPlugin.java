package com.raimondarias.rlogin.velocity;

import com.google.inject.Inject;
import com.raimondarias.rlogin.common.auth.PremiumChecker;
import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.raimondarias.rlogin.common.update.UpdateChecker;
import com.raimondarias.rlogin.velocity.command.RLoginVelocityCommand;
import com.raimondarias.rlogin.velocity.listener.BackendCheck;
import com.raimondarias.rlogin.velocity.listener.LobbyListener;
import com.raimondarias.rlogin.velocity.listener.OnlineModeConflictListener;
import com.raimondarias.rlogin.velocity.listener.PreLoginListener;
import com.raimondarias.rlogin.velocity.listener.SyncListener;
import com.raimondarias.rlogin.velocity.metrics.VelocityMetricsService;
import org.bstats.velocity.Metrics;
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
        version = RLoginVelocityPlugin.PLUGIN_VERSION,
        authors = {"raimondarias"},
        description = "Automatic premium auto-login + password login for cracked accounts (Paper, Velocity, Folia)"
)
public final class RLoginVelocityPlugin {

    /**
     * Velocity's annotation needs a compile-time constant, and the update
     * checker needs the same value at runtime — declaring it once is what
     * stops the two from drifting apart.
     */
    public static final String PLUGIN_VERSION = "1.1.2";

    public static final MinecraftChannelIdentifier SYNC_CHANNEL = MinecraftChannelIdentifier.create("rlogin", "sync");

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;

    private RLoginConfig config;
    private PremiumChecker premiumChecker;
    private SyncListener syncListener;

    @Inject
    public RLoginVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory,
                                Metrics.Factory metricsFactory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        if (!loadConfig()) {
            return;
        }

        server.getChannelRegistrar().register(SYNC_CHANNEL);

        // Velocity turns away every non-premium connection itself when online-mode is on,
        // so on a network that promised to accept them there is nothing left for rLogin to
        // do. Refuse loudly rather than let the misconfiguration look healthy.
        if (server.getConfiguration().isOnlineMode() && config.authMode().allowsPasswords()) {
            server.getEventManager().register(this, new OnlineModeConflictListener(logger));
            return;
        }

        PreLoginListener preLoginListener = new PreLoginListener(config, premiumChecker, logger);
        BackendCheck backendCheck = new BackendCheck(server, config, logger);
        LobbyListener lobbyListener = new LobbyListener(this, server, config, preLoginListener, backendCheck, logger);
        this.syncListener = new SyncListener(server, config, preLoginListener, lobbyListener, backendCheck);
        backendCheck.checkNamesResolve();

        server.getEventManager().register(this, preLoginListener);
        server.getEventManager().register(this, syncListener);
        server.getEventManager().register(this, lobbyListener);

        CommandManager commands = server.getCommandManager();
        commands.register(commands.metaBuilder("rlogin").plugin(this).build(), new RLoginVelocityCommand(this));

        logger.info("rLogin (Velocity) ready. Premium accounts are verified here; everything else is up to the "
                        + "backends. Login servers: {} | After login: {}",
                config.loginServers().isEmpty() ? "(none configured!)" : String.join(", ", config.loginServers()),
                config.afterLoginAction().name().toLowerCase(java.util.Locale.ROOT));

        checkForUpdates();
        VelocityMetricsService.startIfEnabled(metricsFactory, this, config, logger);
    }

    /**
     * Same check the Paper side does, so a network doesn't end up with the
     * proxy and the backends on different versions without anyone noticing.
     * Best-effort: never blocks startup, never complains if it can't reach
     * GitHub.
     */
    private void checkForUpdates() {
        if (!config.updateCheckerEnabled()) {
            return;
        }
        new UpdateChecker(PLUGIN_VERSION).check().thenAccept(result -> {
            switch (result.status()) {
                case OUTDATED -> logger.warn("rLogin {} is available (you're on {}): {}",
                        result.latestVersion(), result.currentVersion(), result.url());
                case UP_TO_DATE -> logger.info("You are running the latest release: {}", result.currentVersion());
                case AHEAD -> logger.info("You are running {}, newer than the latest release ({}).",
                        result.currentVersion(), result.latestVersion());
                case UNKNOWN -> logger.info("Could not check for updates. Nothing is wrong with your proxy.");
            }
        });
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (premiumChecker != null) {
            premiumChecker.shutdown();
        }
    }

    private boolean loadConfig() {
        java.util.List<String> addedSettings = new java.util.ArrayList<>();
        try {
            this.config = RLoginConfig.load(dataDirectory, "velocity-config.yml", addedSettings);
        } catch (IOException e) {
            logger.error("Could not load rLogin's configuration", e);
            return false;
        }
        if (!addedSettings.isEmpty()) {
            logger.info("Added {} new setting(s) to your config.yml: {} - your existing values were kept.",
                    addedSettings.size(), String.join(", ", addedSettings));
        }
        this.premiumChecker = new PremiumChecker(config);
        return true;
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

}
