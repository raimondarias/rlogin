package com.raimondarias.rlogin.common.auth;

import java.util.Locale;

/**
 * Which UUID a player ends up with, chosen by {@code premium.uuid-type}.
 *
 * <p>Only meaningful on a standalone server running {@code online-mode:
 * false}, where the server would otherwise always derive the UUID from the
 * name. Behind a proxy (or in {@code online-mode: true}) the identity is
 * already decided before rLogin sees the connection, and this is ignored.</p>
 *
 * <p>The same three choices nLogin offers, under the names an admin coming
 * from it would look for — {@code offline} is accepted as a synonym of
 * {@link #CRACKED} for exactly that reason.</p>
 */
public enum UuidType {

    /**
     * Verified premium players join as their real Mojang account; everyone
     * else keeps the offline UUID. The default, and the only mode where a
     * premium player and a cracked player using the same name are two
     * distinct identities — which is the whole point of verifying at all.
     */
    REAL,

    /**
     * Everyone gets the offline UUID derived from their name, premium
     * included. Auto-login still works; the identity just never changes.
     * This is what an existing offline-mode world/database needs if its
     * player data must keep working untouched.
     */
    CRACKED,

    /**
     * A UUID generated at random the first time a name connects, then reused
     * for that name from then on, premium and cracked alike. Detaches
     * identity from both the name and the Mojang account, so a player can
     * move between premium and cracked without losing their data.
     */
    RANDOM;

    /** Unknown or missing values fall back to {@link #REAL} rather than failing to start. */
    public static UuidType parse(String raw) {
        if (raw == null) {
            return REAL;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("OFFLINE")) {
            return CRACKED; // nLogin's name for the same thing.
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return REAL;
        }
    }

    /** Whether this mode ever needs rLogin to override the UUID the server would pick on its own. */
    public boolean overridesServerUuid() {
        return this != CRACKED;
    }
}
