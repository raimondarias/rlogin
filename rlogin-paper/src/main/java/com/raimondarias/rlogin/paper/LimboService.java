package com.raimondarias.rlogin.paper;

import org.bukkit.entity.Player;

/**
 * Starts/stops the periodic reminder ("you need to log in...") for a
 * player pending authentication. The freeze itself is enforced by
 * {@link com.raimondarias.rlogin.paper.listener.FreezeListener}, which
 * checks {@link AuthSessionManager} on every event; this class only
 * manages the reminder and the initial message.
 */
public final class LimboService {

    private final RLoginPaperPlugin plugin;

    public LimboService(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    public void freeze(Player player) {
        plugin.authSessions().cancelReminder(player.getUniqueId());
        startLoginTimeout(player);
        if (!plugin.config().limboFreeze()) {
            return;
        }
        player.sendMessage(plugin.messages().get("limbo.freeze-reminder"));
        long period = Math.max(1, plugin.config().limboReminderIntervalSeconds()) * 20L;
        var task = plugin.scheduler().runForPlayerTimer(player, period, period, () -> {
            // A player inside the new-device confirmation window is told exactly
            // what to do by the /confirm prompt itself; the generic "please log
            // in" would only contradict it.
            if (!plugin.authSessions().isAuthenticated(player.getUniqueId())
                    && !plugin.authSessions().isAwaitingDeviceConfirmation(player.getUniqueId())) {
                player.sendMessage(plugin.messages().get("limbo.freeze-reminder"));
            }
        });
        plugin.authSessions().trackReminder(player.getUniqueId(), task);
    }

    /**
     * Kicks a player who never logs in, after {@code limbo.login-timeout-seconds}.
     *
     * <p>Someone sitting frozen at the login prompt forever still occupies a
     * player slot and still costs the server a connection, which is all an
     * attacker needs to fill a server without ever owning an account. The
     * timer is a no-op for anyone who authenticates in time: it re-checks the
     * session before kicking rather than being cancelled, so a player who logs
     * in and stays online is never touched by the task that outlives them.</p>
     */
    private void startLoginTimeout(Player player) {
        int seconds = plugin.config().limboLoginTimeoutSeconds();
        if (seconds <= 0) {
            return;
        }
        plugin.scheduler().runForPlayerLater(player, seconds * 20L, () -> {
            if (!player.isOnline() || plugin.authSessions().isAuthenticated(player.getUniqueId())) {
                return;
            }
            player.kickPlayer(plugin.messages().get("limbo.login-timeout"));
        });
    }
}
