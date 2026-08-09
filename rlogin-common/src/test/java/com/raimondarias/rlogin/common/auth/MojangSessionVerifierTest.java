package com.raimondarias.rlogin.common.auth;

import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MojangSessionVerifierTest {

    private static KeyPair rsaKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        return generator.generateKeyPair();
    }

    private static SecretKey aesKey() throws NoSuchAlgorithmException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        return generator.generateKey();
    }

    @Test
    void isDeterministicForTheSameInputs() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        SecretKey secret = aesKey();

        String hash1 = MojangSessionVerifier.serverIdHash("", secret, keyPair.getPublic());
        String hash2 = MojangSessionVerifier.serverIdHash("", secret, keyPair.getPublic());

        assertEquals(hash1, hash2);
    }

    @Test
    void isAValidHexBigInteger() throws Exception {
        String hash = MojangSessionVerifier.serverIdHash("", aesKey(), rsaKeyPair().getPublic());

        // Must parse back as radix-16 without throwing — Mojang's format allows (and often
        // has) a leading '-', unlike a plain unsigned hex digest.
        new java.math.BigInteger(hash, 16);
        assertTrue(hash.matches("-?[0-9a-f]+"));
    }

    @Test
    void changesWhenTheSharedSecretChanges() throws Exception {
        KeyPair keyPair = rsaKeyPair();

        String hash1 = MojangSessionVerifier.serverIdHash("", aesKey(), keyPair.getPublic());
        String hash2 = MojangSessionVerifier.serverIdHash("", aesKey(), keyPair.getPublic());

        assertNotEquals(hash1, hash2); // astronomically unlikely to collide by chance
    }

    @Test
    void changesWhenTheServerIdChanges() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        SecretKey secret = aesKey();

        String hash1 = MojangSessionVerifier.serverIdHash("", secret, keyPair.getPublic());
        String hash2 = MojangSessionVerifier.serverIdHash("something-else", secret, keyPair.getPublic());

        assertNotEquals(hash1, hash2);
    }

    /** Verbatim shape of a real 200, pretty-printed spacing and all — that's how Mojang sends it. */
    private static final String REAL_RESPONSE = """
            {
              "id" : "4566e69fc90748ee8d71d7ba5aa00d20",
              "name" : "Thinkofdeath",
              "properties" : [ {
                "name" : "textures",
                "value" : "eyJ0aW1lc3RhbXAiOjE0NTk0NzMzNDU4MDF9",
                "signature" : "QH+1rlQJYk8tW+8WlSJnzxZZUL5RIkeOO33dq84cgNo="
              } ]
            }""";

    @Test
    void readsTheRealUuidNameAndSignedSkin() {
        MojangSessionVerifier.VerifiedProfile profile =
                MojangSessionVerifier.parseBody(REAL_RESPONSE).orElseThrow();

        assertEquals(java.util.UUID.fromString("4566e69f-c907-48ee-8d71-d7ba5aa00d20"), profile.uuid());
        assertEquals("Thinkofdeath", profile.name());
        assertEquals(1, profile.properties().size());

        ProfileProperty textures = profile.properties().get(0);
        assertEquals("textures", textures.name());
        assertEquals("eyJ0aW1lc3RhbXAiOjE0NTk0NzMzNDU4MDF9", textures.value());
        assertEquals("QH+1rlQJYk8tW+8WlSJnzxZZUL5RIkeOO33dq84cgNo=", textures.signature());
    }

    @Test
    void doesNotMistakeAPropertyNameForTheProfileName() {
        // "name" appears three times in a real response; only the profile's own one counts.
        assertEquals("Thinkofdeath", MojangSessionVerifier.parseBody(REAL_RESPONSE).orElseThrow().name());
    }

    @Test
    void handlesAProfileWithNoProperties() {
        String body = "{\"id\":\"4566e69fc90748ee8d71d7ba5aa00d20\",\"name\":\"Thinkofdeath\",\"properties\":[]}";

        MojangSessionVerifier.VerifiedProfile profile = MojangSessionVerifier.parseBody(body).orElseThrow();

        assertEquals("Thinkofdeath", profile.name());
        assertTrue(profile.properties().isEmpty());
    }

    @Test
    void keepsAnUnsignedPropertyWithANullSignature() {
        String body = "{\"id\":\"4566e69fc90748ee8d71d7ba5aa00d20\",\"name\":\"X\","
                + "\"properties\":[{\"name\":\"textures\",\"value\":\"abc\"}]}";

        ProfileProperty property = MojangSessionVerifier.parseBody(body).orElseThrow().properties().get(0);

        assertEquals("abc", property.value());
        assertNull(property.signature());
    }

    @Test
    void rejectsABodyWithoutAUuid() {
        assertTrue(MojangSessionVerifier.parseBody("{\"error\":\"nope\"}").isEmpty());
        assertTrue(MojangSessionVerifier.parseBody("").isEmpty());
    }
}
