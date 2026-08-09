package com.raimondarias.rlogin.common.config;

import java.util.Locale;

/**
 * What the proxy does with a player once a backend reports them logged in
 * ({@code after-login.action} in velocity-config.yml).
 *
 * <p>A single choice rather than a set of independent switches: "go back to
 * the previous server" and "send to a lobby" are alternatives, not things
 * you can meaningfully ask for at the same time, and expressing them as two
 * booleans invites a config that contradicts itself.</p>
 */
public enum AfterLogin {

    /** Leave them where they are — right when players log in on the lobby they were headed to anyway. */
    STAY,

    /** Move them to one of {@code after-login.servers}, chosen at random. */
    SEND,

    /** Back to the server they were on last time, falling back to {@link #SEND} when there isn't one. */
    PREVIOUS;

    /** Anything unrecognised means {@link #STAY}: doing nothing is the safe reading of a typo. */
    public static AfterLogin parse(String raw) {
        if (raw == null) {
            return STAY;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return STAY;
        }
    }

    /** Whether this mode ever needs the proxy to remember where players have been. */
    public boolean tracksHistory() {
        return this == PREVIOUS;
    }
}
