package com.raimondarias.rlogin.common.auth;

import com.raimondarias.rlogin.common.config.RLoginConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consulta si un nombre pertenece a una cuenta premium (Java original) vía
 * la API pública de Mojang, con caché en memoria para no golpearla en cada
 * conexión.
 *
 * <p>Es el fallback de {@code rlogin-velocity} cuando no hay Modern
 * Forwarding disponible, y el único mecanismo de detección en
 * {@code rlogin-paper} standalone.</p>
 */
public final class PremiumChecker {

    public enum Status {
        PREMIUM, NOT_PREMIUM, ERROR
    }

    public record MojangProfile(UUID uuid, String name) {
    }

    public record PremiumLookup(Status status, MojangProfile profile) {
        public boolean isPremium() {
            return status == Status.PREMIUM;
        }

        static PremiumLookup premium(MojangProfile profile) {
            return new PremiumLookup(Status.PREMIUM, profile);
        }

        static PremiumLookup notPremium() {
            return new PremiumLookup(Status.NOT_PREMIUM, null);
        }

        static PremiumLookup error() {
            return new PremiumLookup(Status.ERROR, null);
        }
    }

    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
    private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private final RLoginConfig config;
    private final HttpClient http;
    private final ExecutorService executor;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(PremiumLookup result, Instant expiresAt) {
    }

    public PremiumChecker(RLoginConfig config) {
        this.config = config;
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "rlogin-mojang-api");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.premiumApiTimeoutMs()))
                .executor(executor)
                .build();
    }

    public CompletableFuture<PremiumLookup> lookup(String username) {
        String key = username.toLowerCase(Locale.ROOT);
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return CompletableFuture.completedFuture(cached.result());
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + urlEncode(username)))
                .timeout(Duration.ofMillis(config.premiumApiTimeoutMs()))
                .GET()
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(this::parseResponse)
                .exceptionally(ex -> PremiumLookup.error())
                .thenApply(result -> {
                    // No cacheamos errores: la próxima conexión debe reintentar.
                    if (result.status() != Status.ERROR) {
                        cache.put(key, new CacheEntry(result, Instant.now().plusSeconds(config.premiumCacheTtlMinutes() * 60L)));
                    }
                    return result;
                });
    }

    /** Marca manualmente en caché que un UUID ya conocido es premium (evita ir a la API). */
    public void rememberPremium(String username, UUID uuid) {
        cache.put(username.toLowerCase(Locale.ROOT),
                new CacheEntry(PremiumLookup.premium(new MojangProfile(uuid, username)),
                        Instant.now().plusSeconds(config.premiumCacheTtlMinutes() * 60L)));
    }

    private PremiumLookup parseResponse(HttpResponse<String> response) {
        if (response.statusCode() == 200 && !response.body().isBlank()) {
            Matcher idMatcher = ID_PATTERN.matcher(response.body());
            Matcher nameMatcher = NAME_PATTERN.matcher(response.body());
            if (idMatcher.find() && nameMatcher.find()) {
                UUID uuid = dashUuid(idMatcher.group(1));
                return PremiumLookup.premium(new MojangProfile(uuid, nameMatcher.group(1)));
            }
        }
        // 204/404 -> Mojang no conoce ese nombre como cuenta premium.
        return PremiumLookup.notPremium();
    }

    private static UUID dashUuid(String raw32) {
        String dashed = raw32.substring(0, 8) + "-" + raw32.substring(8, 12) + "-"
                + raw32.substring(12, 16) + "-" + raw32.substring(16, 20) + "-" + raw32.substring(20);
        return UUID.fromString(dashed);
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public void shutdown() {
        executor.shutdown();
    }
}
