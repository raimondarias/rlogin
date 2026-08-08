package com.raimondarias.rlogin.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verificador del formato SHA256 por defecto de AuthMe
 * ({@code $SHA$salt$hash} = sha256(sha256(password) + salt)), usado solo
 * para poder loguear cuentas migradas hasta que rLogin las re-hashee a
 * bcrypt automáticamente en su siguiente login correcto.
 */
public final class AuthMeLegacyHash {

    private static final Pattern FORMAT = Pattern.compile("^\\$SHA\\$([a-fA-F0-9]+)\\$([a-fA-F0-9]+)$");

    private AuthMeLegacyHash() {
    }

    public static boolean matches(String stored) {
        return stored != null && FORMAT.matcher(stored).matches();
    }

    public static boolean verify(String plain, String stored) {
        Matcher m = FORMAT.matcher(stored == null ? "" : stored);
        if (!m.matches()) {
            return false;
        }
        String salt = m.group(1);
        String expectedHash = m.group(2);
        String computed = sha256Hex(sha256Hex(plain) + salt);
        return computed.equalsIgnoreCase(expectedHash);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
