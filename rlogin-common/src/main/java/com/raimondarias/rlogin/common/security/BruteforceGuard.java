package com.raimondarias.rlogin.common.security;

import com.raimondarias.rlogin.common.config.RLoginConfig;

import java.time.Instant;

/**
 * Calcula bloqueos progresivos por intentos fallidos de login. El estado
 * (número de intentos, hasta cuándo está bloqueado) vive en
 * {@link com.raimondarias.rlogin.api.RLoginAccount}, persistido por el
 * {@link com.raimondarias.rlogin.api.db.Storage} — así funciona igual en
 * SQLite que en una red con varios backends compartiendo MySQL.
 */
public final class BruteforceGuard {

    private final RLoginConfig config;

    public BruteforceGuard(RLoginConfig config) {
        this.config = config;
    }

    public boolean isEnabled() {
        return config.bruteforceEnabled();
    }

    /** Segundos de bloqueo dado un número de intentos fallidos consecutivos. */
    public long lockoutSecondsFor(int failedAttempts) {
        int overLimit = failedAttempts - config.bruteforceMaxAttempts();
        if (overLimit < 0) {
            return 0;
        }
        double seconds = config.bruteforceLockoutSeconds()
                * Math.pow(config.bruteforceLockoutMultiplier(), overLimit);
        return (long) Math.min(seconds, config.bruteforceMaxLockoutSeconds());
    }

    /** {@code null} si con {@code failedAttempts} aún no toca bloquear. */
    public Instant nextLockUntil(int failedAttempts, Instant now) {
        long seconds = lockoutSecondsFor(failedAttempts);
        return seconds <= 0 ? null : now.plusSeconds(seconds);
    }
}
