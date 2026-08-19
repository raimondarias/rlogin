package com.raimondarias.rlogin.common.security;

/**
 * Measures how long bcrypt actually takes on this machine and suggests
 * whether the configured cost is right.
 *
 * <p>The default cost (10) is a good middle ground on a normal server CPU,
 * but machines differ by an order of magnitude. On a slow VPS, cost 10 can
 * add a visible delay to every login; on a fast box it can be nearly free,
 * which is the same mistake in the other direction — the whole point of the
 * cost is to make stolen hashes expensive to crack.</p>
 *
 * <p>Deliberately a suggestion only: the plugin never re-hashes anything or
 * changes the config on its own. An admin who sees the line and ignores it
 * keeps exactly what they had.</p>
 */
public final class BcryptBenchmark {

    /** Below this, the cost is too cheap for stolen hashes to matter. */
    private static final long TARGET_LOW_MS = 50;
    /** Above this, every login is paying more than the server needs to. */
    private static final long TARGET_HIGH_MS = 300;
    /** The middle of the recommended band, what the suggestion aims at. */
    private static final long TARGET_MS = (TARGET_LOW_MS + TARGET_HIGH_MS) / 2;

    private BcryptBenchmark() {
    }

    /** Time one bcrypt hash at this cost takes on this machine, in milliseconds. */
    public static long measureMs(int cost) {
        PasswordHasher hasher = new PasswordHasher(cost);
        long start = System.nanoTime();
        hasher.hash("rlogin-benchmark");
        return (System.nanoTime() - start) / 1_000_000;
    }

    /**
     * The cost that would land this machine in the recommended band, clamped
     * to bcrypt's own bounds. Each step of cost doubles the time, so the
     * needed steps are {@code log2(target / measured)}.
     *
     * @return the same cost when the machine is already in the band
     */
    public static int suggestedCost(int currentCost, long measuredMs) {
        if (measuredMs <= 0) {
            return currentCost;
        }
        double ratio = (double) TARGET_MS / measuredMs;
        int steps = (int) Math.round(Math.log(ratio) / Math.log(2));
        return Math.max(4, Math.min(31, currentCost + steps));
    }
}
