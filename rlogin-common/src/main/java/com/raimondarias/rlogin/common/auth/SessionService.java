package com.raimondarias.rlogin.common.auth;

import com.raimondarias.rlogin.api.db.Storage;
import com.raimondarias.rlogin.common.config.RLoginConfig;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Sesión "recuérdame": si un jugador no-premium ya autenticado vuelve a
 * conectar desde la misma IP dentro de la ventana configurada, no se le
 * vuelve a pedir {@code /login}.
 */
public final class SessionService {

    private final Storage storage;
    private final RLoginConfig config;

    public SessionService(Storage storage, RLoginConfig config) {
        this.storage = storage;
        this.config = config;
    }

    public boolean isEnabled() {
        return config.rememberMeEnabled();
    }

    public CompletableFuture<Void> remember(UUID uuid, String ip, String server) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        Instant expiresAt = Instant.now().plusSeconds(config.rememberMeMinutes() * 60L);
        return storage.saveSession(uuid, ip, server, expiresAt);
    }

    public CompletableFuture<Boolean> isRemembered(UUID uuid, String ip) {
        if (!isEnabled() || ip == null) {
            return CompletableFuture.completedFuture(false);
        }
        return storage.hasValidSession(uuid, ip, Instant.now());
    }

    public CompletableFuture<Void> forget(UUID uuid) {
        return storage.clearSession(uuid);
    }
}
