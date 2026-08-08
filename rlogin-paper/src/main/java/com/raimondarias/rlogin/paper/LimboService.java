package com.raimondarias.rlogin.paper;

import org.bukkit.entity.Player;

/**
 * Arranca/detiene el recordatorio periódico ("debes iniciar sesión...")
 * para un jugador pendiente de autenticar. La congelación en sí la aplica
 * {@link com.raimondarias.rlogin.paper.listener.FreezeListener} consultando
 * {@link AuthSessionManager} en cada evento; esta clase solo gestiona el
 * recordatorio y el mensaje inicial.
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
