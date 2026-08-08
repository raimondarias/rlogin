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
        if (!plugin.config().limboFreeze()) {
            return;
        }
        player.sendMessage(plugin.messages().get("limbo.freeze-reminder"));
        long period = Math.max(1, plugin.config().limboReminderIntervalSeconds()) * 20L;
        var task = plugin.scheduler().runForPlayerTimer(player, period, period, () -> {
            if (!plugin.authSessions().isAuthenticated(player.getUniqueId())) {
                player.sendMessage(plugin.messages().get("limbo.freeze-reminder"));
            }
        });
        plugin.authSessions().trackReminder(player.getUniqueId(), task);
    }
}
