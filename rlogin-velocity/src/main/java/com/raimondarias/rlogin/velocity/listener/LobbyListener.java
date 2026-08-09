package com.raimondarias.rlogin.velocity.listener;

import com.raimondarias.rlogin.common.config.AfterLogin;
import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Decides which server a connecting player lands on, which is the proxy's
 * entire reason to exist in rLogin: someone who hasn't logged in must reach
 * a server that can ask them to, and must not be able to reach anything
 * else in the meantime.
 *
 * <p>Everything it needs is under {@code backend:} and {@code redirect:} in
 * velocity-config.yml. Nothing about passwords, sessions or premium
 * accounts lives here — those belong to the backends.</p>
 *
 * <p>Once {@link SyncListener} hears from a backend that the player
 * authenticated, {@link #onAuthenticated} moves them on, so switching
 * servers later never asks them to log in a second time.</p>
 */
public final class LobbyListener {

    private final Object plugin;
    private final ProxyServer server;
    private final RLoginConfig config;
    private final PreLoginListener preLoginListener;
    private final BackendCheck backendCheck;
    private final Logger logger;

    /** Where each player was before they were sent to an auth server, for {@code redirect.last-server}. */
    private final Map<UUID, String> lastServerByPlayer = new ConcurrentHashMap<>();

    public LobbyListener(Object plugin, ProxyServer server, RLoginConfig config,
                          PreLoginListener preLoginListener, BackendCheck backendCheck, Logger logger) {
        this.plugin = plugin;
        this.backendCheck = backendCheck;
        this.server = server;
        this.config = config;
        this.preLoginListener = preLoginListener;
        this.logger = logger;
    }

    /**
     * Puts a not-yet-authenticated player on an auth server, overruling the
     * choice Velocity's own {@code try} order already made.
     *
     * <p>Premium players verified during {@code PreLoginEvent} skip this
     * entirely: they are already authenticated as far as the network is
     * concerned, so sending them through a login server would be asking
     * them to do something they've already done.</p>
     */
    @Subscribe(order = PostOrder.LATE)
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();

        if (preLoginListener.trustedThisSession().contains(player.getUniqueId())) {
            sendToPostAuthServer(event, player);
            return;
        }
        if (!config.enforceLoginServers()) {
            return; // The admin routes the first server themselves.
        }
        pick(config.loginServers()).ifPresent(target -> {
            event.setInitialServer(target);
            backendCheck.expectAuthentication(player.getUniqueId(), target);
        });
    }

    /**
     * Called once a backend reports the player authenticated. Moves them on
     * only when {@code redirect.after-auth} (or {@code last-server}) says
     * to — a network that authenticates on its lobby usually wants them to
     * simply stay there.
     */
    public void onAuthenticated(Player player) {
        Optional<RegisteredServer> target = postAuthTarget(player);
        if (target.isEmpty() || isAlreadyThere(player, target.get())) {
            return;
        }
        connectLater(player, target.get(), config.switchDelayMs(), true);
    }

    /** Remembers where a player was, so {@link AfterLogin#PREVIOUS} has somewhere to return them to. */
    public void rememberServer(Player player, String serverName) {
        if (config.afterLoginAction().tracksHistory() && !isExcluded(serverName)) {
            lastServerByPlayer.put(player.getUniqueId(), serverName);
        }
    }

    public void forget(UUID uuid) {
        lastServerByPlayer.remove(uuid);
    }

    private void sendToPostAuthServer(PlayerChooseInitialServerEvent event, Player player) {
        postAuthTarget(player).ifPresent(event::setInitialServer);
    }

    /**
     * Where an authenticated player belongs: the server they were on last
     * time if that's enabled and still exists, otherwise one of
     * {@code after-auth.servers}, otherwise nowhere in particular — in which
     * case Velocity's own choice stands.
     */
    private Optional<RegisteredServer> postAuthTarget(Player player) {
        AfterLogin action = config.afterLoginAction();
        if (action == AfterLogin.STAY) {
            return Optional.empty();
        }
        if (action == AfterLogin.PREVIOUS) {
            String last = lastServerByPlayer.get(player.getUniqueId());
            if (last != null) {
                Optional<RegisteredServer> remembered = server.getServer(last);
                if (remembered.isPresent()) {
                    return remembered;
                }
            }
            // No history, or that server is gone: fall through to the configured lobbies.
        }
        return pick(config.afterLoginServers());
    }

    /**
     * One of the configured servers at random, skipping names that
     * velocity.toml doesn't define — with a warning, because a typo here
     * means players silently land somewhere unintended.
     */
    private Optional<RegisteredServer> pick(List<String> names) {
        List<RegisteredServer> candidates = new ArrayList<>();
        for (String name : names) {
            Optional<RegisteredServer> found = server.getServer(name);
            if (found.isPresent()) {
                candidates.add(found.get());
            } else {
                logger.warn("'{}' is listed in rLogin's config but isn't defined in velocity.toml", name);
            }
        }
        return candidates.isEmpty() ? Optional.empty()
                : Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    /**
     * Connects after a delay, and retries once after a longer one. The first
     * delay keeps the switch from landing mid-handshake; the retry covers a
     * backend that was briefly busy, which would otherwise strand the player
     * on the auth server with no explanation.
     */
    private void connectLater(Player player, RegisteredServer target, int delayMs, boolean retry) {
        server.getScheduler().buildTask(plugin, () -> {
            if (!player.isActive()) {
                return;
            }
            player.createConnectionRequest(target).connect().thenAccept(result -> {
                if (result.isSuccessful() || !retry) {
                    return;
                }
                logger.warn("Could not move {} to '{}', retrying in {}ms",
                        player.getUsername(), target.getServerInfo().getName(), config.retryDelayMs());
                connectLater(player, target, config.retryDelayMs(), false);
            });
        }).delay(java.time.Duration.ofMillis(Math.max(0, delayMs))).schedule();
    }

    private boolean isAlreadyThere(Player player, RegisteredServer target) {
        return player.getCurrentServer()
                .map(current -> current.getServerInfo().getName().equals(target.getServerInfo().getName()))
                .orElse(false);
    }

    /**
     * Login servers are excluded without being listed: returning someone to
     * the server that asked for their password is never what "previous"
     * means, and making admins remember to write it down is a trap.
     */
    private boolean isExcluded(String serverName) {
        return config.loginServers().stream().anyMatch(s -> s.equalsIgnoreCase(serverName))
                || config.neverReturnTo().stream().anyMatch(s -> s.equalsIgnoreCase(serverName));
    }
}
