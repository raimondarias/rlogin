package com.raimondarias.rlogin.paper.hybrid;

import com.raimondarias.rlogin.common.auth.ProfileProperty;
import io.netty.channel.ChannelPipeline;

import javax.crypto.SecretKey;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The piece that lets standalone hybrid-auth hand a verified premium player
 * their <b>real</b> Mojang identity — real UUID and real skin — on a
 * proxy-less {@code online-mode: false} server, instead of the offline
 * stand-in derived from their name.
 *
 * <p>It works because of two fields Spigot (and therefore Paper and every
 * fork of it) adds to the server's netty connection class specifically so
 * that a front proxy can forward an already-authenticated identity:</p>
 *
 * <pre>
 *   public UUID spoofedUUID;
 *   public com.mojang.authlib.properties.Property[] spoofedProfile;
 * </pre>
 *
 * <p>The server's login handler reads them at exactly the moment it would
 * otherwise fall back to an offline profile — its {@code createOfflineProfile}
 * is literally {@code spoofedUUID != null ? spoofedUUID :
 * UUIDUtil.createOfflinePlayerUUID(name)}, then copies {@code spoofedProfile}
 * into the resulting {@code GameProfile}'s properties. So filling these in
 * before the held {@code LOGIN_START} is re-injected produces the identical
 * result to a BungeeCord/Velocity-forwarded login: {@code AsyncPlayerPreLoginEvent},
 * {@code Player#getUniqueId()}, the skin, and everything downstream see the
 * genuine account. This is the same mechanism proxy forwarding itself uses —
 * rLogin just fills it from a Mojang {@code hasJoined} response it verified
 * itself rather than from a proxy's handshake.</p>
 *
 * <p><b>Why reflection is safe here specifically:</b> these are members
 * Spigot adds, not Mojang ones, so unlike the rest of the server internals
 * they are never obfuscated and their names have been stable for as long as
 * proxy forwarding has existed. Name lookup is still backed by a
 * lookup-by-type fallback, and every method fails soft: if anything at all
 * can't be resolved, the caller is told so and falls back to the
 * offline-UUID path, which still logs the player in — they just keep the
 * offline UUID and skin.</p>
 */
public final class NmsConnectionAccess {

    /** Netty handler name of the server's own connection object; set by the server itself. */
    private static final String PACKET_HANDLER = "packet_handler";
    private static final String AUTHLIB_PROPERTY = "com.mojang.authlib.properties.Property";

    private record Members(Field spoofedUuid, Field spoofedProfile, Method setEncryptionKey,
                           Class<?> propertyType, Constructor<?> propertyConstructor) {
    }

    private final Logger logger;
    private volatile Members members;
    private volatile boolean resolutionFailed;

    public NmsConnectionAccess(Logger logger) {
        this.logger = logger;
    }

    /**
     * Turns on AES/CFB8 encryption by handing the shared secret to the
     * server's own connection object, so the cipher handlers land in the
     * pipeline exactly where and how the server puts them for a real
     * online-mode login (including the native cipher it would normally use).
     *
     * @return false if the server's method couldn't be resolved or threw —
     *         the caller should then install the ciphers itself.
     */
    public boolean enableEncryption(ChannelPipeline pipeline, SecretKey sharedSecret) {
        Object connection = connectionOf(pipeline);
        Members resolved = connection == null ? null : membersOf(connection);
        if (resolved == null || resolved.setEncryptionKey() == null) {
            return false;
        }
        try {
            resolved.setEncryptionKey().invoke(connection, sharedSecret);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.log(Level.WARNING, "[hybrid-auth] The server rejected our encryption key, "
                    + "falling back to installing the ciphers directly.", e);
            return false;
        }
    }

    /**
     * Stages the UUID this connection should join with (and, when
     * {@code properties} isn't empty, the real signed skin) for the server
     * to pick up while it builds the profile. Must be called <b>before</b>
     * the held {@code LOGIN_START} is re-injected — after that the profile
     * already exists and these fields are never read again.
     *
     * @param uuid the identity to join as, or null to leave the server's own
     *             offline UUID alone and only apply the skin
     * @return false if the fields couldn't be resolved or written; the
     *         player then keeps the offline UUID, nothing else breaks.
     */
    public boolean applyIdentity(ChannelPipeline pipeline, UUID uuid, List<ProfileProperty> properties) {
        Object connection = connectionOf(pipeline);
        Members resolved = connection == null ? null : membersOf(connection);
        if (resolved == null || resolved.spoofedUuid() == null) {
            return false;
        }
        if (uuid != null) {
            try {
                resolved.spoofedUuid().set(connection, uuid);
            } catch (ReflectiveOperationException | RuntimeException e) {
                logger.log(Level.WARNING, "[hybrid-auth] Could not apply the intended UUID; "
                        + "this player keeps their offline one.", e);
                return false;
            }
        }
        applySkin(connection, resolved, properties);
        return true;
    }

    /** Best-effort and deliberately separate: a missing skin must never cost the player their real UUID. */
    private void applySkin(Object connection, Members resolved, List<ProfileProperty> properties) {
        if (properties.isEmpty() || resolved.spoofedProfile() == null || resolved.propertyConstructor() == null) {
            return;
        }
        try {
            Object array = Array.newInstance(resolved.propertyType(), properties.size());
            for (int i = 0; i < properties.size(); i++) {
                ProfileProperty property = properties.get(i);
                Array.set(array, i, resolved.propertyConstructor()
                        .newInstance(property.name(), property.value(), property.signature()));
            }
            resolved.spoofedProfile().set(connection, array);
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.log(Level.WARNING, "[hybrid-auth] Could not forward the real premium skin "
                    + "(the real UUID was still applied).", e);
        }
    }

    private Object connectionOf(ChannelPipeline pipeline) {
        if (pipeline == null) {
            return null;
        }
        try {
            return pipeline.get(PACKET_HANDLER);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Resolved once per server run, off the first connection that gets this far. */
    private Members membersOf(Object connection) {
        Members cached = members;
        if (cached != null || resolutionFailed) {
            return cached;
        }
        synchronized (this) {
            if (members == null && !resolutionFailed) {
                members = resolve(connection);
                resolutionFailed = members == null;
            }
            return members;
        }
    }

    private Members resolve(Object connection) {
        Class<?> type = connection.getClass();
        try {
            Class<?> propertyType = Class.forName(AUTHLIB_PROPERTY, false, type.getClassLoader());
            Field spoofedUuid = publicField(type, "spoofedUUID", UUID.class);
            if (spoofedUuid == null) {
                logger.warning("[hybrid-auth] This server build has no 'spoofedUUID' on " + type.getName()
                        + " - premium players will auto-login but keep their offline UUID. "
                        + "Set premium.standalone-premium-uuid: false to silence this.");
                return null;
            }
            Members resolved = new Members(spoofedUuid,
                    publicField(type, "spoofedProfile", Array.newInstance(propertyType, 0).getClass()),
                    voidMethodTaking(type, "setEncryptionKey", SecretKey.class),
                    propertyType,
                    propertyConstructor(propertyType));
            logger.info("[hybrid-auth] Real premium UUIDs available on this server build ("
                    + type.getName() + ").");
            return resolved;
        } catch (ClassNotFoundException | RuntimeException e) {
            logger.log(Level.WARNING, "[hybrid-auth] Could not read this server's connection internals; "
                    + "premium players will auto-login but keep their offline UUID.", e);
            return null;
        }
    }

    /**
     * By name first (these are Spigot's own, never-obfuscated names), then
     * by type — which stays unambiguous only while exactly one public
     * instance field has it, otherwise this gives up rather than guess.
     */
    private static Field publicField(Class<?> type, String name, Class<?> fieldType) {
        try {
            Field byName = type.getField(name);
            if (fieldType.isAssignableFrom(byName.getType()) && !Modifier.isStatic(byName.getModifiers())) {
                return byName;
            }
        } catch (NoSuchFieldException ignored) {
            // Renamed or absent on this fork — fall through to the by-type search.
        }
        Field found = null;
        for (Field candidate : type.getFields()) {
            if (Modifier.isStatic(candidate.getModifiers()) || !fieldType.equals(candidate.getType())) {
                continue;
            }
            if (found != null) {
                return null; // Ambiguous: two equally plausible fields, so neither is a safe bet.
            }
            found = candidate;
        }
        return found;
    }

    /** Same idea, for {@code setEncryptionKey}: by name, then by its (unique) signature. */
    private static Method voidMethodTaking(Class<?> type, String name, Class<?> parameterType) {
        Method found = null;
        for (Method candidate : type.getMethods()) {
            if (candidate.getReturnType() != void.class
                    || candidate.getParameterCount() != 1
                    || candidate.getParameterTypes()[0] != parameterType) {
                continue;
            }
            if (candidate.getName().equals(name)) {
                return candidate;
            }
            if (found != null) {
                return null;
            }
            found = candidate;
        }
        return found;
    }

    private static Constructor<?> propertyConstructor(Class<?> propertyType) {
        try {
            return propertyType.getConstructor(String.class, String.class, String.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
