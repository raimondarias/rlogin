package com.raimondarias.rlogin.common.security;

import com.raimondarias.rlogin.common.auth.PremiumChecker;
import com.raimondarias.rlogin.common.config.RLoginConfig;

import java.util.concurrent.CompletableFuture;

/**
 * Evita que una cuenta no-premium se registre con un nombre que pertenece a
 * una cuenta premium real, para que su dueño legítimo no se quede sin poder
 * usar el auto-login premium con ese mismo nombre.
 *
 * <p>Si la API de Mojang falla, se permite el registro (más permisivo que
 * bloquear a todo el mundo por una caída temporal de Mojang).</p>
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
