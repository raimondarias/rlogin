package com.raimondarias.rlogin.common.auth;

import com.raimondarias.rlogin.api.db.Storage;
import com.raimondarias.rlogin.common.config.RLoginConfig;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * "Remember me" session: if an already-authenticated non-premium player
 * reconnects from the same IP within the configured window, they won't be
 * asked for {@code /login} again.
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

    /**
     * Mints a short-lived, single-use transfer code the player can redeem
     * from another device instead of typing the password there. Returns the
     * code, or {@code null} when the feature is switched off
     * ({@code session.transfer-token-minutes: 0}).
     */
    public CompletableFuture<String> issueTransferToken(UUID uuid) {
        int minutes = config.transferTokenMinutes();
        if (minutes <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        Instant expiresAt = Instant.now().plusSeconds(minutes * 60L);
        return storage.issueTransferToken(uuid, expiresAt);
    }

    /** Spends a transfer code; false when unknown, already used, or expired. */
    public CompletableFuture<Boolean> redeemTransferToken(UUID uuid, String token) {
        if (token == null || token.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        return storage.consumeTransferToken(uuid, token, Instant.now());
    }
}
