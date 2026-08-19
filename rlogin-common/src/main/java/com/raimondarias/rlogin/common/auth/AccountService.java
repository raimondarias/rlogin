package com.raimondarias.rlogin.common.auth;

import com.raimondarias.rlogin.api.RLoginAccount;
import com.raimondarias.rlogin.api.db.Storage;
import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.raimondarias.rlogin.common.security.AuthMeLegacyHash;
import com.raimondarias.rlogin.common.security.BruteforceGuard;
import com.raimondarias.rlogin.common.security.IpThrottle;
import com.raimondarias.rlogin.common.security.PasswordPolicy;
import com.raimondarias.rlogin.common.security.RegistrationLimiter;
import com.raimondarias.rlogin.common.security.PasswordHasher;
import com.raimondarias.rlogin.common.security.PremiumNameGuard;
import com.raimondarias.rlogin.common.security.Totp;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates register/login/2FA on top of {@link Storage}, applying
 * hashing, brute-force lockout and premium-name protection. This is the
 * single entry point used by {@code rlogin-paper}'s commands.
 */
public final class AccountService {

    public enum RegisterResult {
        SUCCESS, ALREADY_REGISTERED, PASSWORDS_DONT_MATCH, INVALID_LENGTH, PREMIUM_PROTECTED,
        TOO_MANY_FROM_IP, PASSWORD_TOO_COMMON, PASSWORD_IS_NAME
    }

    public enum LoginResult {
        SUCCESS, WRONG_PASSWORD, NOT_REGISTERED, LOCKED, NEEDS_TOTP, WRONG_TOTP, PREMIUM_NO_PASSWORD
    }

    public record LoginOutcome(LoginResult result, RLoginAccount account, long lockedSecondsRemaining,
                               int attemptsLeft, String previousIp) {
        static LoginOutcome of(LoginResult result) {
            return new LoginOutcome(result, null, 0, 0, null);
        }
    }

    private final Storage storage;
    private final RLoginConfig config;
    private final PasswordHasher hasher;
    private final BruteforceGuard bruteforceGuard;
    private final IpThrottle ipThrottle;
    private final RegistrationLimiter registrationLimiter;
    private final PasswordPolicy passwordPolicy;
    private final PremiumNameGuard premiumNameGuard;

    public AccountService(Storage storage, RLoginConfig config, PremiumNameGuard premiumNameGuard) {
        this.storage = storage;
        this.config = config;
        this.hasher = new PasswordHasher(config.bcryptCost());
        this.bruteforceGuard = new BruteforceGuard(config);
        this.ipThrottle = new IpThrottle(config, bruteforceGuard);
        this.registrationLimiter = new RegistrationLimiter(config);
        this.passwordPolicy = new PasswordPolicy(config);
        this.premiumNameGuard = premiumNameGuard;
    }

    public CompletableFuture<Optional<RLoginAccount>> find(UUID uuid) {
        return storage.findByUuid(uuid);
    }

    public CompletableFuture<Optional<RLoginAccount>> findByUsername(String username) {
        return storage.findByUsername(username);
    }

    /** Creates or refreshes an account already verified as premium by Velocity/Mojang; never asks for a password. */
    public CompletableFuture<RLoginAccount> upsertPremium(UUID uuid, String username, String ip) {
        Instant now = Instant.now();
        return storage.findByUuid(uuid).thenCompose(existing -> {
            Instant registeredAt = existing.map(RLoginAccount::registeredAt).orElse(now);
            String passwordHash = existing.map(RLoginAccount::passwordHash).orElse(null);
            String hashAlgo = existing.map(RLoginAccount::hashAlgo).orElse(null);
            String totpSecret = existing.map(RLoginAccount::totpSecret).orElse(null);
            boolean totpEnabled = existing.map(RLoginAccount::totpEnabled).orElse(false);
            RLoginAccount toSave = new RLoginAccount(uuid, username, true, passwordHash, hashAlgo, totpSecret,
                    totpEnabled, ip, now, registeredAt, 0, null);
            return storage.save(toSave);
        });
    }

    /**
     * @param ip the address they registered from, stored as the account's
     *           last-seen address. It matters beyond bookkeeping: standalone
     *           hybrid mode uses it to recognise a returning cracked player
     *           and skip the premium handshake for them. Leaving it null
     *           until their first {@code /login} would mean a player who
     *           registers and is then carried by "remember me" never gets
     *           recognised at all.
     */
    public CompletableFuture<RegisterResult> register(UUID uuid, String username, String password, String confirm,
                                                       String ip) {
        if (!password.equals(confirm)) {
            return CompletableFuture.completedFuture(RegisterResult.PASSWORDS_DONT_MATCH);
        }
        RegisterResult rejection = switch (passwordPolicy.check(password, username)) {
            case TOO_SHORT, TOO_LONG -> RegisterResult.INVALID_LENGTH;
            case TOO_COMMON -> RegisterResult.PASSWORD_TOO_COMMON;
            case SAME_AS_NAME -> RegisterResult.PASSWORD_IS_NAME;
            case OK -> null;
        };
        if (rejection != null) {
            return CompletableFuture.completedFuture(rejection);
        }
        // Checked before touching the database: refusing the flood is the point, and
        // a lookup per attempt is the cost this is meant to avoid paying.
        Instant attemptedAt = Instant.now();
        if (!registrationLimiter.isAllowed(ip, attemptedAt)) {
            return CompletableFuture.completedFuture(RegisterResult.TOO_MANY_FROM_IP);
        }
        return storage.findByUuid(uuid).thenCompose(existing -> {
            if (existing.isPresent()) {
                return CompletableFuture.completedFuture(RegisterResult.ALREADY_REGISTERED);
            }
            return premiumNameGuard.canRegister(username).thenCompose(allowed -> {
                if (!allowed) {
                    return CompletableFuture.completedFuture(RegisterResult.PREMIUM_PROTECTED);
                }
                Instant now = Instant.now();
                RLoginAccount account = new RLoginAccount(uuid, username, false, hasher.hash(password),
                        PasswordHasher.ALGO_ID, null, false, ip, now, now, 0, null);
                return storage.save(account).thenApply(saved -> {
                    // Only successful creations count: a refused attempt is somebody
                    // getting it wrong, not somebody consuming what this protects.
                    registrationLimiter.recordRegistration(ip, now);
                    return RegisterResult.SUCCESS;
                });
            });
        });
    }

    public CompletableFuture<LoginOutcome> login(UUID uuid, String password, String totpCode, String ip) {
        return storage.findByUuid(uuid).thenCompose(existingOpt -> {
            if (existingOpt.isEmpty()) {
                return CompletableFuture.completedFuture(LoginOutcome.of(LoginResult.NOT_REGISTERED));
            }
            RLoginAccount account = existingOpt.get();
            if (account.premium()) {
                return CompletableFuture.completedFuture(LoginOutcome.of(LoginResult.PREMIUM_NO_PASSWORD));
            }
            Instant now = Instant.now();
            String previousIp = account.lastIp();
            // Locked by ADDRESS, never by account: locking the account would let anyone who
            // knows a name keep its owner out by failing logins on purpose. See IpThrottle.
            long lockedFor = ipThrottle.lockedSecondsRemaining(ip, now);
            if (lockedFor > 0) {
                return CompletableFuture.completedFuture(
                        new LoginOutcome(LoginResult.LOCKED, account, lockedFor, 0, previousIp));
            }
            if (!verifyPassword(password, account)) {
                return registerFailedAttempt(account, ip, now, LoginResult.WRONG_PASSWORD);
            }
            if (account.totpEnabled()) {
                if (totpCode == null || totpCode.isBlank()) {
                    return CompletableFuture.completedFuture(
                            new LoginOutcome(LoginResult.NEEDS_TOTP, account, 0, 0, previousIp));
                }
                if (!Totp.verify(account.totpSecret(), totpCode)) {
                    return registerFailedAttempt(account, ip, now, LoginResult.WRONG_TOTP);
                }
            }
            ipThrottle.recordSuccess(ip);
            RLoginAccount authenticated = account.withLogin(ip, now);
            // Account migrated from another plugin (e.g. AuthMe SHA256): on a successful
            // login, it gets re-hashed to bcrypt, retiring the legacy algorithm for good.
            if (!PasswordHasher.ALGO_ID.equals(account.hashAlgo())) {
                authenticated = authenticated.withPassword(hasher.hash(password), PasswordHasher.ALGO_ID);
            }
            return storage.save(authenticated)
                    .thenApply(saved -> new LoginOutcome(LoginResult.SUCCESS, saved, 0, 0, previousIp));
        });
    }

    /** Bcrypt is the native format; legacy SHA256 from accounts imported from AuthMe is accepted too. */
    private boolean verifyPassword(String plain, RLoginAccount account) {
        String stored = account.passwordHash();
        if (AuthMeLegacyHash.matches(stored)) {
            return AuthMeLegacyHash.verify(plain, stored);
        }
        return hasher.verify(plain, stored);
    }

    /**
     * The address is what gets throttled; the account only keeps a running
     * count so {@code /rlogin info} can show that someone has been trying.
     * {@code lockedUntil} is deliberately left null — locking the account is
     * what made this a way to keep its owner out.
     */
    private CompletableFuture<LoginOutcome> registerFailedAttempt(RLoginAccount account, String ip,
                                                                    Instant now, LoginResult reason) {
        int attemptsLeft = ipThrottle.recordFailure(ip, now);
        RLoginAccount updated = account.withFailedAttempt(account.failedAttempts() + 1, null);
        return storage.save(updated)
                .thenApply(saved -> new LoginOutcome(reason, saved, 0, attemptsLeft, account.lastIp()));
    }

    /**
     * Deletes the account, and with it the password and any TOTP secret —
     * they live on the same row, so there is nothing left to disable
     * separately.
     *
     * <p>The "remember me" session goes too. It outlives the account
     * otherwise: nothing links the two tables, and the session check asks
     * only whether one is valid for this UUID and address, not whether the
     * account still exists. An unregistered player reconnecting from the same
     * address would be waved straight in, with no account and no prompt to
     * make one.</p>
     */
    /** How long this address must wait before it may create another account. */
    public long secondsUntilRegistrationAllowed(String ip) {
        return registrationLimiter.secondsUntilAllowed(ip, Instant.now());
    }

    public CompletableFuture<Void> unregister(UUID uuid) {
        return storage.delete(uuid).thenCompose(ignored -> storage.clearSession(uuid));
    }

    public enum ChangeIdentityResult {
        SUCCESS, SOURCE_NOT_FOUND, TARGET_ALREADY_EXISTS, SAME_IDENTITY
    }

    /**
     * Moves an account's credentials — password, 2FA, registration date — to
     * a different UUID/name, deleting the old row.
     *
     * <p>This is the answer to a name changing hands: a cracked player
     * registered a name, its real premium owner later claimed it, and the
     * two are now separate accounts (different UUIDs) by design. The cracked
     * player doesn't lose what they had, they carry it to whatever identity
     * they play as now.</p>
     *
     * <p><b>Scope, deliberately:</b> only what rLogin itself owns. Inventory,
     * position and experience live in the world's {@code playerdata}, and
     * permissions/economy live in other plugins' storage — all keyed by UUID
     * and none of it reachable from here. Anything beyond credentials has to
     * be moved with the tools that own it.</p>
     */
    public CompletableFuture<ChangeIdentityResult> changeIdentity(UUID from, UUID to, String newUsername) {
        if (from.equals(to)) {
            return CompletableFuture.completedFuture(ChangeIdentityResult.SAME_IDENTITY);
        }
        return storage.findByUuid(from).thenCompose(sourceOpt -> {
            if (sourceOpt.isEmpty()) {
                return CompletableFuture.completedFuture(ChangeIdentityResult.SOURCE_NOT_FOUND);
            }
            return storage.findByUuid(to).thenCompose(targetOpt -> {
                if (targetOpt.isPresent()) {
                    // Never silently merge two real accounts into one.
                    return CompletableFuture.completedFuture(ChangeIdentityResult.TARGET_ALREADY_EXISTS);
                }
                RLoginAccount source = sourceOpt.get();
                String username = newUsername != null ? newUsername : source.username();
                // Written before deleted: a failure here leaves the original untouched
                // rather than losing the account between the two calls.
                return storage.save(source.withIdentity(to, username))
                        .thenCompose(saved -> storage.clearSession(from))
                        .thenCompose(ignored -> storage.delete(from))
                        .thenApply(ignored -> ChangeIdentityResult.SUCCESS);
            });
        });
    }

    public CompletableFuture<Boolean> changePassword(UUID uuid, String oldPassword, String newPassword) {
        if (newPassword.length() < config.passwordMinLength() || newPassword.length() > config.passwordMaxLength()) {
            return CompletableFuture.completedFuture(false);
        }
        return storage.findByUuid(uuid).thenCompose(opt -> {
            if (opt.isEmpty() || opt.get().premium() || !verifyPassword(oldPassword, opt.get())) {
                return CompletableFuture.completedFuture(false);
            }
            RLoginAccount updated = opt.get().withPassword(hasher.hash(newPassword), PasswordHasher.ALGO_ID);
            return storage.save(updated).thenApply(saved -> true);
        });
    }

    public CompletableFuture<RLoginAccount> forceLogin(UUID uuid, String ip) {
        return storage.findByUuid(uuid).thenCompose(opt -> {
            RLoginAccount account = opt.orElseThrow(() -> new IllegalStateException("Account not found"));
            return storage.save(account.withLogin(ip, Instant.now()));
        });
    }

    /** Generates a new TOTP secret, not yet enabled (needs confirming via {@link #confirmTotp}). */
    public CompletableFuture<String> beginTotpSetup(UUID uuid) {
        String secret = Totp.generateSecret();
        return storage.findByUuid(uuid).thenCompose(opt -> {
            RLoginAccount account = opt.orElseThrow(() -> new IllegalStateException("Account not found"));
            return storage.save(account.withTotp(secret, false)).thenApply(saved -> secret);
        });
    }

    public CompletableFuture<Boolean> confirmTotp(UUID uuid, String code) {
        return storage.findByUuid(uuid).thenCompose(opt -> {
            RLoginAccount account = opt.orElseThrow(() -> new IllegalStateException("Account not found"));
            if (account.totpSecret() == null || !Totp.verify(account.totpSecret(), code)) {
                return CompletableFuture.completedFuture(false);
            }
            return storage.save(account.withTotp(account.totpSecret(), true)).thenApply(saved -> true);
        });
    }

    public CompletableFuture<Void> disableTotp(UUID uuid) {
        return storage.findByUuid(uuid).thenCompose(opt -> {
            RLoginAccount account = opt.orElseThrow(() -> new IllegalStateException("Account not found"));
            return storage.save(account.withTotp(null, false)).thenAccept(saved -> {
            });
        });
    }
}
