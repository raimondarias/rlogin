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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void transferTokenRedeemsOnce() {
        UUID uuid = UUID.randomUUID();
        String token = sessionService.issueTransferToken(uuid).join();
        assertNotNull(token);
        assertTrue(sessionService.redeemTransferToken(uuid, token).join());
        // Single-use: the same code must never work twice.
        assertFalse(sessionService.redeemTransferToken(uuid, token).join());
    }

    @Test
    void transferTokenRejectsUnknownOrBlankCode() {
        assertFalse(sessionService.redeemTransferToken(UUID.randomUUID(), "no-such-code").join());
        assertFalse(sessionService.redeemTransferToken(UUID.randomUUID(), "  ").join());
    }

    @Test
    void transferTokenOnlyRedeemsForItsOwner() {
        UUID owner = UUID.randomUUID();
        String token = sessionService.issueTransferToken(owner).join();
        assertFalse(sessionService.redeemTransferToken(UUID.randomUUID(), token).join());
    }

    @Test
    void transferTokensAreIndependentOfRememberMeSessions() {
        // Minting a transfer code must not cancel the player's own remember-me
        // session (they live in different tables for exactly this reason).
        UUID uuid = UUID.randomUUID();
        sessionService.remember(uuid, "1.2.3.4", "lobby").join();
        String token = sessionService.issueTransferToken(uuid).join();
        assertNotNull(token);
        assertTrue(sessionService.isRemembered(uuid, "1.2.3.4").join());
    }
}
