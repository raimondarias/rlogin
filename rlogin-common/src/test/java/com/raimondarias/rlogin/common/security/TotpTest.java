package com.raimondarias.rlogin.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpTest {

    @Test
    void codeIsAlways6Digits() {
        String secret = Totp.generateSecret();
        String code = Totp.codeAtTime(secret, 1_700_000_000L);
        assertEquals(6, code.length());
        assertTrue(code.chars().allMatch(Character::isDigit));
    }

    @Test
    void sameSecretAndTimeStepAlwaysProducesSameCode() {
        String secret = Totp.generateSecret();
        String code1 = Totp.codeAtTime(secret, 1_700_000_000L);
        String code2 = Totp.codeAtTime(secret, 1_700_000_000L);
        assertEquals(code1, code2);
    }

    @Test
    void differentTimeStepsUsuallyProduceDifferentCodes() {
        String secret = Totp.generateSecret();
        // Dos pasos de 30s separados por una hora: virtualmente imposible que coincidan por azar.
        String codeA = Totp.codeAtTime(secret, 1_700_000_000L);
        String codeB = Totp.codeAtTime(secret, 1_700_003_600L);
        assertFalse(codeA.equals(codeB));
    }

    @Test
    void withinSameTimeStepCodeDoesNotChange() {
        String secret = Totp.generateSecret();
        long baseStep = (1_700_000_000L / 30) * 30;
        String codeStart = Totp.codeAtTime(secret, baseStep);
        String codeEnd = Totp.codeAtTime(secret, baseStep + 29);
        assertEquals(codeStart, codeEnd);
    }

    @Test
    void verifyAcceptsCurrentCode() {
        String secret = Totp.generateSecret();
        String code = Totp.currentCode(secret);
        assertTrue(Totp.verify(secret, code));
    }

    @Test
    void verifyRejectsWrongCode() {
        String secret = Totp.generateSecret();
        assertFalse(Totp.verify(secret, "000000".equals(Totp.currentCode(secret)) ? "111111" : "000000"));
    }

    @Test
    void verifyRejectsMalformedInput() {
        String secret = Totp.generateSecret();
        assertFalse(Totp.verify(secret, null));
        assertFalse(Totp.verify(secret, "12a456"));
        assertFalse(Totp.verify(secret, "12345"));
        assertFalse(Totp.verify(secret, "1234567"));
    }

    @Test
    void otpAuthUriContainsExpectedParts() {
        String secret = Totp.generateSecret();
        String uri = Totp.buildOtpAuthUri("rLogin", "Steve", secret);
        assertTrue(uri.startsWith("otpauth://totp/"));
        assertTrue(uri.contains("secret=" + secret));
        assertTrue(uri.contains("issuer=rLogin"));
        assertTrue(uri.contains("digits=6"));
        assertTrue(uri.contains("period=30"));
    }
}
