package com.raimondarias.rlogin.common.auth;

import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.raimondarias.rlogin.common.db.SqliteStorage;
import com.raimondarias.rlogin.common.security.PasswordHasher;
import com.raimondarias.rlogin.common.security.PasswordPolicy;
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

class RecoveryServiceTest {

    private SqliteStorage storage;
    private AccountService accountService;
    private RecoveryService recoveryService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        // A tight recovery limit so the throttle is exercised without 5 attempts.
        Files.writeString(tempDir.resolve("config.yml"),
                "premium:\n  protect-premium-names: false\nsecurity:\n  recovery:\n    max-attempts: 2\n",
                StandardCharsets.UTF_8);
        RLoginConfig config = RLoginConfig.load(tempDir);

        storage = new SqliteStorage(tempDir.resolve("rlogin-test.db"));
        storage.init().join();

        PremiumChecker premiumChecker = new PremiumChecker(config);
        PremiumNameGuard guard = new PremiumNameGuard(config, premiumChecker);
        accountService = new AccountService(storage, config, guard);
        recoveryService = new RecoveryService(storage, config,
                new PasswordHasher(config.bcryptCost()), new PasswordPolicy(config));
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void recoverWithACorrectCodeSetsANewPassword() {
        UUID uuid = UUID.randomUUID();
        accountService.register(uuid, "Steve", "viejaClave1", "viejaClave1", "203.0.113.7").join();
        List<String> codes = recoveryService.issueCodes(uuid).join();

        var outcome = recoveryService.recover(uuid, "203.0.113.7", codes.get(0), "nuevaClave2").join();

        assertEquals(RecoveryService.RecoverResult.SUCCESS, outcome.result());
        var login = accountService.login(uuid, "nuevaClave2", null, "203.0.113.7").join();
        assertEquals(AccountService.LoginResult.SUCCESS, login.result());
    }

    @Test
    void wrongCodeIsRejected() {
        UUID uuid = UUID.randomUUID();
        accountService.register(uuid, "Steve", "viejaClave1", "viejaClave1", "203.0.113.7").join();
        recoveryService.issueCodes(uuid).join();

        var outcome = recoveryService.recover(uuid, "203.0.113.7", "ZZZZ-ZZZZ", "nuevaClave2").join();

        assertEquals(RecoveryService.RecoverResult.WRONG_CODE, outcome.result());
    }

    @Test
    void aCodeWorksOnlyOnce() {
        UUID uuid = UUID.randomUUID();
        accountService.register(uuid, "Steve", "viejaClave1", "viejaClave1", "203.0.113.7").join();
        String code = recoveryService.issueCodes(uuid).join().get(0);

        assertEquals(RecoveryService.RecoverResult.SUCCESS,
                recoveryService.recover(uuid, "203.0.113.7", code, "nuevaClave2").join().result());

        // The spent code must not open the account a second time.
        var again = recoveryService.recover(uuid, "203.0.113.7", code, "otraClave3").join();
        assertEquals(RecoveryService.RecoverResult.WRONG_CODE, again.result());
    }

    @Test
    void tooManyWrongCodesThrottleTheAddressAndAccount() {
        UUID uuid = UUID.randomUUID();
        accountService.register(uuid, "Steve", "viejaClave1", "viejaClave1", "203.0.113.7").join();
        recoveryService.issueCodes(uuid).join();

        assertEquals(RecoveryService.RecoverResult.WRONG_CODE,
                recoveryService.recover(uuid, "203.0.113.7", "AAAA-AAAA", "nuevaClave2").join().result());
        assertEquals(RecoveryService.RecoverResult.WRONG_CODE,
                recoveryService.recover(uuid, "203.0.113.7", "BBBB-BBBB", "nuevaClave2").join().result());

        var third = recoveryService.recover(uuid, "203.0.113.7", "CCCC-CCCC", "nuevaClave2").join();
        assertEquals(RecoveryService.RecoverResult.THROTTLED, third.result());
        assertTrue(third.lockedSecondsRemaining() > 0);
    }

    @Test
    void recoveryRejectsAWeakPassword() {
        UUID uuid = UUID.randomUUID();
        accountService.register(uuid, "Steve", "viejaClave1", "viejaClave1", "203.0.113.7").join();
        String code = recoveryService.issueCodes(uuid).join().get(0);

        var outcome = recoveryService.recover(uuid, "203.0.113.7", code, "123456").join();

        assertEquals(RecoveryService.RecoverResult.PASSWORD_REJECTED, outcome.result());
        // Nothing was consumed: the same code still works after the rejection.
        assertEquals(RecoveryService.RecoverResult.SUCCESS,
                recoveryService.recover(uuid, "203.0.113.7", code, "nuevaClave2").join().result());
    }
}
