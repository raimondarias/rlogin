package com.raimondarias.rlogin.common.security;

import com.raimondarias.rlogin.common.auth.PremiumChecker;
import com.raimondarias.rlogin.common.config.RLoginConfig;

import java.util.concurrent.CompletableFuture;

/**
 * Prevents a non-premium account from registering with a name that belongs
 * to a real premium account, so its legitimate owner never loses the
 * ability to use premium auto-login with that same name.
 *
 * <p>If the Mojang API fails, registration is allowed (more permissive than
 * blocking everyone over a temporary Mojang outage).</p>
 */
public final class PremiumNameGuard {

    private final RLoginConfig config;
    private final PremiumChecker premiumChecker;

    public PremiumNameGuard(RLoginConfig config, PremiumChecker premiumChecker) {
        this.config = config;
        this.premiumChecker = premiumChecker;
    }

    public CompletableFuture<Boolean> canRegister(String username) {
        if (!config.protectPremiumNames()) {
            return CompletableFuture.completedFuture(true);
        }
        return premiumChecker.lookup(username)
                .thenApply(result -> result.status() != PremiumChecker.Status.PREMIUM);
    }
}
