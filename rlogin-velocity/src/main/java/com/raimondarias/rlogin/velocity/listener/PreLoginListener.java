package com.raimondarias.rlogin.velocity.listener;

import com.raimondarias.rlogin.common.auth.PremiumChecker;
import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The heart of premium auto-login: decides, for every incoming connection,
 * whether Velocity should force the encrypted Mojang handshake (premium
 * account — the client authenticates on its own, no password) or let it
 * through in offline mode (cracked account — rLogin will ask for /login on
 * the backend).
 *
 * <p>This is exactly what's usually called "hybrid mode": the proxy as a
 * whole runs with {@code online-mode: false}, but each individual
 * connection can be forced to online-mode based on what we decide here.</p>
 */
public final class PreLoginListener {

    private final RLoginConfig config;
    private final PremiumChecker premiumChecker;
    private final Logger logger;

    /** UUIDs Velocity already knows are premium in this proxy session (used by {@link SyncListener}). */
    private final Set<java.util.UUID> trustedThisSession = new CopyOnWriteArraySet<>();
    private final ConcurrentHashMap<String, Boolean> lastDecisionByUsername = new ConcurrentHashMap<>();

    public PreLoginListener(RLoginConfig config, PremiumChecker premiumChecker, Logger logger) {
        this.config = config;
        this.premiumChecker = premiumChecker;
        this.logger = logger;
    }

    @Subscribe
    public EventTask onPreLogin(PreLoginEvent event) {
        String username = event.getUsername();

        if (!config.premiumAutoLogin()) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
            lastDecisionByUsername.put(username.toLowerCase(java.util.Locale.ROOT), false);
            return null;
        }

        return EventTask.async(() -> {
            PremiumChecker.PremiumLookup lookup = premiumChecker.lookup(username).join();

            if (lookup.status() == PremiumChecker.Status.ERROR && !config.premiumApiFailOpen()) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        Component.text("Could not verify your account with Mojang. Please try again in a few seconds.")));
                return;
            }

            boolean premium = lookup.status() == PremiumChecker.Status.PREMIUM;
            lastDecisionByUsername.put(username.toLowerCase(java.util.Locale.ROOT), premium);

            if (premium) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                logger.debug("{} detected as premium, forcing online-mode", username);
            } else {
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
            }
        });
    }

    /** Called by {@link SyncListener} as soon as we know the player's final UUID. */
    public boolean wasForcedPremium(String username) {
        return Boolean.TRUE.equals(lastDecisionByUsername.get(username.toLowerCase(java.util.Locale.ROOT)));
    }

    public void forgetDecision(String username) {
        lastDecisionByUsername.remove(username.toLowerCase(java.util.Locale.ROOT));
    }

    Set<java.util.UUID> trustedThisSession() {
        return trustedThisSession;
    }
}
