package com.raimondarias.rlogin.common.security;

import com.raimondarias.rlogin.common.config.RLoginConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BruteforceGuardTest {

    // Valores por defecto de default-config.yml: max-attempts=5, lockout-seconds=60,
    // lockout-multiplier=2.0, max-lockout-seconds=3600.

    private RLoginConfig loadDefaultConfig(Path tempDir) throws IOException {
        return RLoginConfig.load(tempDir);
    }

    @Test
    void noLockoutBelowMaxAttempts(@TempDir Path tempDir) throws IOException {
        BruteforceGuard guard = new BruteforceGuard(loadDefaultConfig(tempDir));
        assertEquals(0, guard.lockoutSecondsFor(1));
        assertEquals(0, guard.lockoutSecondsFor(4));
    }

    @Test
    void locksOutOnceMaxAttemptsReached(@TempDir Path tempDir) throws IOException {
        BruteforceGuard guard = new BruteforceGuard(loadDefaultConfig(tempDir));
        assertEquals(60, guard.lockoutSecondsFor(5));
    }

    @Test
    void lockoutGrowsExponentially(@TempDir Path tempDir) throws IOException {
        BruteforceGuard guard = new BruteforceGuard(loadDefaultConfig(tempDir));
        assertEquals(60, guard.lockoutSecondsFor(5));   // 60 * 2^0
        assertEquals(120, guard.lockoutSecondsFor(6));  // 60 * 2^1
        assertEquals(240, guard.lockoutSecondsFor(7));  // 60 * 2^2
    }

    @Test
    void lockoutIsCappedAtMaximum(@TempDir Path tempDir) throws IOException {
        BruteforceGuard guard = new BruteforceGuard(loadDefaultConfig(tempDir));
        assertEquals(3600, guard.lockoutSecondsFor(20)); // se dispararía muy por encima de 3600 sin el cap
    }

    @Test
    void nextLockUntilIsNullWhenNotLocked(@TempDir Path tempDir) throws IOException {
        BruteforceGuard guard = new BruteforceGuard(loadDefaultConfig(tempDir));
        assertNull(guard.nextLockUntil(1, Instant.now()));
    }

    @Test
    void nextLockUntilIsInTheFutureWhenLocked(@TempDir Path tempDir) throws IOException {
        BruteforceGuard guard = new BruteforceGuard(loadDefaultConfig(tempDir));
        Instant now = Instant.now();
        Instant lockUntil = guard.nextLockUntil(5, now);
        assertTrue(lockUntil.isAfter(now));
        assertEquals(now.plusSeconds(60), lockUntil);
    }
}
