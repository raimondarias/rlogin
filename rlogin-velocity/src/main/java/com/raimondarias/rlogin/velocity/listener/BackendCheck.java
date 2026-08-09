package com.raimondarias.rlogin.velocity.listener;

import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Warns when a server listed under {@code login-servers} isn't actually
 * running rLogin.
 *
 * <p>This is the failure that hurts most and shows least. A name typed
 * slightly wrong, or a lobby the admin forgot to install the plugin on,
 * means players are routed somewhere that never asks them for a password —
 * so they walk straight in unauthenticated, and nothing anywhere says so.
 * The proxy can't tell the difference on its own: a server that doesn't
 * answer looks exactly like one that hasn't answered yet.</p>
 *
 * <p>So it uses the one signal that already exists. Every rLogin backend
 * announces a player over the {@code rlogin:sync} channel when they
 * authenticate; a login server that has taken players and never once spoken
 * on that channel is either missing the plugin or misconfigured, and gets
 * named in the console.</p>
 */
public final class BackendCheck {

    /** Players sent to a login server that we're still waiting to hear about. */
    private final Set<UUID> awaiting = ConcurrentHashMap.newKeySet();

    /** Login servers that have proven they run rLogin, so they're never warned about twice. */
    private final Set<String> confirmed = ConcurrentHashMap.newKeySet();

    /** Login servers already reported, so a busy network doesn't repeat the same warning. */
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    private final ProxyServer server;
    private final RLoginConfig config;
    private final Logger logger;

    public BackendCheck(ProxyServer server, RLoginConfig config, Logger logger) {
        this.server = server;
        this.config = config;
        this.logger = logger;
    }

    /** At startup: names in the config that velocity.toml has never heard of are simply wrong. */
    public void checkNamesResolve() {
        if (config.loginServers().isEmpty()) {
            logger.warn("login-servers.servers is empty, so rLogin cannot send anyone anywhere to log in. "
                    + "List the server(s) running rLogin there.");
            return;
        }
        Set<String> unknown = new LinkedHashSet<>();
        for (String name : config.loginServers()) {
            if (server.getServer(name).isEmpty()) {
                unknown.add(name);
            }
        }
        if (!unknown.isEmpty()) {
            logger.warn("These login servers are not defined in velocity.toml and will be skipped: {}", unknown);
        }
    }

    /** Called when a player is routed to a login server, so we can notice if it never replies. */
    public void expectAuthentication(UUID uuid, RegisteredServer target) {
        if (!confirmed.contains(name(target))) {
            awaiting.add(uuid);
        }
    }

    /** Called when a backend reports someone authenticated: that backend is proven to run rLogin. */
    public void heardFrom(UUID uuid, String serverName) {
        awaiting.remove(uuid);
        confirmed.add(serverName.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Called when a player leaves. If they were routed to a login server,
     * played, and left without that server ever announcing them, the server
     * is not doing the one job it was listed for.
     */
    public void playerGone(UUID uuid, Optional<RegisteredServer> lastServer) {
        if (!awaiting.remove(uuid) || lastServer.isEmpty()) {
            return;
        }
        String name = name(lastServer.get());
        if (confirmed.contains(name) || !isLoginServer(name) || !warned.add(name)) {
            return;
        }
        logger.warn("'{}' is listed under login-servers but has never reported a login to the proxy. "
                + "If rLogin is not installed there, players are reaching it without authenticating.", name);
    }

    private boolean isLoginServer(String name) {
        return config.loginServers().stream().anyMatch(s -> s.equalsIgnoreCase(name));
    }

    private static String name(RegisteredServer server) {
        return server.getServerInfo().getName().toLowerCase(java.util.Locale.ROOT);
    }
}
