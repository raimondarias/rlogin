package com.raimondarias.rlogin.common.security;

import com.raimondarias.rlogin.common.config.RLoginConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a password is allowed, beyond fitting between the
 * configured lengths.
 *
 * <p>A length rule alone accepts {@code 123456}, and on a server where names
 * are public it accepts a player's own name as their password. Both are the
 * first things anyone guessing tries, so both are refused here — and because
 * people reuse passwords across servers, a weak one accepted here is not only
 * this server's problem.</p>
 *
 * <p>The list is small on purpose. It is not an attempt to enumerate bad
 * passwords, which cannot be done; it covers the handful that appear at the
 * top of every breach corpus, where the cost of refusing is one retry and the
 * cost of accepting is an account anyone can walk into.</p>
 */
public final class PasswordPolicy {

    public enum Verdict {
        OK,
        TOO_SHORT,
        TOO_LONG,
        TOO_COMMON,
        SAME_AS_NAME
    }

    private static final String LIST_RESOURCE = "/common-passwords.txt";

    private final RLoginConfig config;
    private final Set<String> common;

    public PasswordPolicy(RLoginConfig config) {
        this.config = config;
        this.common = config.rejectCommonPasswords() ? loadCommonList() : Set.of();
    }

    public Verdict check(String password, String username) {
        if (password.length() < config.passwordMinLength()) {
            return Verdict.TOO_SHORT;
        }
        if (password.length() > config.passwordMaxLength()) {
            return Verdict.TOO_LONG;
        }
        String normalised = password.toLowerCase(Locale.ROOT);
        if (username != null && normalised.equals(username.toLowerCase(Locale.ROOT))) {
            return Verdict.SAME_AS_NAME;
        }
        if (common.contains(normalised)) {
            return Verdict.TOO_COMMON;
        }
        return Verdict.OK;
    }

    /** Exposed for the tests and for {@code /rlogin info}; never for logging. */
    public int commonListSize() {
        return common.size();
    }

    /**
     * Missing or unreadable means the list check is skipped, not that every
     * password is refused: a packaging mistake must not lock a server out of
     * its own registrations.
     */
    private static Set<String> loadCommonList() {
        try (InputStream in = PasswordPolicy.class.getResourceAsStream(LIST_RESOURCE)) {
            if (in == null) {
                return Set.of();
            }
            Set<String> out = new HashSet<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim().toLowerCase(Locale.ROOT);
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        out.add(trimmed);
                    }
                }
            }
            return Collections.unmodifiableSet(out);
        } catch (IOException e) {
            return Set.of();
        }
    }
}
