package com.raimondarias.rlogin.common.auth;

import com.raimondarias.rlogin.api.RLoginAccount;
import com.raimondarias.rlogin.api.db.Storage;
import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.raimondarias.rlogin.common.security.PasswordHasher;
import com.raimondarias.rlogin.common.security.PasswordPolicy;
import com.raimondarias.rlogin.common.security.RecoveryThrottle;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * One-time codes that let a player back into their own account.
 *
 * <p>Without these, "I forgot my password" and "I lost my authenticator" both
 * end at an administrator with database access, which is a support burden for
 * the owner and an indefinite lockout for the player. Worse, the usual fix —
 * an admin running {@code unregister} — hands the name to whoever registers
 * it next, so the recovery path is itself an attack if anyone can talk staff
 * into it.</p>
 *
 * <p>Codes are shown once, at registration, and stored only as bcrypt hashes:
 * a recovery code readable from the database is a second password that skips
 * the first. Using one sets a new password <em>and</em> clears any TOTP, since
 * the two things people lose are the two things a code has to be able to
 * replace — and a code that restored access without clearing 2FA would leave
 * the player still locked out by the factor they no longer have.</p>
 *
 * <p>Each code works once. What remains is reported back so a player can be
 * told when they are running out.</p>
 */
public final class RecoveryService {

    /**
     * Deliberately without {@code 0/O}, {@code 1/I/L} and {@code 5/S}: these
     * get read off a screen and typed by hand, often from a phone photo, and
     * a code refused because of a misread character is indistinguishable from
     * a wrong one.
     */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRTUVWXYZ2346789";
    private static final int GROUP = 4;
    private static final int GROUPS = 2;

    private final Storage storage;
    private final RLoginConfig config;
    private final PasswordHasher hasher;
    private final PasswordPolicy passwordPolicy;
    private final RecoveryThrottle throttle;
    private final SecureRandom random = new SecureRandom();

    public RecoveryService(Storage storage, RLoginConfig config, PasswordHasher hasher,
                           PasswordPolicy passwordPolicy) {
        this.storage = storage;
        this.config = config;
        this.hasher = hasher;
        this.passwordPolicy = passwordPolicy;
        this.throttle = new RecoveryThrottle(config);
    }

    public enum RecoverResult {
        SUCCESS,
        DISABLED,
        NOT_REGISTERED,
        NO_CODES,
        WRONG_CODE,
        PASSWORD_REJECTED,
        THROTTLED
    }

    public record RecoverOutcome(RecoverResult result, int codesRemaining,
                                 PasswordPolicy.Verdict passwordVerdict, long lockedSecondsRemaining) {
        static RecoverOutcome of(RecoverResult result) {
            return new RecoverOutcome(result, 0, PasswordPolicy.Verdict.OK, 0);
        }
    }

    public boolean isEnabled() {
        return config.recoveryCodesEnabled();
    }

    /**
     * Issues a fresh set, replacing any previous one, and returns the
     * plaintext codes for showing to the player exactly once.
     */
    public CompletableFuture<List<String>> issueCodes(UUID uuid) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<String> codes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < Math.max(1, config.recoveryCodeCount()); i++) {
            String code = generateCode();
            codes.add(code);
            hashes.add(hasher.hash(normalise(code)));
        }
        return storage.replaceRecoveryCodes(uuid, hashes).thenApply(ignored -> List.copyOf(codes));
    }

    public CompletableFuture<Integer> remainingCodes(UUID uuid) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(0);
        }
        return storage.unusedRecoveryCodeHashes(uuid).thenApply(List::size);
    }

    /**
     * Spends a code to set a new password and clear any second factor.
     *
     * <p>The new password goes through the same policy as {@code /register}:
     * an account being recovered is exactly when somebody reaches for
     * something memorable and terrible.</p>
     */
    public CompletableFuture<RecoverOutcome> recover(UUID uuid, String ip, String code, String newPassword) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(RecoverOutcome.of(RecoverResult.DISABLED));
        }
        Instant now = Instant.now();
        long lockedFor = throttle.lockedSecondsRemaining(ip, uuid, now);
        if (lockedFor > 0) {
            return CompletableFuture.completedFuture(
                    new RecoverOutcome(RecoverResult.THROTTLED, 0, PasswordPolicy.Verdict.OK, lockedFor));
        }
        return storage.findByUuid(uuid).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(RecoverOutcome.of(RecoverResult.NOT_REGISTERED));
            }
            RLoginAccount account = found.get();
            PasswordPolicy.Verdict verdict = passwordPolicy.check(newPassword, account.username());
            if (verdict != PasswordPolicy.Verdict.OK) {
                return CompletableFuture.completedFuture(
                        new RecoverOutcome(RecoverResult.PASSWORD_REJECTED, 0, verdict, 0));
            }
            return storage.unusedRecoveryCodeHashes(uuid).thenCompose(hashes -> {
                if (hashes.isEmpty()) {
                    return CompletableFuture.completedFuture(RecoverOutcome.of(RecoverResult.NO_CODES));
                }
                Optional<String> match = hashes.stream()
                        .filter(hash -> hasher.verify(normalise(code), hash))
                        .findFirst();
                if (match.isEmpty()) {
                    throttle.recordFailure(ip, uuid, now);
                    return CompletableFuture.completedFuture(RecoverOutcome.of(RecoverResult.WRONG_CODE));
                }
                RLoginAccount recovered = new RLoginAccount(
                        account.uuid(), account.username(), account.premium(),
                        hasher.hash(newPassword), PasswordHasher.ALGO_ID,
                        // The second factor goes with it: someone recovering has usually lost
                        // the authenticator, and restoring the password alone would leave them
                        // locked out by the very thing they came here about.
                        null, false,
                        account.lastIp(), account.lastLoginAt(), account.registeredAt(),
                        0, null);
                return storage.save(recovered)
                        .thenCompose(ignored -> storage.consumeRecoveryCode(uuid, match.get()))
                        // Any session predates the recovery, and whoever held it is the
                        // reason this account needed recovering in the first place.
                        .thenCompose(ignored -> storage.clearSession(uuid))
                        .thenApply(ignored -> {
                            throttle.recordSuccess(ip, uuid);
                            return new RecoverOutcome(
                                    RecoverResult.SUCCESS, hashes.size() - 1, PasswordPolicy.Verdict.OK, 0);
                        });
            });
        });
    }

    /** Typed by hand, so case and dashes are not part of the secret. */
    private static String normalise(String code) {
        return code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private String generateCode() {
        StringBuilder out = new StringBuilder();
        for (int group = 0; group < GROUPS; group++) {
            if (group > 0) {
                out.append('-');
            }
            for (int i = 0; i < GROUP; i++) {
                out.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
        }
        return out.toString();
    }
}
