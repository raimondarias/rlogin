package com.raimondarias.rlogin.common.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthMeLegacyHashTest {

    /** sha256(sha256(password) + salt), computed independently of the production code. */
    private static String referenceHash(String password, String salt) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String innerHex = HexFormat.of().formatHex(digest.digest(password.getBytes(StandardCharsets.UTF_8)));
        digest.reset();
        return HexFormat.of().formatHex(digest.digest((innerHex + salt).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void matchesRecognizesAuthMeFormat() {
        assertTrue(AuthMeLegacyHash.matches("$SHA$abc123$deadbeef"));
        assertFalse(AuthMeLegacyHash.matches("$2a$10$abcdefghijklmnopqrstuv"));
        assertFalse(AuthMeLegacyHash.matches(null));
        assertFalse(AuthMeLegacyHash.matches("not-a-hash"));
    }

    @Test
    void verifyAcceptsCorrectPassword() throws Exception {
        String salt = "a1b2c3";
        String password = "hunter2";
        String stored = "$SHA$" + salt + "$" + referenceHash(password, salt);
        assertTrue(AuthMeLegacyHash.verify(password, stored));
    }

    @Test
    void verifyRejectsWrongPassword() throws Exception {
        String salt = "a1b2c3";
        String stored = "$SHA$" + salt + "$" + referenceHash("hunter2", salt);
        assertFalse(AuthMeLegacyHash.verify("wrongpassword", stored));
    }

    @Test
    void verifyRejectsMalformedStoredValue() {
        assertFalse(AuthMeLegacyHash.verify("hunter2", "not-authme-format"));
        assertFalse(AuthMeLegacyHash.verify("hunter2", null));
    }
}
