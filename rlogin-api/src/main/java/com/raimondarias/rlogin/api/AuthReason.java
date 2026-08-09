package com.raimondarias.rlogin.api;

/** Why a player is considered authenticated at a given moment. */
public enum AuthReason {
    /** Velocity verified the account against Mojang via Modern Forwarding (forceOnlineMode). */
    PREMIUM_FORWARDED,
    /** Verified as premium by querying the Mojang API directly (standalone mode). */
    PREMIUM_MOJANG_API,
    /** The whole server runs in online-mode: true; everyone who joins is already verified. */
    PREMIUM_SERVER_ONLINE_MODE,
    /** Bedrock player authenticated via Floodgate. */
    FLOODGATE,
    /** Typed /login with the correct password. */
    PASSWORD,
    /** Valid "remember me" session by IP+UUID within the configured window. */
    REMEMBERED_SESSION,
    /** An admin forced the login manually. */
    FORCED_BY_ADMIN,
    /** Holds {@code rlogin.bypass} — never asked to log in in the first place (NPCs, test bots). */
    BYPASS_PERMISSION;

    /**
     * Whether this is an account Mojang (or Floodgate, for Bedrock) already
     * vouched for, so the player was let in without typing anything — the
     * ones worth telling the player about on join.
     */
    public boolean isAutomaticPremium() {
        return this == PREMIUM_FORWARDED
                || this == PREMIUM_MOJANG_API
                || this == PREMIUM_SERVER_ONLINE_MODE
                || this == FLOODGATE;
    }
}
