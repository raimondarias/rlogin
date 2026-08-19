package com.raimondarias.rlogin.common.sync;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncMessageTest {

    @Test
    void trustedMessageRoundTrips() {
        UUID uuid = UUID.randomUUID();
        SyncMessage original = new SyncMessage(SyncMessage.Type.TRUSTED, uuid);

        Optional<SyncMessage> decoded = SyncMessage.decode(original.encode("mi-secreto"), "mi-secreto");

        assertTrue(decoded.isPresent());
        assertEquals(SyncMessage.Type.TRUSTED, decoded.get().type());
        assertEquals(uuid, decoded.get().uuid());
    }

    @Test
    void authenticatedMessageRoundTrips() {
        UUID uuid = UUID.randomUUID();
        SyncMessage original = new SyncMessage(SyncMessage.Type.AUTHENTICATED, uuid);

        Optional<SyncMessage> decoded = SyncMessage.decode(original.encode("mi-secreto"), "mi-secreto");

        assertTrue(decoded.isPresent());
        assertEquals(SyncMessage.Type.AUTHENTICATED, decoded.get().type());
        assertEquals(uuid, decoded.get().uuid());
    }

    @Test
    void firstServerFlagSurvivesTheRoundTrip() {
        UUID uuid = UUID.randomUUID();
        SyncMessage original = new SyncMessage(SyncMessage.Type.TRUSTED, uuid, false);

        Optional<SyncMessage> decoded = SyncMessage.decode(original.encode("secreto"), "secreto");

        assertTrue(decoded.isPresent());
        assertFalse(decoded.get().firstServer());
    }

    @Test
    void wrongSecretIsRejected() {
        byte[] encoded = new SyncMessage(SyncMessage.Type.AUTHENTICATED, UUID.randomUUID()).encode("secreto-a");

        assertTrue(SyncMessage.decode(encoded, "secreto-b").isEmpty());
    }

    @Test
    void blankSecretIsRejected() {
        byte[] encoded = new SyncMessage(SyncMessage.Type.AUTHENTICATED, UUID.randomUUID()).encode("secreto");

        assertTrue(SyncMessage.decode(encoded, "").isEmpty());
        assertTrue(SyncMessage.decode(encoded, null).isEmpty());
    }

    @Test
    void tamperedBodyIsRejected() {
        UUID uuid = UUID.randomUUID();
        byte[] encoded = new SyncMessage(SyncMessage.Type.TRUSTED, uuid).encode("secreto");
        // Flip one bit of the first body byte: the signature must no longer match.
        encoded[4] ^= 0x01;

        assertTrue(SyncMessage.decode(encoded, "secreto").isEmpty());
    }

    @Test
    void garbageIsRejected() {
        assertTrue(SyncMessage.decode(new byte[]{1, 2, 3}, "secreto").isEmpty());
        assertTrue(SyncMessage.decode(null, "secreto").isEmpty());
    }
}
