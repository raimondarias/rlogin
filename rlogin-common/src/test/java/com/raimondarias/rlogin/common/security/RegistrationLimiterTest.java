package com.raimondarias.rlogin.common.security;

import com.raimondarias.rlogin.common.config.RLoginConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cap on how many accounts one address may create. Without it a single
 * address can claim every name on the server and grow the database until the
 * disk fills, none of which requires owning an account.
 */
class RegistrationLimiterTest {

    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    private static RegistrationLimiter limiterWith(Path dir, String yaml) throws IOException {
        Files.writeString(dir.resolve("config.yml"), yaml, StandardCharsets.UTF_8);
        RLoginConfig config = RLoginConfig.load(dir, "default-config.yml", new ArrayList<>());
        return new RegistrationLimiter(config);
    }

    @Test
    @DisplayName("allows up to the cap, then refuses")
    void allowsUpToTheCap(@TempDir Path dir) throws IOException {
        RegistrationLimiter limiter = limiterWith(dir, "security:\n  registration:\n    max-per-ip: 2\n");

        assertTrue(limiter.isAllowed("1.2.3.4", NOW));
        limiter.recordRegistration("1.2.3.4", NOW);
        assertTrue(limiter.isAllowed("1.2.3.4", NOW));
        limiter.recordRegistration("1.2.3.4", NOW);

        assertFalse(limiter.isAllowed("1.2.3.4", NOW), "the third from this address is over the cap");
    }

    @Test
    @DisplayName("the cap is per address, not global")
    void capIsPerAddress(@TempDir Path dir) throws IOException {
        RegistrationLimiter limiter = limiterWith(dir, "security:\n  registration:\n    max-per-ip: 1\n");

        limiter.recordRegistration("1.2.3.4", NOW);

        assertFalse(limiter.isAllowed("1.2.3.4", NOW));
        assertTrue(limiter.isAllowed("5.6.7.8", NOW), "one address filling up must not lock out everyone else");
    }

    @Test
    @DisplayName("entries age out of the window")
    void entriesExpire(@TempDir Path dir) throws IOException {
        RegistrationLimiter limiter = limiterWith(dir,
                "security:\n  registration:\n    max-per-ip: 1\n    window-minutes: 60\n");

        limiter.recordRegistration("1.2.3.4", NOW);
        assertFalse(limiter.isAllowed("1.2.3.4", NOW));

        assertTrue(limiter.isAllowed("1.2.3.4", NOW.plus(Duration.ofMinutes(61))),
                "the limit is a rate, not a lifetime quota");
    }

    @Test
    @DisplayName("reports how long the wait is")
    void reportsTheWait(@TempDir Path dir) throws IOException {
        RegistrationLimiter limiter = limiterWith(dir,
                "security:\n  registration:\n    max-per-ip: 1\n    window-minutes: 10\n");

        assertEquals(0, limiter.secondsUntilAllowed("1.2.3.4", NOW), "nothing to wait for yet");

        limiter.recordRegistration("1.2.3.4", NOW);
        long wait = limiter.secondsUntilAllowed("1.2.3.4", NOW.plus(Duration.ofMinutes(4)));

        assertEquals(360, wait, "six minutes left of a ten-minute window");
    }

    @Test
    @DisplayName("max-per-ip: 0 turns the cap off")
    void zeroDisables(@TempDir Path dir) throws IOException {
        RegistrationLimiter limiter = limiterWith(dir, "security:\n  registration:\n    max-per-ip: 0\n");

        for (int i = 0; i < 50; i++) {
            assertTrue(limiter.isAllowed("1.2.3.4", NOW));
            limiter.recordRegistration("1.2.3.4", NOW);
        }
    }

    @Test
    @DisplayName("a missing address is not something to refuse over")
    void nullAddressIsAllowed(@TempDir Path dir) throws IOException {
        RegistrationLimiter limiter = limiterWith(dir, "security:\n  registration:\n    max-per-ip: 1\n");

        assertTrue(limiter.isAllowed(null, NOW));
        limiter.recordRegistration(null, NOW);
        assertTrue(limiter.isAllowed(null, NOW));
    }
}
