package com.raimondarias.rlogin.common.auth;

import java.util.Locale;

/**
 * Who is allowed on the server ({@code auth-mode} in config.yml).
 *
 * <p>Deliberately separate from {@link UuidType}, which they are easy to
 * confuse: this decides <em>who gets in</em>, {@link UuidType} decides
 * <em>what identity</em> the ones who got in are given. A server can want
 * "everybody, and premium players keep their real Mojang UUID"
 * ({@link #AUTO} + {@link UuidType#REAL}) just as reasonably as
 * "everybody, and nobody's identity ever changes" ({@link #AUTO} +
 * {@link UuidType#CRACKED}).</p>
 */
public enum AuthMode {

    /**
     * Both kinds of player. Premium accounts are verified and join without
     * typing anything; everyone else registers and logs in with a password.
     * This is what most servers running rLogin want, and the default.
     */
    AUTO,

    /**
     * Premium accounts only. Anyone whose account Mojang doesn't vouch for
     * is refused, and {@code /register} is pointless — nobody who could use
     * it can connect in the first place.
     */
    ONLINE,

    /**
     * Passwords only. Nothing is checked against Mojang, so a premium player
     * registers and logs in like everybody else. Useful for a private server
     * that would rather not depend on Mojang being reachable — and the one
     * mode where PacketEvents isn't needed on a standalone server, because
     * there is no verification to perform.
     */
    OFFLINE;

    /** Unknown or missing values fall back to {@link #AUTO} rather than failing to start. */
    public static AuthMode parse(String raw) {
        if (raw == null) {
            return AUTO;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AUTO;
        }
    }

    /**
     * Whether rLogin has to check accounts against Mojang at all. False only
     * for {@link #OFFLINE}, which is why that mode needs no PacketEvents.
     */
    public boolean verifiesWithMojang() {
        return this != OFFLINE;
    }

    /** Whether a player who can't prove a premium account may still play, with a password. */
    public boolean allowsPasswords() {
        return this != ONLINE;
    }
}
