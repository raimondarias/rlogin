package com.raimondarias.rlogin.common.security;

import com.raimondarias.rlogin.common.config.RLoginConfig;

import java.time.Instant;

/**
 * Computes progressive lockouts from failed login attempts. The state
 * (attempt count, locked-until) lives on
 * {@link com.raimondarias.rlogin.api.RLoginAccount}, persisted via
 * {@link com.raimondarias.rlogin.api.db.Storage} — so it works the same way
 * on SQLite as on a network with several backends sharing MySQL.
 */
public final class BruteforceGuard {

    private final RLoginConfig config;

    public BruteforceGuard(RLoginConfig config) {
        this.config = config;
    }

    public boolean isEnabled() {
        return config.bruteforceEnabled();
    }

    /** Lockout duration in seconds given a number of consecutive failed attempts. */
    public long lockoutSecondsFor(int failedAttempts) {
        int overLimit = failedAttempts - config.bruteforceMaxAttempts();
        if (overLimit < 0) {
            return 0;
        }
        double seconds = config.bruteforceLockoutSeconds()
                * Math.pow(config.bruteforceLockoutMultiplier(), overLimit);
        return (long) Math.min(seconds, config.bruteforceMaxLockoutSeconds());
    }

    /** {@code null} if {@code failedAttempts} doesn't warrant a lockout yet. */
    public Instant nextLockUntil(int failedAttempts, Instant now) {
        long seconds = lockoutSecondsFor(failedAttempts);
        return seconds <= 0 ? null : now.plusSeconds(seconds);
    }
}
