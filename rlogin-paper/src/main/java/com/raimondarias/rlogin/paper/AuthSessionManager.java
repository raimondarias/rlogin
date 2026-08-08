package com.raimondarias.rlogin.paper;

import com.raimondarias.rlogin.paper.scheduler.SchedulerAdapter;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Autoridad en memoria de "¿este jugador conectado ya está autenticado?".
 * Vive solo mientras el jugador está en línea; el estado persistente real
 * (contraseña, 2FA, sesión recordada por IP) está en la base de datos.
 *
 * <p>{@link com.raimondarias.rlogin.paper.listener.FreezeListener} consulta
 * este estado en cada evento para decidir si congelar al jugador — así que
 * en cuanto algo marca a alguien como autenticado (login, registro, premium,
 * "recuérdame", o el aviso {@code TRUSTED} del proxy), deja de estar
 * congelado de inmediato sin ningún paso adicional.</p>
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
