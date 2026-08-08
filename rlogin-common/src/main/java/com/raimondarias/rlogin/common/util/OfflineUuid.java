package com.raimondarias.rlogin.common.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * El mismo UUID "offline" que genera Minecraft/Bukkit/Velocity para un
 * jugador no-premium: {@code UUID.nameUUIDFromBytes("OfflinePlayer:<nombre>")}.
 *
 * <p>Es la base de la detección de premium en {@code rlogin-paper}: si el
 * UUID real de un jugador que se conecta NO coincide con este cálculo, es
 * que viene de una verificación Mojang real (proxy con Modern Forwarding
 * forzando online-mode, o el propio servidor en online-mode: true) y por
 * tanto es premium de verdad.</p>
 */
public final class OfflineUuid {

    private OfflineUuid() {
    }

    public static UUID of(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isOffline(UUID uuid, String username) {
        return of(username).equals(uuid);
    }
}
