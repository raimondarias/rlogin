package com.raimondarias.rlogin.paper.listener;

import com.raimondarias.rlogin.common.util.OfflineUuid;
import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Determina si un jugador es premium y arranca (o no) la congelación.
 *
 * <p>La detección de premium es deliberadamente simple y uniforme: se
 * compara el UUID real con el que generaría el propio servidor para ese
 * mismo nombre en modo offline ({@link OfflineUuid}). Si no coinciden, es
 * que alguien (Velocity vía Modern Forwarding, o el propio servidor en
 * online-mode: true) ya verificó esa cuenta contra Mojang — sin necesidad
 * de repetir esa verificación aquí ni de sincronizar nada entre procesos.</p>
 */
public final class JoinListener implements Listener {

    private final RLoginPaperPlugin plugin;

    public JoinListener(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String username = event.getName();
        UUID uuid = event.getUniqueId();
        String ip = event.getAddress() != null ? event.getAddress().getHostAddress() : "unknown";

        boolean floodgatePremium = plugin.config().floodgateAutoLogin()
                && plugin.floodgate().isAvailable()
                && username.startsWith(plugin.config().floodgatePrefix())
                && plugin.floodgate().isFloodgatePlayer(uuid);

        boolean premium = floodgatePremium || !OfflineUuid.isOffline(uuid, username);

        if (premium) {
            plugin.accountService().upsertPremium(uuid, username, ip).join();
            plugin.authSessions().markAuthenticated(uuid);
            return;
        }

        boolean remembered = plugin.sessionService().isRemembered(uuid, ip).join();
        if (remembered) {
            plugin.authSessions().markAuthenticated(uuid);
            plugin.sessionService().remember(uuid, ip, plugin.getServer().getName());
        }
        // Si no está recordado ni es premium, se queda pendiente: FreezeListener y
        // los comandos /login, /register se encargan del resto en cuanto entre al mundo.
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.authSessions().isAuthenticated(uuid)) {
            return;
        }
        if (player.hasPermission("rlogin.bypass")) {
            plugin.authSessions().markAuthenticated(uuid);
            return;
        }
        plugin.limboService().freeze(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.authSessions().forget(event.getPlayer().getUniqueId());
    }
}
