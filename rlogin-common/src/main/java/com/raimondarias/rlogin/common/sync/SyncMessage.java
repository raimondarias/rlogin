package com.raimondarias.rlogin.common.sync;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
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
 * <p>Every message is signed with HMAC-SHA256 under a shared secret
 * ({@code sync.secret}, configured identically on the proxy and on every
 * backend). This is not optional decoration: plugin messages travel over the
 * player's own connection, and Bukkit's API hands every incoming message to
 * the listener with the <em>player</em> as the sender — it cannot tell a
 * message forwarded by the proxy from one forged by the client. Without a
 * signature, any player could send a {@code TRUSTED} message for their own
 * UUID and be waved straight in. The signature is what makes that
 * impossible: the client does not know the secret.</p>
 *
 * <p>While {@code sync.secret} is empty on either side, the channel is
 * simply not trusted: recipients reject every message, so the network
 * degrades to "ask for /login again on every backend" — the safe state, not
 * a broken one.</p>
 *
 * <p>Purely a byte array, with no dependency on any platform's API, so it
 * can be shared between {@code rlogin-velocity} and {@code rlogin-paper}.</p>
 */
public final class SyncMessage {

    public enum Type {
        AUTHENTICATED,
        TRUSTED
    }

    private static final String HMAC_ALGO = "HmacSHA256";
    /** HMAC-SHA256 digests are 32 bytes. */
    private static final int SIGNATURE_LENGTH = 32;

    private final Type type;
    private final UUID uuid;
    private final boolean firstServer;

    public SyncMessage(Type type, UUID uuid) {
        this(type, uuid, true);
    }

    public SyncMessage(Type type, UUID uuid, boolean firstServer) {
        this.type = type;
        this.uuid = uuid;
        this.firstServer = firstServer;
    }

    public Type type() {
        return type;
    }

    public UUID uuid() {
        return uuid;
    }

    /**
     * Whether this is the player's first backend since they connected to the
     * proxy, as opposed to a server switch.
     *
     * <p>Only the proxy can tell the difference: every backend sees an
     * ordinary join and would greet an already-greeted player all over again.
     * Defaults to {@code true} when the sender didn't say, so an older proxy
     * paired with a newer backend behaves exactly as it did before rather
     * than falling silent.</p>
     */
    public boolean firstServer() {
        return firstServer;
    }

    /** Encodes the message with its HMAC-SHA256 signature under {@code secret}. */
    public byte[] encode(String secret) {
        byte[] body = body();
        byte[] signature = hmac(secret, body);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bos)) {
            out.writeInt(body.length);
            out.write(body);
            out.write(signature);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bos.toByteArray();
    }

    /**
     * Decodes and verifies a message's signature.
     *
     * @return the message, or empty when the bytes are malformed, the
     *         signature is missing/wrong, or {@code secret} is blank — in
     *         every one of those cases the caller must not act on it.
     */
    public static Optional<SyncMessage> decode(byte[] data, String secret) {
        if (data == null || secret == null || secret.isBlank()) {
            return Optional.empty();
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int bodyLength = in.readInt();
            if (bodyLength < 0 || bodyLength > data.length - Integer.BYTES - SIGNATURE_LENGTH) {
                return Optional.empty();
            }
            byte[] body = new byte[bodyLength];
            in.readFully(body);
            byte[] signature = new byte[SIGNATURE_LENGTH];
            in.readFully(signature);
            if (!MessageDigest.isEqual(signature, hmac(secret, body))) {
                return Optional.empty();
            }
            try (DataInputStream bodyIn = new DataInputStream(new ByteArrayInputStream(body))) {
                Type type = Type.valueOf(bodyIn.readUTF());
                UUID uuid = UUID.fromString(bodyIn.readUTF());
                // Absent from messages written before this field existed.
                boolean firstServer = bodyIn.available() <= 0 || bodyIn.readBoolean();
                return Optional.of(new SyncMessage(type, uuid, firstServer));
            }
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] body() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bos)) {
            out.writeUTF(type.name());
            out.writeUTF(uuid.toString());
            out.writeBoolean(firstServer);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bos.toByteArray();
    }

    private static byte[] hmac(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            return mac.doFinal(body);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable on this JVM", e);
        }
    }
}
