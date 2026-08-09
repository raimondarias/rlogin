package com.raimondarias.rlogin.paper.hybrid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The record of "this connection proved it owns the account", handed from the
 * packet layer to the join listener.
 *
 * <p>Single-use is the whole safety property. The proof belongs to one
 * connection; if reading it left it in place, the next connection under that
 * name would inherit somebody else's Mojang handshake and be let in without a
 * password.</p>
 */
class HybridVerificationTrackerTest {

    private final HybridVerificationTracker tracker = new HybridVerificationTracker();

    @Test
    @DisplayName("a name nobody verified is not verified")
    void unknownIsNotVerified() {
        assertFalse(tracker.consumeIfVerified("Notch"));
    }

    @Test
    @DisplayName("a verified name reads true exactly once")
    void proofIsSingleUse() {
        tracker.markVerified("Notch");

        assertTrue(tracker.consumeIfVerified("Notch"));
        assertFalse(tracker.consumeIfVerified("Notch"),
                "the second connection under this name must not inherit the first one's proof");
    }

    @Test
    @DisplayName("names are matched regardless of case")
    void matchingIgnoresCase() {
        tracker.markVerified("Notch");

        // Mojang echoes the canonical spelling, which need not be what the client sent.
        assertTrue(tracker.consumeIfVerified("notch"));
    }

    @Test
    @DisplayName("one player's proof does not verify another")
    void proofIsPerName() {
        tracker.markVerified("Notch");

        assertFalse(tracker.consumeIfVerified("Herobrine"));
        assertTrue(tracker.consumeIfVerified("Notch"), "the wrong name must not consume the right one's proof");
    }
}
