package com.raimondarias.rlogin.common.sync;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncMessageTest {

    @Test
    void trustedMessageRoundTrips() {
        UUID uuid = UUID.randomUUID();
        SyncMessage original = new SyncMessage(SyncMessage.Type.TRUSTED, uuid);
        SyncMessage decoded = SyncMessage.decode(original.encode());
        assertEquals(SyncMessage.Type.TRUSTED, decoded.type());
        assertEquals(uuid, decoded.uuid());
    }

    @Test
    void authenticatedMessageRoundTrips() {
        UUID uuid = UUID.randomUUID();
        SyncMessage original = new SyncMessage(SyncMessage.Type.AUTHENTICATED, uuid);
        SyncMessage decoded = SyncMessage.decode(original.encode());
        assertEquals(SyncMessage.Type.AUTHENTICATED, decoded.type());
        assertEquals(uuid, decoded.uuid());
    }
}
