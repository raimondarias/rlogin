package com.raimondarias.rlogin.velocity.listener;

import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Optional authentication-lobby routing. If {@code lobby.auth-server} is
 * configured, every player who isn't already trusted (i.e. not premium,
 * no valid remembered session yet — checked here as "not premium", the
 * final call on remembered sessions still happens on the backend) is sent
 * there first instead of wherever velocity.toml's "try" order would place
 * them. If {@code lobby.default-server} is configured, already-trusted
 * players (premium accounts) go straight there instead.
 *
 * <p>Leaving both settings blank fully disables this and restores
 * velocity.toml's normal behavior — nothing changes for networks that
 * don't want a dedicated auth lobby.</p>
 */
public final class LobbyListener {

    private final ProxyServer server;
    private final RLoginConfig config;
    private final PreLoginListener preLoginListener;
    private final Logger logger;

    public LobbyListener(ProxyServer server, RLoginConfig config, PreLoginListener preLoginListener, Logger logger) {
        this.server = server;
        this.config = config;
        this.preLoginListener = preLoginListener;
        this.logger = logger;
    }

    @Subscribe(order = PostOrder.LATE)
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        boolean trusted = preLoginListener.trustedThisSession().contains(player.getUniqueId());

        String targetName = trusted ? config.defaultLobbyServer() : config.authLobbyServer();
        if (targetName == null || targetName.isBlank()) {
            return; // No lobby configured for this case: respect velocity.toml's normal "try" order.
        }

        Optional<RegisteredServer> target = server.getServer(targetName);
        if (target.isPresent()) {
            event.setInitialServer(target.get());
        } else {
            logger.warn("lobby.{}-server points to '{}', which isn't defined in velocity.toml",
                    trusted ? "default" : "auth", targetName);
        }
    }
}
