package com.raimondarias.rlogin.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

/**
 * TOTP (RFC 6238) on top of HOTP (RFC 4226) with HMAC-SHA1, the same
 * algorithm Google Authenticator, Authy, Aegis, etc. use by default. No
 * external dependencies: it's all standard JDK cryptography.
 */
public final class Totp {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    /** +/- 1 step (30s) of margin to tolerate small clock drift. */
    private static final int ALLOWED_DRIFT_STEPS = 1;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** Generates a random 160-bit secret, Base32-encoded. */
    public static String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public static boolean verify(String base32Secret, String code) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return false;
        }
        long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        for (long drift = -ALLOWED_DRIFT_STEPS; drift <= ALLOWED_DRIFT_STEPS; drift++) {
            if (constantTimeEquals(generateCode(base32Secret, currentStep + drift), code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A code comparison must not leak how many leading digits matched, or a
     * remote attacker could recover the code digit by digit from response
     * timings. Six digits are too small to risk it.
     */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    public static String currentCode(String base32Secret) {
        return generateCode(base32Secret, Instant.now().getEpochSecond() / TIME_STEP_SECONDS);
    }

    /** Code for a specific instant instead of "now" — mostly useful in tests. */
    public static String codeAtTime(String base32Secret, long epochSeconds) {
        return generateCode(base32Secret, epochSeconds / TIME_STEP_SECONDS);
    }

    /** Standard {@code otpauth://} URI, to paste into any authenticator app. */
    public static String buildOtpAuthUri(String issuer, String account, String base32Secret) {
        String label = urlEncode(issuer) + ":" + urlEncode(account);
        return "otpauth://totp/" + label
                + "?secret=" + base32Secret
                + "&issuer=" + urlEncode(issuer)
                + "&digits=" + DIGITS
                + "&period=" + TIME_STEP_SECONDS;
    }

    private static String generateCode(String base32Secret, long counter) {
        try {
            byte[] key = base32Decode(base32Secret);
            byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);

            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format(Locale.ROOT, "%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate the TOTP code", e);
        }
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // --- Base32 (RFC 4648), unpadded ---

    private static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int bits = 0;
        int value = 0;
        for (byte b : data) {
            value = (value << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32_ALPHABET.charAt((value >>> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32_ALPHABET.charAt((value << (5 - bits)) & 0x1F));
        }
        return sb.toString();
    }

    private static byte[] base32Decode(String s) {
        String clean = s.trim().toUpperCase(Locale.ROOT).replace("=", "");
        int bits = 0;
        int value = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (char c : clean.toCharArray()) {
            int idx = BASE32_ALPHABET.indexOf(c);
            if (idx < 0) {
                continue;
            }
            value = (value << 5) | idx;
            bits += 5;
            if (bits >= 8) {
                out.write((value >>> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
