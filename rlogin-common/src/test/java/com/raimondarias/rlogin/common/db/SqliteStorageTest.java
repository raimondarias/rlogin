package com.raimondarias.rlogin.common.db;

import com.raimondarias.rlogin.api.RLoginAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteStorageTest {

    private SqliteStorage storage;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        storage = new SqliteStorage(tempDir.resolve("rlogin-test.db"));
        storage.init().join();
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    private RLoginAccount sampleAccount(UUID uuid) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return new RLoginAccount(uuid, "Steve", false, "bcrypt-hash", "bcrypt",
                null, false, "127.0.0.1", now, now, 0, null);
    }

    @Test
    void savedAccountIsFoundByUuid() {
        UUID uuid = UUID.randomUUID();
        storage.save(sampleAccount(uuid)).join();

        Optional<RLoginAccount> found = storage.findByUuid(uuid).join();
        assertTrue(found.isPresent());
        assertEquals("Steve", found.get().username());
        assertFalse(found.get().premium());
    }

    @Test
    void savedAccountIsFoundByUsernameCaseInsensitive() {
        UUID uuid = UUID.randomUUID();
        storage.save(sampleAccount(uuid)).join();

        assertTrue(storage.findByUsername("STEVE").join().isPresent());
        assertTrue(storage.findByUsername("steve").join().isPresent());
    }

    @Test
    void unknownUuidReturnsEmpty() {
        assertTrue(storage.findByUuid(UUID.randomUUID()).join().isEmpty());
    }

    @Test
    void savingTwiceUpdatesInsteadOfDuplicating() {
        UUID uuid = UUID.randomUUID();
        storage.save(sampleAccount(uuid)).join();
        storage.save(sampleAccount(uuid).withFailedAttempt(3, null)).join();

        RLoginAccount found = storage.findByUuid(uuid).join().orElseThrow();
        assertEquals(3, found.failedAttempts());
    }

    @Test
    void deleteRemovesAccount() {
        UUID uuid = UUID.randomUUID();
        storage.save(sampleAccount(uuid)).join();
        storage.delete(uuid).join();
        assertTrue(storage.findByUuid(uuid).join().isEmpty());
    }

    @Test
    void sessionIsValidWithinWindow() {
        UUID uuid = UUID.randomUUID();
        storage.saveSession(uuid, "1.2.3.4", "lobby", Instant.now().plusSeconds(60)).join();
        assertTrue(storage.hasValidSession(uuid, "1.2.3.4", Instant.now()).join());
    }

    @Test
    void sessionExpiresAfterItsWindow() {
        UUID uuid = UUID.randomUUID();
        storage.saveSession(uuid, "1.2.3.4", "lobby", Instant.now().plusSeconds(30)).join();
        assertFalse(storage.hasValidSession(uuid, "1.2.3.4", Instant.now().plusSeconds(60)).join());
    }

    @Test
    void sessionDoesNotMatchDifferentIp() {
        UUID uuid = UUID.randomUUID();
        storage.saveSession(uuid, "1.2.3.4", "lobby", Instant.now().plusSeconds(60)).join();
        assertFalse(storage.hasValidSession(uuid, "9.9.9.9", Instant.now()).join());
    }

    @Test
    void clearSessionRemovesIt() {
        UUID uuid = UUID.randomUUID();
        storage.saveSession(uuid, "1.2.3.4", "lobby", Instant.now().plusSeconds(60)).join();
        storage.clearSession(uuid).join();
        assertFalse(storage.hasValidSession(uuid, "1.2.3.4", Instant.now()).join());
    }
}
