package com.raimondarias.rlogin.common.sync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Codec del canal de plugin messaging {@code rlogin:sync} entre Velocity y
 * los backends Paper/Folia. Solo dos tipos de mensaje:
 *
 * <ul>
 *   <li>{@code AUTHENTICATED} (backend → proxy): un jugador acaba de
 *   autenticarse (login/registro/2FA) en ese backend.</li>
 *   <li>{@code TRUSTED} (proxy → backend): el proxy ya considera a este
 *   jugador autenticado (premium auto-login, o porque otro backend avisó
 *   antes) — el backend puede saltarse el login sin volver a preguntarle.</li>
 * </ul>
 *
 * <p>Puramente un array de bytes, sin dependencias de la API de ninguna
 * plataforma, para poder compartirlo entre {@code rlogin-velocity} y
 * {@code rlogin-paper}.</p>
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
