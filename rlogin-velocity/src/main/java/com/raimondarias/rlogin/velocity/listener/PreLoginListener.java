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
 * El corazón del auto-login premium: decide, para cada conexión entrante,
 * si Velocity debe forzar el handshake cifrado con Mojang (cuenta premium,
 * el cliente se autentica solo, sin contraseña) o dejarla pasar en modo
 * offline (cuenta cracked, rLogin le pedirá /login en el backend).
 *
 * <p>Esto es exactamente lo que Minecraft llama "modo híbrido": el proxy en
 * su conjunto corre con {@code online-mode: false}, pero cada conexión
 * individual puede forzarse a online-mode según lo que decidamos aquí.</p>
 */
public final class PreLoginListener {

    private final RLoginConfig config;
    private final PremiumChecker premiumChecker;
    private final Logger logger;

    /** UUIDs que Velocity ya sabe premium en esta sesión de proxy (útil para {@link SyncListener}). */
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
                        Component.text("No se pudo verificar tu cuenta con Mojang. Inténtalo de nuevo en unos segundos.")));
                return;
            }

            boolean premium = lookup.status() == PremiumChecker.Status.PREMIUM;
            lastDecisionByUsername.put(username.toLowerCase(java.util.Locale.ROOT), premium);

            if (premium) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                logger.debug("{} detectado como premium, forzando online-mode", username);
            } else {
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
            }
        });
    }

    /** Llamado por {@link SyncListener} en cuanto conocemos el UUID final del jugador. */
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
