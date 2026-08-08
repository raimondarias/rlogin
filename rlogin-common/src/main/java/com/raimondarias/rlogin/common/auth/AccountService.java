package com.raimondarias.rlogin.common.auth;

import com.raimondarias.rlogin.api.RLoginAccount;
import com.raimondarias.rlogin.api.db.Storage;
import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.raimondarias.rlogin.common.security.AuthMeLegacyHash;
import com.raimondarias.rlogin.common.security.BruteforceGuard;
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
        SUCCESS, ALREADY_REGISTERED, PASSWORDS_DONT_MATCH, INVALID_LENGTH, PREMIUM_PROTECTED
    }

    public enum LoginResult {
        SUCCESS, WRONG_PASSWORD, NOT_REGISTERED, LOCKED, NEEDS_TOTP, WRONG_TOTP, PREMIUM_NO_PASSWORD
    }

    public record LoginOutcome(LoginResult result, RLoginAccount account, long lockedSecondsRemaining, int attemptsLeft) {
        static LoginOutcome of(LoginResult result) {
            return new LoginOutcome(result, null, 0, 0);
        }
    }

    private final Storage storage;
    private final RLoginConfig config;
    private final PasswordHasher hasher;
    private final BruteforceGuard bruteforceGuard;
    private final PremiumNameGuard premiumNameGuard;

    public AccountService(Storage storage, RLoginConfig config, PremiumNameGuard premiumNameGuard) {
        this.storage = storage;
        this.config = config;
        this.hasher = new PasswordHasher(config.bcryptCost());
        this.bruteforceGuard = new BruteforceGuard(config);
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

    public CompletableFuture<RegisterResult> register(UUID uuid, String username, String password, String confirm) {
        if (!password.equals(confirm)) {
            return CompletableFuture.completedFuture(RegisterResult.PASSWORDS_DONT_MATCH);
        }
        if (password.length() < config.passwordMinLength() || password.length() > config.passwordMaxLength()) {
            return CompletableFuture.completedFuture(RegisterResult.INVALID_LENGTH);
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
                        PasswordHasher.ALGO_ID, null, false, null, now, now, 0, null);
                return storage.save(account).thenApply(saved -> RegisterResult.SUCCESS);
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
            if (bruteforceGuard.isEnabled() && account.isLocked(now)) {
                long remaining = account.lockedUntil().getEpochSecond() - now.getEpochSecond();
                return CompletableFuture.completedFuture(
                        new LoginOutcome(LoginResult.LOCKED, account, Math.max(remaining, 0), 0));
            }
            if (!verifyPassword(password, account)) {
                return registerFailedAttempt(account, now, LoginResult.WRONG_PASSWORD);
            }
            if (account.totpEnabled()) {
                if (totpCode == null || totpCode.isBlank()) {
                    return CompletableFuture.completedFuture(new LoginOutcome(LoginResult.NEEDS_TOTP, account, 0, 0));
                }
                if (!Totp.verify(account.totpSecret(), totpCode)) {
                    return registerFailedAttempt(account, now, LoginResult.WRONG_TOTP);
                }
            }
            RLoginAccount authenticated = account.withLogin(ip, now);
            // Account migrated from another plugin (e.g. AuthMe SHA256): on a successful
            // login, it gets re-hashed to bcrypt, retiring the legacy algorithm for good.
            if (!PasswordHasher.ALGO_ID.equals(account.hashAlgo())) {
                authenticated = authenticated.withPassword(hasher.hash(password), PasswordHasher.ALGO_ID);
            }
            return storage.save(authenticated).thenApply(saved -> new LoginOutcome(LoginResult.SUCCESS, saved, 0, 0));
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

    private CompletableFuture<LoginOutcome> registerFailedAttempt(RLoginAccount account, Instant now, LoginResult reason) {
        int attempts = account.failedAttempts() + 1;
        Instant lockUntil = bruteforceGuard.isEnabled() ? bruteforceGuard.nextLockUntil(attempts, now) : null;
        RLoginAccount updated = account.withFailedAttempt(attempts, lockUntil);
        int attemptsLeft = Math.max(0, config.bruteforceMaxAttempts() - attempts);
        return storage.save(updated).thenApply(saved -> new LoginOutcome(reason, saved, 0, attemptsLeft));
    }

    public CompletableFuture<Void> unregister(UUID uuid) {
        return storage.delete(uuid);
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
