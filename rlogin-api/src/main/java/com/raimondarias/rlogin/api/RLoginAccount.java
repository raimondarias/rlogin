package com.raimondarias.rlogin.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an account managed by rLogin, either premium (Java-original,
 * verified by Mojang/Modern Forwarding) or non-premium (cracked, with its
 * own password).
 *
 * <p>For premium accounts {@code passwordHash} is normally {@code null}: no
 * password is needed because identity is already verified by Mojang before
 * the player ever reaches the server.</p>
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

    /**
     * Same account under a different identity. Used when a name changes
     * hands — e.g. a cracked player who registered a name that its real
     * premium owner later claims, and whose password/2FA has to follow them
     * to whatever they play as now.
     */
    public RLoginAccount withIdentity(UUID newUuid, String newUsername) {
        return new RLoginAccount(newUuid, newUsername, premium, passwordHash, hashAlgo, totpSecret, totpEnabled,
                lastIp, lastLoginAt, registeredAt, failedAttempts, lockedUntil);
    }

    public RLoginAccount withFailedAttempt(int attempts, Instant lockUntil) {
        return new RLoginAccount(uuid, username, premium, passwordHash, hashAlgo, totpSecret, totpEnabled,
                lastIp, lastLoginAt, registeredAt, attempts, lockUntil);
    }
}
