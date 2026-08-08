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
        // Minecraft trata "Steve" y "steve" como nombres distintos a efectos de este cálculo.
        assertNotEquals(OfflineUuid.of("Steve"), OfflineUuid.of("steve"));
    }

    @Test
    void isOfflineDetectsComputedUuid() {
        UUID computed = OfflineUuid.of("Steve");
        assertTrue(OfflineUuid.isOffline(computed, "Steve"));
    }

    @Test
    void isOfflineRejectsUnrelatedUuid() {
        // Un UUID real de Mojang (aleatorio v4) nunca coincidirá con el cálculo offline determinista.
        UUID random = UUID.randomUUID();
        assertFalse(OfflineUuid.isOffline(random, "Steve"));
    }
}
