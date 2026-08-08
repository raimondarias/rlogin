package com.raimondarias.rlogin.common.auth;

import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.raimondarias.rlogin.common.db.SqliteStorage;
import com.raimondarias.rlogin.common.security.PremiumNameGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountServiceTest {

    private SqliteStorage storage;
    private AccountService accountService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        // Desactiva la protección de nombres premium para no depender de la API de Mojang en los tests.
        Files.writeString(tempDir.resolve("config.yml"),
                "premium:\n  protect-premium-names: false\nsecurity:\n  bruteforce:\n    max-attempts: 3\n"
                        + "    lockout-seconds: 60\n",
                StandardCharsets.UTF_8);
        RLoginConfig config = RLoginConfig.load(tempDir);

        storage = new SqliteStorage(tempDir.resolve("rlogin-test.db"));
        storage.init().join();

        PremiumChecker premiumChecker = new PremiumChecker(config);
        PremiumNameGuard premiumNameGuard = new PremiumNameGuard(config, premiumChecker);
        accountService = new AccountService(storage, config, premiumNameGuard);
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void registerThenLoginWithCorrectPasswordSucceeds() {
        UUID uuid = UUID.randomUUID();
        var registerResult = accountService.register(uuid, "Steve", "hunter22", "hunter22").join();
        assertEquals(AccountService.RegisterResult.SUCCESS, registerResult);

        var outcome = accountService.login(uuid, "hunter22", null, "127.0.0.1").join();
        assertEquals(AccountService.LoginResult.SUCCESS, outcome.result());
    }

    @Test
    void registeringTwiceFails() {
        UUID uuid = UUID.randomUUID();
        accountService.register(uuid, "Steve", "hunter22", "hunter22").join();
        var second = accountService.register(uuid, "Steve", "otraClave1", "otraClave1").join();
        assertEquals(AccountService.RegisterResult.ALREADY_REGISTERED, second);
    }

    @Test
    void registerWithMismatchedPasswordsFails() {
        var result = accountService.register(UUID.randomUUID(), "Steve", "hunter22", "otraCosa").join();
        assertEquals(AccountService.RegisterResult.PASSWORDS_DONT_MATCH, result);
    }

    @Test
    void loginWithoutAccountReturnsNotRegistered() {
        var outcome = accountService.login(UUID.randomUUID(), "loquesea", null, "127.0.0.1").join();
        assertEquals(AccountService.LoginResult.NOT_REGISTERED, outcome.result());
    }

    @Test
    void wrongPasswordDecrementsAttemptsLeft() {
        UUID uuid = UUID.randomUUID();
        accountService.register(uuid, "Steve", "hunter22", "hunter22").join();

        var outcome = accountService.login(uuid, "incorrecta", null, "127.0.0.1").join();
        assertEquals(AccountService.LoginResult.WRONG_PASSWORD, outcome.result());
        assertEquals(2, outcome.attemptsLeft()); // max-attempts=3, este fue el 1er fallo
    }

    @Test
    void accountLocksAfterTooManyFailedAttempts() {
        UUID uuid = UUID.randomUUID();
        accountService.register(uuid, "Steve", "hunter22", "hunter22").join();

        accountService.login(uuid, "mal1", null, "127.0.0.1").join();
        accountService.login(uuid, "mal2", null, "127.0.0.1").join();
        accountService.login(uuid, "mal3", null, "127.0.0.1").join(); // 3er fallo = límite

        var lockedAttempt = accountService.login(uuid, "hunter22", null, "127.0.0.1").join();
        assertEquals(AccountService.LoginResult.LOCKED, lockedAttempt.result());
        assertTrue(lockedAttempt.lockedSecondsRemaining() > 0);
    }

    @Test
    void premiumAccountNeverAsksForPassword() {
        UUID uuid = UUID.randomUUID();
        accountService.upsertPremium(uuid, "Steve", "127.0.0.1").join();

        var outcome = accountService.login(uuid, "cualquiera", null, "127.0.0.1").join();
        assertEquals(AccountService.LoginResult.PREMIUM_NO_PASSWORD, outcome.result());
    }

    @Test
    void changePasswordWorksWithCorrectOldPassword() {
        UUID uuid = UUID.randomUUID();
        accountService.register(uuid, "Steve", "viejaClave1", "viejaClave1").join();

        boolean changed = accountService.changePassword(uuid, "viejaClave1", "nuevaClave2").join();
        assertTrue(changed);

        var outcome = accountService.login(uuid, "nuevaClave2", null, "127.0.0.1").join();
        assertEquals(AccountService.LoginResult.SUCCESS, outcome.result());
    }

    @Test
    void changePasswordFailsWithWrongOldPassword() {
        UUID uuid = UUID.randomUUID();
        accountService.register(uuid, "Steve", "viejaClave1", "viejaClave1").join();

        boolean changed = accountService.changePassword(uuid, "incorrecta", "nuevaClave2").join();
        assertEquals(false, changed);
    }

    @Test
    void unregisterDeletesAccount() {
        UUID uuid = UUID.randomUUID();
        accountService.register(uuid, "Steve", "hunter22", "hunter22").join();
        accountService.unregister(uuid).join();

        var outcome = accountService.login(uuid, "hunter22", null, "127.0.0.1").join();
        assertEquals(AccountService.LoginResult.NOT_REGISTERED, outcome.result());
    }
}
