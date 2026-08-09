package com.raimondarias.rlogin.paper.hybrid;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hand-off between {@link HybridAuthListener} (packet layer — proves a
 * connecting client genuinely owns a premium account via Mojang's session
 * server) and {@code JoinListener} (Bukkit layer — decides premium vs
 * cracked for {@code AsyncPlayerPreLoginEvent}).
 *
 * <p>Deliberately dumb: a short-lived, single-use "this username was just
 * cryptographically verified" flag, nothing more. The packet-level
 * handshake always finishes a moment before the corresponding Bukkit login
 * events fire for the same connection, so a short TTL is enough.
 *
 * <p>This is the <em>backstop</em>, not the main path. Normally {@link
 * NmsConnectionAccess} has already given the connection its real Mojang
 * UUID, and {@code JoinListener} recognises the player as premium from that
 * alone — the same way it recognises a Velocity-forwarded one. This flag is
 * what keeps auto-login working anyway on a server build where that
 * couldn't be applied (or with {@code premium.standalone-premium-uuid}
 * turned off), where the UUID stays the offline one and so proves nothing
 * by itself.</p>
 */
public final class HybridVerificationTracker {

    private static final long TTL_SECONDS = 30;

    private final Map<String, Instant> verified = new ConcurrentHashMap<>();

    public void markVerified(String username) {
        verified.put(username.toLowerCase(Locale.ROOT), Instant.now().plusSeconds(TTL_SECONDS));
        purgeExpired();
    }

    /**
     * Drops flags nobody came back to claim. Without this the map only ever
     * shrinks when a flag is consumed, so a player who is verified and then
     * drops before the Bukkit login events fire leaves an entry behind for
     * as long as the server runs. Swept here because this is the only place
     * that adds anything, and it happens once per premium login.
     */
    private void purgeExpired() {
        Instant now = Instant.now();
        verified.values().removeIf(expiresAt -> !expiresAt.isAfter(now));
    }

    /** Single-use: true (and consumed) only once per successful handshake. */
    public boolean consumeIfVerified(String username) {
        Instant expiresAt = verified.remove(username.toLowerCase(Locale.ROOT));
        return expiresAt != null && expiresAt.isAfter(Instant.now());
    }
}
