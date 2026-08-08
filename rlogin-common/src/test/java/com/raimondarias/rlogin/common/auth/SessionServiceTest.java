package com.raimondarias.rlogin.common.auth;

import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.raimondarias.rlogin.common.db.SqliteStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionServiceTest {

    private SqliteStorage storage;
    private SessionService sessionService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        RLoginConfig config = RLoginConfig.load(tempDir); // remember-me activado por defecto (30 min)
        storage = new SqliteStorage(tempDir.resolve("rlogin-test.db"));
        storage.init().join();
        sessionService = new SessionService(storage, config);
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void notRememberedByDefault() {
        assertFalse(sessionService.isRemembered(UUID.randomUUID(), "1.2.3.4").join());
    }

    @Test
    void rememberedAfterCallingRemember() {
        UUID uuid = UUID.randomUUID();
        sessionService.remember(uuid, "1.2.3.4", "lobby").join();
        assertTrue(sessionService.isRemembered(uuid, "1.2.3.4").join());
    }

    @Test
    void notRememberedFromDifferentIp() {
        UUID uuid = UUID.randomUUID();
        sessionService.remember(uuid, "1.2.3.4", "lobby").join();
        assertFalse(sessionService.isRemembered(uuid, "9.9.9.9").join());
    }

    @Test
    void forgetClearsSession() {
        UUID uuid = UUID.randomUUID();
        sessionService.remember(uuid, "1.2.3.4", "lobby").join();
        sessionService.forget(uuid).join();
        assertFalse(sessionService.isRemembered(uuid, "1.2.3.4").join());
    }
}
