package com.raimondarias.rlogin.common.sync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Codec for the {@code rlogin:sync} plugin messaging channel between
 * Velocity and the Paper/Folia backends. Only two message types:
 *
 * <ul>
 *   <li>{@code AUTHENTICATED} (backend → proxy): a player just authenticated
 *   (login/register/2FA) on that backend.</li>
 *   <li>{@code TRUSTED} (proxy → backend): the proxy already considers this
 *   player authenticated (premium auto-login, or because another backend
 *   reported it earlier) — the backend can skip the login prompt entirely.</li>
 * </ul>
 *
 * <p>Purely a byte array, with no dependency on any platform's API, so it
 * can be shared between {@code rlogin-velocity} and {@code rlogin-paper}.</p>
 */
public final class SyncMessage {

    public enum Type {
        AUTHENTICATED,
        TRUSTED
    }

    private final Type type;
    private final UUID uuid;

    public SyncMessage(Type type, UUID uuid) {
        this.type = type;
        this.uuid = uuid;
    }

    public Type type() {
        return type;
    }

    public UUID uuid() {
        return uuid;
    }

    public byte[] encode() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bos)) {
            out.writeUTF(type.name());
            out.writeUTF(uuid.toString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bos.toByteArray();
    }

    public static SyncMessage decode(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            Type type = Type.valueOf(in.readUTF());
            UUID uuid = UUID.fromString(in.readUTF());
            return new SyncMessage(type, uuid);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
