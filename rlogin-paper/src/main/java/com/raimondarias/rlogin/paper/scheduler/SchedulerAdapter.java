package com.raimondarias.rlogin.paper.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper over the "Folia-safe" scheduler API that Paper also exposes
 * on regular servers ({@code Entity#getScheduler()},
 * {@code Bukkit#getAsyncScheduler()}): the same code behaves identically on
 * Paper and Folia with no platform branching needed, because on regular
 * Paper those methods simply delegate to the main thread.
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

    /** Runs on this player's "owning" thread (their region on Folia). */
    public void runForPlayer(Player player, Runnable task) {
        player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    /** Runs off the server thread (I/O, network, database...). */
    public void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    /** Repeats on this player's thread at a fixed interval; auto-cancels when they disconnect. */
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

    /** Repeats on an async thread, not tied to any particular player (e.g. session cleanup). */
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
