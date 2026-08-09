package com.raimondarias.rlogin.common.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidTypeTest {

    @Test
    void readsTheThreeModes() {
        assertEquals(UuidType.REAL, UuidType.parse("real"));
        assertEquals(UuidType.CRACKED, UuidType.parse("cracked"));
        assertEquals(UuidType.RANDOM, UuidType.parse("random"));
    }

    @Test
    void isForgivingAboutHowItsWritten() {
        assertEquals(UuidType.REAL, UuidType.parse("REAL"));
        assertEquals(UuidType.RANDOM, UuidType.parse("  Random "));
    }

    @Test
    void acceptsNLoginsNameForCracked() {
        // Admins migrating from nLogin will write what its own docs call it.
        assertEquals(UuidType.CRACKED, UuidType.parse("offline"));
        assertEquals(UuidType.CRACKED, UuidType.parse("OFFLINE"));
    }

    @Test
    void fallsBackToRealRatherThanRefusingToStart() {
        assertEquals(UuidType.REAL, UuidType.parse("nonsense"));
        assertEquals(UuidType.REAL, UuidType.parse(""));
        assertEquals(UuidType.REAL, UuidType.parse(null));
    }

    @Test
    void knowsWhenItHasToOverrideTheServersOwnUuid() {
        assertTrue(UuidType.REAL.overridesServerUuid());
        assertTrue(UuidType.RANDOM.overridesServerUuid());
        // Cracked IS the server's own behaviour, so there is nothing to override.
        assertFalse(UuidType.CRACKED.overridesServerUuid());
    }
}
