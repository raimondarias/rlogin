package com.raimondarias.rlogin.common.security;

import com.raimondarias.rlogin.common.config.RLoginConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a password has to clear before it protects anything. A length rule on
 * its own accepts {@code 123456}, which is the first guess anyone makes.
 */
class PasswordPolicyTest {

    private static PasswordPolicy policyWith(Path dir, String yaml) throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        RLoginConfig config = RLoginConfig.load(dir, "default-config.yml", new ArrayList<>());
        return new PasswordPolicy(config);
    }

    @Test
    @DisplayName("a reasonable password passes")
    void acceptsAReasonablePassword(@TempDir Path dir) throws IOException {
        PasswordPolicy policy = policyWith(dir, "");
        assertEquals(PasswordPolicy.Verdict.OK, policy.check("correct-horse-9", "Notch"));
    }

    @Test
    @DisplayName("the lengths are still enforced")
    void enforcesLength(@TempDir Path dir) throws IOException {
        PasswordPolicy policy = policyWith(dir, "");
        assertEquals(PasswordPolicy.Verdict.TOO_SHORT, policy.check("ab", "Notch"));
        assertEquals(PasswordPolicy.Verdict.TOO_LONG, policy.check("x".repeat(200), "Notch"));
    }

    @Test
    @DisplayName("the passwords everybody guesses first are refused")
    void refusesCommonPasswords(@TempDir Path dir) throws IOException {
        PasswordPolicy policy = policyWith(dir, "");
        assertTrue(policy.commonListSize() > 50, "the bundled list should have loaded");

        assertEquals(PasswordPolicy.Verdict.TOO_COMMON, policy.check("123456", "Notch"));
        assertEquals(PasswordPolicy.Verdict.TOO_COMMON, policy.check("password", "Notch"));
        assertEquals(PasswordPolicy.Verdict.TOO_COMMON, policy.check("minecraft", "Notch"));
        // Case is not a defence: PASSWORD is the same guess as password.
        assertEquals(PasswordPolicy.Verdict.TOO_COMMON, policy.check("PassWord", "Notch"));
    }

    @Test
    @DisplayName("a password that is just the player's own name is refused")
    void refusesTheirOwnName(@TempDir Path dir) throws IOException {
        PasswordPolicy policy = policyWith(dir, "");
        // Names are public on a server, so this is a password everyone can already read.
        assertEquals(PasswordPolicy.Verdict.SAME_AS_NAME, policy.check("Raimondtx", "Raimondtx"));
        assertEquals(PasswordPolicy.Verdict.SAME_AS_NAME, policy.check("raimondtx", "Raimondtx"));
    }

    @Test
    @DisplayName("turning the list off leaves only the length rules")
    void listCanBeTurnedOff(@TempDir Path dir) throws IOException {
        PasswordPolicy policy = policyWith(dir, "security:\n  password:\n    reject-common: false\n");

        assertEquals(0, policy.commonListSize());
        assertEquals(PasswordPolicy.Verdict.OK, policy.check("123456", "Notch"));
        // The name rule is not part of the list, so it still applies.
        assertEquals(PasswordPolicy.Verdict.SAME_AS_NAME, policy.check("Notch", "Notch"));
    }
}
