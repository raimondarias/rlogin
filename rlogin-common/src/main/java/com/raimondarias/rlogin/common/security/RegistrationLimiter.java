package com.raimondarias.rlogin.common.security;

import com.raimondarias.rlogin.common.config.RLoginConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caps how many accounts one address may create in a window.
 *
 * <p>{@link IpThrottle} guards the other direction — guessing a password that
 * already exists — and neither covers the other. Without this, a single
 * address can create accounts as fast as it can send packets: the database
 * grows without bound, every name on the server gets claimed, and the only
 * signal anything happened is the disk filling up. Nothing about it requires
 * owning an account, which is what makes it worth a limit of its own.</p>
 *
 * <p>Deliberately in memory, not in the database. The window is short enough
 * that losing it on restart costs an attacker one window, and the alternative
 * — a row per attempt — means writing to disk on behalf of exactly the
 * traffic you are trying not to serve.</p>
 *
 * <p>Counts only accounts actually created. A rejected registration (name
 * taken, password too weak) is somebody getting it wrong, not somebody
 * consuming the resource this protects.</p>
 */
public final class RegistrationLimiter {

    private final RLoginConfig config;
    private final Map<String, Deque<Instant>> recentByIp = new ConcurrentHashMap<>();

    public RegistrationLimiter(RLoginConfig config) {
        this.config = config;
    }

    /**
     * Whether this address may create another account right now. Does not
     * record anything: call {@link #recordRegistration} once one is created.
     */
    public boolean isAllowed(String ip, Instant now) {
        if (!config.registrationLimitEnabled() || ip == null) {
            return true;
        }
        return countWithinWindow(ip, now) < config.registrationsMaxPerIp();
    }

    public void recordRegistration(String ip, Instant now) {
        if (!config.registrationLimitEnabled() || ip == null) {
            return;
        }
        recentByIp.computeIfAbsent(ip, key -> new ArrayDeque<>()).addLast(now);
    }

    /** How long until this address may register again, or 0 if it may now. */
    public long secondsUntilAllowed(String ip, Instant now) {
        if (isAllowed(ip, now)) {
            return 0;
        }
        Deque<Instant> times = recentByIp.get(ip);
        Instant oldest = times == null ? null : times.peekFirst();
        if (oldest == null) {
            return 0;
        }
        long remaining = Duration.between(now, oldest.plus(window())).toSeconds();
        return Math.max(1, remaining);
    }

    /**
     * Drops anything that has aged out and returns what is left, so the
     * entries expire on the next read from that address rather than needing a
     * sweep of their own.
     */
    private int countWithinWindow(String ip, Instant now) {
        Deque<Instant> times = recentByIp.get(ip);
        if (times == null) {
            return 0;
        }
        Instant cutoff = now.minus(window());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
                times.removeFirst();
            }
            if (times.isEmpty()) {
                recentByIp.remove(ip);
            }
            return times.size();
        }
    }

    private Duration window() {
        return Duration.ofMinutes(Math.max(1, config.registrationsWindowMinutes()));
    }
}
