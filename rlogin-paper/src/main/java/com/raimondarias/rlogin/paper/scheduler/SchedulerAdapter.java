package com.raimondarias.rlogin.paper.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Envoltorio fino sobre la API de scheduler "Folia-safe" que Paper expone
 * también en servidores normales ({@code Entity#getScheduler()},
 * {@code Bukkit#getAsyncScheduler()}): el mismo código funciona igual en
 * Paper que en Folia sin necesidad de ramificar por plataforma, porque en
 * Paper normal esos métodos simplemente delegan al hilo principal.
 */
public final class SchedulerAdapter {

    private static final boolean FOLIA = detectFolia();

    private final Plugin plugin;

    public SchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    /** Ejecuta en el hilo "dueño" de este jugador (su región en Folia). */
    public void runForPlayer(Player player, Runnable task) {
        player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    /** Ejecuta fuera del hilo del servidor (I/O, red, base de datos...). */
    public void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    /** Repite cada cierto intervalo en el hilo del jugador; se autocancela si se desconecta. */
    public CancellableTask runForPlayerTimer(Player player, long delayTicks, long periodTicks, Runnable task) {
        ScheduledTask scheduled = player.getScheduler().runAtFixedRate(plugin, st -> {
            if (!player.isOnline()) {
                st.cancel();
                return;
            }
            task.run();
        }, null, Math.max(1, delayTicks), Math.max(1, periodTicks));
        return () -> {
            if (scheduled != null) {
                scheduled.cancel();
            }
        };
    }

    /** Repite en un hilo async, sin depender de ningún jugador concreto (ej. limpieza de sesiones). */
    public CancellableTask runAsyncTimer(long delayTicks, long periodTicks, Runnable task) {
        long delayMs = delayTicks * 50L;
        long periodMs = periodTicks * 50L;
        ScheduledTask scheduled = Bukkit.getAsyncScheduler()
                .runAtFixedRate(plugin, st -> task.run(), Math.max(1, delayMs), Math.max(50, periodMs), TimeUnit.MILLISECONDS);
        return scheduled::cancel;
    }

    @FunctionalInterface
    public interface CancellableTask {
        void cancel();
    }
}
