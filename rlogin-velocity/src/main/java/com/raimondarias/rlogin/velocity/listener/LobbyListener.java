package com.raimondarias.rlogin.velocity.listener;

import com.raimondarias.rlogin.common.auth.SessionService;
import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Optional authentication-lobby routing. If {@code lobby.auth-server} is
 * configured, every player who isn't already trusted (i.e. not premium, and
 * — only when {@code database.enabled: true}, see velocity-config.yml — no
 * valid remembered session either) is sent there first instead of wherever
 * velocity.toml's "try" order would place them. If {@code lobby.default-server}
 * is configured, already-trusted players (premium accounts, or a remembered
 * session confirmed via the optional database read) go straight there
 * instead.
 *
 * <p>Without the optional database, a non-premium player with a valid
 * "remember me" session still briefly lands on auth-server and only gets
 * transferred to default-server once the backend confirms the session over
 * the rlogin:sync channel — one extra hop, cosmetic but avoidable.</p>
 *
 * <p>Leaving both lobby settings blank fully disables this and restores
 * velocity.toml's normal behavior — nothing changes for networks that
 * don't want a dedicated auth lobby.</p>
 */
public final class LobbyListener {

    private final ProxyServer server;
    private final RLoginConfig config;
    private final PreLoginListener preLoginListener;
    private final SessionService sessionService;
    private final Logger logger;

    public LobbyListener(ProxyServer server, RLoginConfig config, PreLoginListener preLoginListener,
                          SessionService sessionService, Logger logger) {
        this.server = server;
        this.config = config;
        this.preLoginListener = preLoginListener;
        this.sessionService = sessionService;
        this.logger = logger;
    }

    @Subscribe(order = PostOrder.LATE)
    public EventTask onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();

        if (preLoginListener.trustedThisSession().contains(player.getUniqueId())) {
            route(event, config.defaultLobbyServer());
            return null;
        }

        String authServer = config.authLobbyServer();
        String defaultServer = config.defaultLobbyServer();
        if (authServer.isBlank() && defaultServer.isBlank()) {
            return null; // Auth-lobby feature disabled: respect velocity.toml's normal try order.
        }

        if (sessionService == null) {
            route(event, authServer);
            return null;
        }

        String ip = remoteIp(player);
        if (ip == null) {
            route(event, authServer);
            return null;
        }

        return EventTask.async(() -> {
            boolean remembered;
            try {
                remembered = sessionService.isRemembered(player.getUniqueId(), ip).join();
            } catch (Exception e) {
                logger.warn("Could not check the remember-me session for {} - routing through auth-server as usual. Cause: {}",
                        player.getUsername(), e.getMessage());
                remembered = false;
            }
            if (remembered) {
                preLoginListener.trustedThisSession().add(player.getUniqueId());
                route(event, defaultServer);
            } else {
                route(event, authServer);
            }
        });
    }

    private void route(PlayerChooseInitialServerEvent event, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            return; // No lobby configured for this case: respect velocity.toml's normal "try" order.
        }
        Optional<RegisteredServer> target = server.getServer(targetName);
        if (target.isPresent()) {
            event.setInitialServer(target.get());
        } else {
            logger.warn("lobby.{}-server points to '{}', which isn't defined in velocity.toml",
                    targetName.equals(config.defaultLobbyServer()) ? "default" : "auth", targetName);
        }
    }

    private static String remoteIp(Player player) {
        InetSocketAddress address = player.getRemoteAddress();
        return address == null || address.getAddress() == null ? null : address.getAddress().getHostAddress();
    }
}
