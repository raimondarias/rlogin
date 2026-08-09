package com.raimondarias.rlogin.common.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
            "https://api.github.com/repos/pyrelightmc/rlogin/releases/latest";
    private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public enum Status {
        /** Running exactly the latest published release. */
        UP_TO_DATE,
        /** A newer release exists. */
        OUTDATED,
        /** Running something newer than any release — a self-built jar. */
        AHEAD,
        /** The check didn't work: no network, rate limit, GitHub down. */
        UNKNOWN
    }

    /**
     * What the check found. {@link Status#UNKNOWN} is deliberately distinct
     * from {@link Status#UP_TO_DATE}: reporting "you are on the latest" when
     * the request failed tells a server owner the opposite of the truth, and
     * that is exactly when they most want to know.
     */
    public record Result(Status status, String latestVersion, String currentVersion, String url) {

        public boolean isOutdated() {
            return status == Status.OUTDATED;
        }
    }

    private static final String RELEASES = "https://github.com/pyrelightmc/rlogin/releases";

    private final String currentVersion;

    public UpdateChecker(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    /** Never fails: an unreachable GitHub resolves to {@link Status#UNKNOWN}. */
    public CompletableFuture<Result> check() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LATEST_RELEASE))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "rLogin/" + currentVersion)
                .GET()
                .build();

        // Closed once the answer is in: an HttpClient owns a selector thread and a
        // connection pool, and this one is used for exactly one request at startup.
        // Redirects are followed on purpose: GitHub answers 301 for a repository that
        // has been renamed or moved to an organisation, and the default policy is to
        // follow nothing — which is how this check died silently the first time rLogin
        // changed owner, reporting no update to every server still on the old build.
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(this::parse)
                .exceptionally(e -> unknown())
                .whenComplete((result, error) -> http.close());
    }

    private Result parse(HttpResponse<String> response) {
        if (response.statusCode() != 200 || response.body() == null) {
            return unknown();
        }
        Matcher tag = TAG_PATTERN.matcher(response.body());
        if (!tag.find()) {
            return unknown();
        }
        String latest = strip(tag.group(1));
        int comparison = compare(latest, strip(currentVersion));
        Status status = comparison > 0 ? Status.OUTDATED
                : comparison < 0 ? Status.AHEAD
                : Status.UP_TO_DATE;
        return new Result(status, latest, currentVersion, RELEASES);
    }

    private Result unknown() {
        return new Result(Status.UNKNOWN, null, currentVersion, RELEASES);
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
