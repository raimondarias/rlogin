package com.raimondarias.rlogin.paper.hybrid;

import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-term memory of "this address already failed to prove it owns that
 * premium name", so the second attempt doesn't hit the same wall.
 *
 * <p>It exists because of a hard limit in the protocol: once the server has
 * sent an {@code EncryptionRequest}, a client with no valid Mojang session
 * gives up <em>on its own</em> ("Invalid session — try restarting the game
 * and the launcher") and drops the connection before answering. rLogin
 * never gets a chance to fall back for that attempt — there is no packet
 * left to fall back on. The decision has to be made <em>before</em> the
 * request goes out, and the only evidence that a given client can't
 * authenticate is that it already failed to.</p>
 *
 * <p>So {@link HybridAuthListener} records the abort here, and the next
 * connection with that name from that same address skips the handshake and
 * is served as a normal cracked login. In practice a cracked player using a
 * premium name is bounced once, reconnects, and is in.</p>
 *
 * <p>Deliberately keyed by name <b>and</b> IP, and deliberately short-lived:
 * this is the one piece of state that can downgrade a premium login to a
 * cracked one, so it must never be something a third party can plant for
 * someone else. Even then it is only ever consulted for names that have no
 * premium account yet — see {@code HybridAuthListener#shouldAskForPremiumProof},
 * where a name that already belongs to a verified premium account is
 * handshaked unconditionally and never reaches this class.</p>
 */
public final class AbortedPremiumHandshakes {

    private static final long TTL_SECONDS = 90;

    private final Map<String, Instant> abortedUntil = new ConcurrentHashMap<>();

    public void record(String username, String ip) {
        abortedUntil.put(key(username, ip), Instant.now().plusSeconds(TTL_SECONDS));
    }

    /**
     * Not single-use, unlike {@link HybridVerificationTracker}: a client that
     * fails this way often retries more than once (auto-reconnect, a second
     * click), and every one of those attempts should be let through as
     * cracked rather than only the first.
     */
    public boolean recentlyAbortedBy(String username, String ip) {
        Instant expiresAt = abortedUntil.get(key(username, ip));
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isAfter(Instant.now())) {
            return true;
        }
        abortedUntil.remove(key(username, ip));
        return false;
    }

    /** Drops entries nobody came back for; called on the same rare path that adds them. */
    public void purgeExpired() {
        Instant now = Instant.now();
        for (Iterator<Map.Entry<String, Instant>> it = abortedUntil.entrySet().iterator(); it.hasNext(); ) {
            if (!it.next().getValue().isAfter(now)) {
                it.remove();
            }
        }
    }

    private static String key(String username, String ip) {
        return username.toLowerCase(Locale.ROOT) + "|" + ip;
    }
}
