package com.raimondarias.rlogin.common.security;

import com.raimondarias.rlogin.common.config.RLoginConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryThrottleTest {

    private RLoginConfig configWith(int maxAttempts, int lockoutMinutes, Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("config.yml"),
                "security:\n  recovery:\n    max-attempts: " + maxAttempts
                        + "\n    lockout-minutes: " + lockoutMinutes + "\n",
                StandardCharsets.UTF_8);
        return RLoginConfig.load(tempDir);
    }

    @Test
    void noLockoutBelowTheLimit(@TempDir Path tempDir) throws IOException {
        RecoveryThrottle throttle = new RecoveryThrottle(configWith(5, 15, tempDir));
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();

        for (int i = 0; i < 4; i++) {
            throttle.recordFailure("203.0.113.7", uuid, now);
        }
        assertEquals(0, throttle.lockedSecondsRemaining("203.0.113.7", uuid, now));
    }

    @Test
    void locksOutBothAddressAndAccountAtTheLimit(@TempDir Path tempDir) throws IOException {
        RecoveryThrottle throttle = new RecoveryThrottle(configWith(3, 15, tempDir));
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();

        throttle.recordFailure("203.0.113.7", uuid, now);
        throttle.recordFailure("203.0.113.7", uuid, now);
        throttle.recordFailure("203.0.113.7", uuid, now);

        long remaining = throttle.lockedSecondsRemaining("203.0.113.7", uuid, now);
        assertTrue(remaining > 0 && remaining <= 15 * 60, "expected a lockout, got " + remaining);
    }

    @Test
    void accountIsLockedEvenFromAnotherAddress(@TempDir Path tempDir) throws IOException {
        RecoveryThrottle throttle = new RecoveryThrottle(configWith(3, 15, tempDir));
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();

        throttle.recordFailure("203.0.113.7", uuid, now);
        throttle.recordFailure("198.51.100.2", uuid, now);
        throttle.recordFailure("192.0.2.9", uuid, now);

        // The account crossed the limit, so a fourth address is locked out too.
        assertTrue(throttle.lockedSecondsRemaining("198.51.100.2", uuid, now) > 0);
    }

    @Test
    void successClearsTheLockout(@TempDir Path tempDir) throws IOException {
        RecoveryThrottle throttle = new RecoveryThrottle(configWith(3, 15, tempDir));
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();

        throttle.recordFailure("203.0.113.7", uuid, now);
        throttle.recordFailure("203.0.113.7", uuid, now);
        throttle.recordFailure("203.0.113.7", uuid, now);
        assertTrue(throttle.lockedSecondsRemaining("203.0.113.7", uuid, now) > 0);

        throttle.recordSuccess("203.0.113.7", uuid);
        assertEquals(0, throttle.lockedSecondsRemaining("203.0.113.7", uuid, now));
    }

    @Test
    void disabledWhenMaxAttemptsIsZero(@TempDir Path tempDir) throws IOException {
        RecoveryThrottle throttle = new RecoveryThrottle(configWith(0, 15, tempDir));
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();

        for (int i = 0; i < 50; i++) {
            throttle.recordFailure("203.0.113.7", uuid, now);
        }
        assertEquals(0, throttle.lockedSecondsRemaining("203.0.113.7", uuid, now));
    }

    @Test
    void lockoutExpires(@TempDir Path tempDir) throws IOException {
        RecoveryThrottle throttle = new RecoveryThrottle(configWith(3, 15, tempDir));
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();

        throttle.recordFailure("203.0.113.7", uuid, now);
        throttle.recordFailure("203.0.113.7", uuid, now);
        throttle.recordFailure("203.0.113.7", uuid, now);
        assertTrue(throttle.lockedSecondsRemaining("203.0.113.7", uuid, now) > 0);

        // After the window, the same address+account is free again.
        Instant later = now.plusSeconds(15 * 60 + 1);
        assertEquals(0, throttle.lockedSecondsRemaining("203.0.113.7", uuid, later));
    }
}
