package com.raimondarias.rlogin.velocity;

import com.google.inject.Inject;
import com.raimondarias.rlogin.common.auth.PremiumChecker;
import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.raimondarias.rlogin.common.i18n.Messages;
import com.raimondarias.rlogin.velocity.command.RLoginVelocityCommand;
import com.raimondarias.rlogin.velocity.listener.PreLoginListener;
import com.raimondarias.rlogin.velocity.listener.SyncListener;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Punto de entrada del lado proxy de rLogin. Su única responsabilidad
 * "mágica" es decidir, en {@link PreLoginListener}, si Velocity debe forzar
 * el handshake cifrado con Mojang (cuenta premium) o dejar pasar la
 * conexión en modo offline (cuenta cracked, se le pedirá /login en el
 * backend). No persiste cuentas: eso vive en {@code rlogin-paper}, que es
 * quien tiene la base de datos.
 */
@Plugin(
        id = "rlogin",
        name = "rLogin",
        version = "1.0.0",
        authors = {"raimondarias"},
        description = "Autenticación premium automática + login para no-premium (Paper, Velocity, Folia)"
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
        this.syncListener = new SyncListener(server, preLoginListener);

        server.getEventManager().register(this, preLoginListener);
        server.getEventManager().register(this, syncListener);

        CommandManager commands = server.getCommandManager();
        commands.register(commands.metaBuilder("rlogin").plugin(this).build(), new RLoginVelocityCommand(this));

        logger.info("rLogin (Velocity) listo. Auto-login premium: {}",
                config.premiumAutoLogin() ? "activado" : "desactivado");
    }

    private boolean loadConfig() {
        try {
            this.config = RLoginConfig.load(dataDirectory);
        } catch (IOException e) {
            logger.error("No se pudo cargar la configuración de rLogin", e);
            return false;
        }
        this.messages = Messages.load(dataDirectory, config.language());
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

    public Messages messages() {
        return messages;
    }
}
