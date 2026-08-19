package com.raimondarias.rlogin.common.security;

import com.raimondarias.rlogin.api.db.Storage;
import com.raimondarias.rlogin.common.config.RLoginConfig;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts failed logins per address and locks the address out.
 *
 * <p>rLogin used to lock the <em>account</em> instead, which had the
 * problem every per-account lockout has: anyone who knows a name can fail
 * five logins and lock its owner out, then keep doing it. The attacker
 * spends nothing and the victim is the one who can't play. Locking the
 * address turns that around — whoever is guessing is the one who waits,
 * and the owner is never affected by someone else's attempts.</p>
 *
 * <p>The trade-off is honest: an attacker with many addresses can keep
 * trying. That is a much more expensive attack than typing a name into a
 * chat box, and it is the trade every serious auth plugin makes.</p>
 *
 * <p>State lives in memory for the fast, per-server path, and every failure
 * is mirrored into {@link Storage} so the backends of a proxy network
 * (sharing a database) stop a guesser together: spreading tries across
 * servers must not multiply the attempts each one sees. {@link
 * AccountService} consults the stored counts as the authoritative check,
 * and a restart forgiving the local map no longer forgives the shared
 * record.</p>
 */
public final class IpThrottle {

    private record Attempts(int failures, Instant lockedUntil) {
    }

    /** Entries older than this with no activity are dropped, so the map can't grow forever. */
    private static final long FORGET_AFTER_SECONDS = 3600;

    private final Storage storage;
    private final RLoginConfig config;
    private final BruteforceGuard guard;
    private final Map<String, Attempts> byAddress = new ConcurrentHashMap<>();

    public IpThrottle(Storage storage, RLoginConfig config, BruteforceGuard guard) {
        this.storage = storage;
        this.config = config;
        this.guard = guard;
    }

    /** Seconds still to wait, or 0 when this address may try again now. */
    public long lockedSecondsRemaining(String ip, Instant now) {
        if (!guard.isEnabled() || ip == null) {
            return 0;
        }
        Attempts attempts = byAddress.get(ip);
        if (attempts == null || attempts.lockedUntil() == null || !attempts.lockedUntil().isAfter(now)) {
            return 0;
        }
        return attempts.lockedUntil().getEpochSecond() - now.getEpochSecond();
    }

    /**
     * Records one wrong password from this address.
     *
     * @param username the account name that was tried, so the shared record
     *                 can also stop someone guessing the <em>same</em> name
     *                 from many different addresses.
     * @return how many tries are left before it is locked out; 0 means it just was.
     */
    public int recordFailure(String ip, String username, Instant now) {
        if (!guard.isEnabled() || ip == null) {
            return Integer.MAX_VALUE;
        }
        purgeStale(now);
        Attempts updated = byAddress.compute(ip, (key, previous) -> {
            int failures = (previous == null ? 0 : previous.failures()) + 1;
            Instant lockedUntil = failures >= config.bruteforceMaxAttempts()
                    ? guard.nextLockUntil(failures, now)
                    : null;
            return new Attempts(failures, lockedUntil);
        });
        // Fire-and-forget into the shared record; the login that called this is
        // already async, and the write's job is to be there for the NEXT attempt.
        if (username != null) {
            storage.recordLoginFailure(ip, username, now);
        }
        return Math.max(0, config.bruteforceMaxAttempts() - updated.failures());
    }

    /** Clears the address after a correct password; nothing is held against it any more. */
    public void recordSuccess(String ip, String username) {
        if (ip != null) {
            byAddress.remove(ip);
        }
        if (username != null) {
            storage.clearLoginFailures(ip, username);
        }
    }

    private void purgeStale(Instant now) {
        Instant cutoff = now.minusSeconds(FORGET_AFTER_SECONDS);
        for (Iterator<Map.Entry<String, Attempts>> it = byAddress.entrySet().iterator(); it.hasNext(); ) {
            Attempts attempts = it.next().getValue();
            if (attempts.lockedUntil() != null && attempts.lockedUntil().isBefore(cutoff)) {
                it.remove();
            }
        }
    }
}
