package com.raimondarias.rlogin.common.auth;

import com.raimondarias.rlogin.common.config.RLoginConfig;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies that a connecting client genuinely owns the premium account it
 * claims to be, via Mojang's session server — the actual cryptographic
 * proof step, distinct from {@link PremiumChecker} (which only resolves a
 * username to a UUID; nothing stops anyone from typing that same name on a
 * cracked connection, so it alone is never proof of ownership).
 *
 * <p>Used exclusively when {@code rlogin-paper} has to verify premium
 * accounts itself, on a standalone server with nothing in front of it:
 * after a plugin-driven encryption handshake, the resulting
 * shared secret + server public key + a freshly generated server ID are
 * hashed exactly the way the vanilla client hashes them ({@link
 * #serverIdHash}), and handed to Mojang's {@code hasJoined} endpoint. A 200
 * response proves the connecting client holds the real account's session;
 * anything else (204, timeout) means it does not — treat it as not
 * premium, never as an error to ignore.</p>
 *
 * <p>The 200 body is the authoritative copy of that account's identity —
 * its real UUID <em>and</em> its signed {@code textures} property — which
 * is why {@link #hasJoined} returns the whole {@link VerifiedProfile}
 * rather than just a boolean: it's the only trustworthy source for both,
 * and both are needed to hand the player their real premium identity
 * instead of an offline-mode stand-in.</p>
 */
public final class MojangSessionVerifier {

    /**
     * A premium identity Mojang has just vouched for, for this one
     * connection. Everything here comes straight from the {@code hasJoined}
     * response — never from anything the client said about itself.
     */
    public record VerifiedProfile(UUID uuid, String name, List<ProfileProperty> properties) {
    }

    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
    private static final Pattern PROPERTIES_PATTERN = Pattern.compile("\"properties\"\\s*:\\s*\\[(.*)]", Pattern.DOTALL);
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\{([^{}]*)}", Pattern.DOTALL);
    private static final Pattern PROP_NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern PROP_VALUE_PATTERN = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern PROP_SIGNATURE_PATTERN = Pattern.compile("\"signature\"\\s*:\\s*\"([^\"]*)\"");

    private final RLoginConfig config;
    private final HttpClient http;
    private final ExecutorService executor;

    public MojangSessionVerifier(RLoginConfig config) {
        this.config = config;
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "rlogin-mojang-session");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.premiumApiTimeoutMs()))
                .executor(executor)
                .build();
    }

    /**
     * The exact hash the vanilla client and Mojang's session server agree
     * on to identify one login attempt: SHA-1 of (server ID + shared secret
     * + server's public key), formatted as a signed hex string via {@code
     * new BigInteger(bytes).toString(16)} — Mojang's own long-standing
     * quirk, NOT a plain unsigned hex digest (a leading {@code -} is normal
     * and expected roughly half the time).
     */
    public static String serverIdHash(String serverId, SecretKey sharedSecret, PublicKey serverPublicKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(serverId.getBytes(StandardCharsets.ISO_8859_1));
            digest.update(sharedSecret.getEncoded());
            digest.update(serverPublicKey.getEncoded());
            return new BigInteger(digest.digest()).toString(16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable on this JVM", e);
        }
    }

    /** Empty if Mojang says this session isn't valid (204), the request failed, or timed out. */
    public CompletableFuture<Optional<VerifiedProfile>> hasJoined(String username, String serverIdHash) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/hasJoined?username="
                        + urlEncode(username) + "&serverId=" + serverIdHash))
                .timeout(Duration.ofMillis(config.premiumApiTimeoutMs()))
                .GET()
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(this::parse)
                .exceptionally(e -> Optional.empty());
    }

    private Optional<VerifiedProfile> parse(HttpResponse<String> response) {
        if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
            return Optional.empty();
        }
        return parseBody(response.body());
    }

    /** Package-private for the tests; {@code body} is a {@code hasJoined} 200 response. */
    static Optional<VerifiedProfile> parseBody(String body) {
        Matcher id = ID_PATTERN.matcher(body);
        if (!id.find()) {
            return Optional.empty();
        }
        Matcher properties = PROPERTIES_PATTERN.matcher(body);
        boolean hasProperties = properties.find();

        // "name" appears both at the top level and inside every property, so the profile's
        // own name is only looked for in what comes before the properties array.
        String head = hasProperties ? body.substring(0, properties.start()) : body;
        Matcher name = PROP_NAME_PATTERN.matcher(head);

        return Optional.of(new VerifiedProfile(PremiumChecker.dashUuid(id.group(1)),
                name.find() ? name.group(1) : null,
                hasProperties ? parseProperties(properties.group(1)) : List.of()));
    }

    private static List<ProfileProperty> parseProperties(String arrayBody) {
        List<ProfileProperty> parsed = new ArrayList<>();
        Matcher entries = PROPERTY_PATTERN.matcher(arrayBody);
        while (entries.find()) {
            String entry = entries.group(1);
            Matcher name = PROP_NAME_PATTERN.matcher(entry);
            Matcher value = PROP_VALUE_PATTERN.matcher(entry);
            if (!name.find() || !value.find()) {
                continue; // Not a property object we understand — skip rather than guess.
            }
            Matcher signature = PROP_SIGNATURE_PATTERN.matcher(entry);
            parsed.add(new ProfileProperty(name.group(1), value.group(1),
                    signature.find() ? signature.group(1) : null));
        }
        return List.copyOf(parsed);
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public void shutdown() {
        executor.shutdown();
    }
}
