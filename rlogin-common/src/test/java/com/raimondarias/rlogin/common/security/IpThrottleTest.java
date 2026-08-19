package com.raimondarias.rlogin.common.security;

import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.raimondarias.rlogin.common.db.SqliteStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpThrottleTest {

    private SqliteStorage storage;
    private IpThrottle throttle;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        RLoginConfig config = RLoginConfig.load(tempDir); // defaults: max-attempts 5, lockout 60s
        storage = new SqliteStorage(tempDir.resolve("rlogin-test.db"));
        storage.init().join();
        throttle = new IpThrottle(storage, config, new BruteforceGuard(config));
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void locksAddressAfterMaxAttempts() {
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure("203.0.113.7", "Steve", now);
        }
        assertTrue(throttle.lockedSecondsRemaining("203.0.113.7", now) > 0);
    }

    @Test
    void everyFailureIsMirroredToStorage() {
        Instant now = Instant.now();
        throttle.recordFailure("203.0.113.7", "Steve", now);
        throttle.recordFailure("203.0.113.7", "Steve", now);
        int counted = storage.countLoginFailures("203.0.113.7", "Steve", now.minusSeconds(60)).join();
        assertEquals(2, counted);
    }

    @Test
    void successClearsStoredFailures() {
        Instant now = Instant.now();
        throttle.recordFailure("203.0.113.7", "Steve", now);
        throttle.recordSuccess("203.0.113.7", "Steve");
        assertEquals(0, storage.countLoginFailures("203.0.113.7", "Steve", now.minusSeconds(60)).join());
    }

    @Test
    void sharedRecordCountsDifferentNamesFromSameAddress() {
        // The distributed limit counts per address too: two failed names from
        // the same address are two failures for the address.
        Instant now = Instant.now();
        throttle.recordFailure("203.0.113.7", "Alice", now);
        throttle.recordFailure("203.0.113.7", "Bob", now);
        assertEquals(2, storage.countLoginFailures("203.0.113.7", "Carol", now.minusSeconds(60)).join());
    }

    @Test
    void storedFailuresAgeOutOfTheWindow() {
        Instant earlier = Instant.now().minusSeconds(120);
        throttle.recordFailure("203.0.113.7", "Steve", earlier);
        assertEquals(0, storage.countLoginFailures("203.0.113.7", "Steve", Instant.now().minusSeconds(60)).join());
    }
}
