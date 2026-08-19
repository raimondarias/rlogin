package com.raimondarias.rlogin.common.security;

import com.raimondarias.rlogin.common.config.RLoginConfig;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate-limits {@code /recover} attempts, by address and by account.
 *
 * <p>Login is throttled by {@link IpThrottle} and registration by
 * {@link RegistrationLimiter}, but recovery had neither — and it is the one
 * path where a guess is cheap to make and expensive to serve: every attempt
 * runs a bcrypt verify against every stored code, and the target UUID on a
 * cracked server is derivable from the player's name. Without a limit, a few
 * connections could sit there burning CPU indefinitely, and nothing stopped
 * someone from trying codes all day.</p>
 *
 * <p>Deliberately in memory, like the other throttles: the window is short
 * and a restart forgiving everyone costs an attacker one window.</p>
 */
public final class RecoveryThrottle {

    private record Attempts(int failures, Instant lockedUntil) {
    }

    private final RLoginConfig config;
    private final Map<String, Attempts> byIp = new ConcurrentHashMap<>();
    private final Map<UUID, Attempts> byAccount = new ConcurrentHashMap<>();

    public RecoveryThrottle(RLoginConfig config) {
        this.config = config;
    }

    /** A max of 0 turns the throttle off entirely. */
    public boolean isEnabled() {
        return config.recoveryMaxAttempts() > 0;
    }

    /** Seconds still to wait, or 0 when this address+account may try again now. */
    public long lockedSecondsRemaining(String ip, UUID uuid, Instant now) {
        if (!isEnabled()) {
            return 0;
        }
        return Math.max(lockedFor(byIp.get(ip), now), lockedFor(byAccount.get(uuid), now));
    }

    /** Records one wrong code; the address and the account are throttled together. */
    public void recordFailure(String ip, UUID uuid, Instant now) {
        if (!isEnabled()) {
            return;
        }
        record(byIp, ip, now);
        record(byAccount, uuid, now);
    }

    /** A successful recovery clears both sides. */
    public void recordSuccess(String ip, UUID uuid) {
        if (ip != null) {
            byIp.remove(ip);
        }
        byAccount.remove(uuid);
    }

    private void record(Map<?, Attempts> map, Object key, Instant now) {
        map.compute(key, (k, previous) -> {
            int failures = (previous == null ? 0 : previous.failures()) + 1;
            Instant lockedUntil = failures >= config.recoveryMaxAttempts()
                    ? now.plusSeconds(config.recoveryLockoutMinutes() * 60L)
                    : null;
            return new Attempts(failures, lockedUntil);
        });
    }

    private long lockedFor(Attempts attempts, Instant now) {
        if (attempts == null || attempts.lockedUntil() == null || !attempts.lockedUntil().isAfter(now)) {
            return 0;
        }
        return attempts.lockedUntil().getEpochSecond() - now.getEpochSecond();
    }
}
