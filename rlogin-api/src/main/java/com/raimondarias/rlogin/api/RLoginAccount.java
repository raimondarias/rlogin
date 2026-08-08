package com.raimondarias.rlogin.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Representa una cuenta gestionada por rLogin, ya sea premium (Java original,
 * verificada por Mojang/Modern Forwarding) o no-premium (cracked, con
 * contraseña propia).
 *
 * <p>Para cuentas premium {@code passwordHash} normalmente es {@code null}:
 * no hace falta contraseña porque la identidad ya viene verificada por
 * Mojang antes de que el jugador llegue al servidor.</p>
 */
public record RLoginAccount(
        UUID uuid,
        String username,
        boolean premium,
        String passwordHash,
        String hashAlgo,
        String totpSecret,
        boolean totpEnabled,
        String lastIp,
        Instant lastLoginAt,
        Instant registeredAt,
        int failedAttempts,
        Instant lockedUntil
) {

    public boolean requiresPassword() {
        return !premium;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public RLoginAccount withPassword(String newHash, String algo) {
        return new RLoginAccount(uuid, username, premium, newHash, algo, totpSecret, totpEnabled,
                lastIp, lastLoginAt, registeredAt, failedAttempts, lockedUntil);
    }

    public RLoginAccount withTotp(String secret, boolean enabled) {
        return new RLoginAccount(uuid, username, premium, passwordHash, hashAlgo, secret, enabled,
                lastIp, lastLoginAt, registeredAt, failedAttempts, lockedUntil);
    }

    public RLoginAccount withLogin(String ip, Instant when) {
        return new RLoginAccount(uuid, username, premium, passwordHash, hashAlgo, totpSecret, totpEnabled,
                ip, when, registeredAt, 0, null);
    }

    public RLoginAccount withFailedAttempt(int attempts, Instant lockUntil) {
        return new RLoginAccount(uuid, username, premium, passwordHash, hashAlgo, totpSecret, totpEnabled,
                lastIp, lastLoginAt, registeredAt, attempts, lockUntil);
    }
}
