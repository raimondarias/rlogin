package com.raimondarias.rlogin.common.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineUuidTest {

    @Test
    void sameUsernameAlwaysProducesSameUuid() {
        assertEquals(OfflineUuid.of("Steve"), OfflineUuid.of("Steve"));
    }

    @Test
    void differentUsernamesProduceDifferentUuids() {
        assertNotEquals(OfflineUuid.of("Steve"), OfflineUuid.of("Alex"));
    }

    @Test
    void usernameIsCaseSensitive() {
        // Minecraft treats "Steve" and "steve" as distinct names for this computation.
        assertNotEquals(OfflineUuid.of("Steve"), OfflineUuid.of("steve"));
    }

    @Test
    void isOfflineDetectsComputedUuid() {
        UUID computed = OfflineUuid.of("Steve");
        assertTrue(OfflineUuid.isOffline(computed, "Steve"));
    }

    @Test
    void isOfflineRejectsUnrelatedUuid() {
        // A real Mojang UUID (random v4) will never match the deterministic offline computation.
        UUID random = UUID.randomUUID();
        assertFalse(OfflineUuid.isOffline(random, "Steve"));
    }
}
