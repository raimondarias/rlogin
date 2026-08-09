package com.raimondarias.rlogin.paper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The detection that decides whether rLogin verifies a connection at all.
 * Getting it wrong in either direction is a security bug, not a cosmetic one:
 * verifying when something else already did wastes a handshake, while
 * <em>not</em> verifying because a proxy was assumed lets anyone in under any
 * name.
 */
class ServerTopologyTest {

    @Test
    @DisplayName("online-mode wins over everything: the server already verified")
    void onlineModeWins() {
        assertEquals(ServerTopology.ONLINE_MODE, ServerTopology.decide(true, false));
        // Even with forwarding configured. A server in online-mode verified the
        // connection itself, whatever is in front of it.
        assertEquals(ServerTopology.ONLINE_MODE, ServerTopology.decide(true, true));
    }

    @Test
    @DisplayName("forwarding without online-mode means somebody upstream verified")
    void forwardingMeansProxy() {
        assertEquals(ServerTopology.BEHIND_PROXY, ServerTopology.decide(false, true));
    }

    @Test
    @DisplayName("no online-mode and no forwarding is the setup rLogin has to cover itself")
    void standaloneIsTheFallback() {
        assertEquals(ServerTopology.STANDALONE_OFFLINE, ServerTopology.decide(false, false));
    }

    @Test
    @DisplayName("only the standalone case verifies for itself")
    void onlyStandaloneVerifies() {
        assertTrue(ServerTopology.STANDALONE_OFFLINE.needsOwnVerification());
        assertFalse(ServerTopology.BEHIND_PROXY.needsOwnVerification(),
                "a proxy already verified; doing it again would mean verifying twice");
        assertFalse(ServerTopology.ONLINE_MODE.needsOwnVerification());
    }

    @Test
    @DisplayName("an unreadable server layout falls back to verifying, not to trusting")
    void unknownLayoutFallsBackSafely() {
        // serverRoot() returns null when the directory layout isn't recognised, which
        // reaches decide() as "no forwarding". The safe reading of "I don't know" is
        // that nothing verified this connection yet.
        assertEquals(ServerTopology.STANDALONE_OFFLINE, ServerTopology.decide(false, false));
    }
}
