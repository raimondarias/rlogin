package com.raimondarias.rlogin.paper;

import com.raimondarias.rlogin.api.AuthReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who is allowed to move, and why. Everything that unfreezes a player goes
 * through here, so a wrong answer is either a player stuck at the login
 * prompt forever or one walking around without having logged in.
 */
class AuthSessionManagerTest {

    private final AuthSessionManager sessions = new AuthSessionManager();

    @Test
    @DisplayName("an unknown player is not authenticated")
    void unknownIsNotAuthenticated() {
        assertFalse(sessions.isAuthenticated(UUID.randomUUID()));
        assertNull(sessions.reasonFor(UUID.randomUUID()));
    }

    @Test
    @DisplayName("marking remembers both the fact and the reason")
    void marksWithReason() {
        UUID uuid = UUID.randomUUID();
        sessions.markAuthenticated(uuid, AuthReason.PREMIUM_MOJANG_API);

        assertTrue(sessions.isAuthenticated(uuid));
        assertEquals(AuthReason.PREMIUM_MOJANG_API, sessions.reasonFor(uuid));
    }

    @Test
    @DisplayName("forgetting clears authentication, so a reconnect starts over")
    void forgetClears() {
        UUID uuid = UUID.randomUUID();
        sessions.markAuthenticated(uuid, AuthReason.PASSWORD);
        sessions.forget(uuid);

        assertFalse(sessions.isAuthenticated(uuid), "a disconnected player must not stay authenticated");
        assertNull(sessions.reasonFor(uuid));
    }

    @Test
    @DisplayName("the proxy's already-greeted flag is per player and cleared on quit")
    void alreadyGreetedIsPerPlayerAndCleared() {
        UUID hopping = UUID.randomUUID();
        UUID arriving = UUID.randomUUID();

        sessions.markAlreadyGreeted(hopping);

        assertTrue(sessions.wasAlreadyGreeted(hopping));
        assertFalse(sessions.wasAlreadyGreeted(arriving),
                "one player's server switch must not silence another player's welcome");

        // Left set, a reconnecting player would never be greeted again for the
        // rest of the server's uptime.
        sessions.forget(hopping);
        assertFalse(sessions.wasAlreadyGreeted(hopping));
    }

    @Test
    @DisplayName("marking twice keeps the most recent reason")
    void reasonIsOverwritten() {
        UUID uuid = UUID.randomUUID();
        sessions.markAuthenticated(uuid, AuthReason.REMEMBERED_SESSION);
        sessions.markAuthenticated(uuid, AuthReason.PASSWORD);

        assertEquals(AuthReason.PASSWORD, sessions.reasonFor(uuid));
    }
}
