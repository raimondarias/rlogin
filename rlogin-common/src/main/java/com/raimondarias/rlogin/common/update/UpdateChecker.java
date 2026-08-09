package com.raimondarias.rlogin.common.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks GitHub for a newer rLogin release and reports it once, at startup.
 *
 * <p>Reads {@code /releases/latest}, which GitHub only ever points at a
 * published, non-prerelease, non-draft release — so a tag pushed mid-work
 * never tells anyone their server is out of date.</p>
 *
 * <p>Everything about this is best-effort by design: it runs off the main
 * thread, has a short timeout, and any failure at all (no network, rate
 * limit, GitHub down, a tag that doesn't parse) resolves to "nothing to
 * report" rather than a stack trace. An update check must never be the
 * reason a server has a bad startup.</p>
 */
public final class UpdateChecker {

    private static final String LATEST_RELEASE =
            "https://api.github.com/repos/raimondarias/rlogin/releases/latest";
    private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** The newer version's name, plus where to get it. */
    public record Update(String latestVersion, String currentVersion, String url) {
    }

    private final String currentVersion;

    public UpdateChecker(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    /** Empty when already up to date, ahead of the latest release, or the check simply didn't work. */
    public CompletableFuture<Optional<Update>> check() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LATEST_RELEASE))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "rLogin/" + currentVersion)
                .GET()
                .build();

        // Closed once the answer is in: an HttpClient owns a selector thread and a
        // connection pool, and this one is used for exactly one request at startup.
        HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(this::parse)
                .exceptionally(e -> Optional.<Update>empty())
                .whenComplete((result, error) -> http.close());
    }

    private Optional<Update> parse(HttpResponse<String> response) {
        if (response.statusCode() != 200 || response.body() == null) {
            return Optional.empty();
        }
        Matcher tag = TAG_PATTERN.matcher(response.body());
        if (!tag.find()) {
            return Optional.empty();
        }
        String latest = tag.group(1);
        return isNewerThanCurrent(latest)
                ? Optional.of(new Update(latest, currentVersion, "https://github.com/raimondarias/rlogin/releases"))
                : Optional.empty();
    }

    private boolean isNewerThanCurrent(String latest) {
        return compare(strip(latest), strip(currentVersion)) > 0;
    }

    /** Tags are conventionally written {@code v1.2.3}; the plugin's own version isn't. */
    private static String strip(String version) {
        String trimmed = version.trim();
        return trimmed.startsWith("v") || trimmed.startsWith("V") ? trimmed.substring(1) : trimmed;
    }

    /**
     * Numeric, segment by segment, ignoring anything after the first
     * non-numeric part ({@code 1.2.3-SNAPSHOT} compares as {@code 1.2.3}).
     * Returns >0 when {@code a} is newer.
     */
    private static int compare(String a, String b) {
        String[] left = a.split("[.\\-+]");
        String[] right = b.split("[.\\-+]");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = numberAt(left, i);
            int r = numberAt(right, i);
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static int numberAt(String[] parts, int index) {
        if (index >= parts.length) {
            return 0; // "1.2" and "1.2.0" are the same version.
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException e) {
            return 0; // A qualifier like "SNAPSHOT" ranks the same as absent, never as newer.
        }
    }
}
