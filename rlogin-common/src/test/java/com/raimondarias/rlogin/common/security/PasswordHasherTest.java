package com.raimondarias.rlogin.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(4); // minimum cost: fast tests

    @Test
    void hashThenVerifySucceeds() {
        String hash = hasher.hash("correct-horse-battery-staple");
        assertTrue(hasher.verify("correct-horse-battery-staple", hash));
    }

    @Test
    void verifyFailsWithWrongPassword() {
        String hash = hasher.hash("myPassword123");
        assertFalse(hasher.verify("otherPassword", hash));
    }

    @Test
    void sameSaltNeverRepeatsAcrossHashes() {
        String h1 = hasher.hash("same-password");
        String h2 = hasher.hash("same-password");
        assertNotEquals(h1, h2); // bcrypt generates a different salt every time
        assertTrue(hasher.verify("same-password", h1));
        assertTrue(hasher.verify("same-password", h2));
    }

    @Test
    void verifyFailsGracefullyWithNullOrEmptyHash() {
        assertFalse(hasher.verify("whatever", null));
        assertFalse(hasher.verify("whatever", ""));
    }

    @Test
    void costBelowMinimumIsClampedInsteadOfRejected() {
        // 0 is bumped up to the minimum (4) instead of failing; we test with a real
        // low cost to keep the test fast (31 would be computationally very expensive).
        PasswordHasher tooLow = new PasswordHasher(0);
        assertTrue(tooLow.verify("x", tooLow.hash("x")));
    }
}
