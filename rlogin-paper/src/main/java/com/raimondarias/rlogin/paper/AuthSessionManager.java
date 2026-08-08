package com.raimondarias.rlogin.paper;

import com.raimondarias.rlogin.paper.scheduler.SchedulerAdapter;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory authority for "is this connected player already authenticated?".
 * It only lives while the player is online; the real persistent state
 * (password, 2FA, IP-remembered session) lives in the database.
 *
 * <p>{@link com.raimondarias.rlogin.paper.listener.FreezeListener} checks
 * this state on every event to decide whether to freeze the player — so as
 * soon as something marks someone as authenticated (login, register,
 * premium, "remember me", or the proxy's {@code TRUSTED} notice), they stop
 * being frozen immediately, with no extra step needed.</p>
 */
public final class AuthSessionManager {

    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SchedulerAdapter.CancellableTask> reminderTasks = new ConcurrentHashMap<>();

    public void markAuthenticated(UUID uuid) {
        authenticated.add(uuid);
        cancelReminder(uuid);
    }

    public boolean isAuthenticated(UUID uuid) {
        return authenticated.contains(uuid);
    }

    public void trackReminder(UUID uuid, SchedulerAdapter.CancellableTask task) {
        SchedulerAdapter.CancellableTask previous = reminderTasks.put(uuid, task);
        if (previous != null) {
            previous.cancel();
        }
    }

    public void cancelReminder(UUID uuid) {
        SchedulerAdapter.CancellableTask task = reminderTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void forget(UUID uuid) {
        authenticated.remove(uuid);
        cancelReminder(uuid);
    }
}
