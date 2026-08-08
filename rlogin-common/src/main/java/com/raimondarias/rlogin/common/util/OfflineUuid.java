package com.raimondarias.rlogin.common.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The same "offline" UUID Minecraft/Bukkit/Velocity generates for a
 * non-premium player: {@code UUID.nameUUIDFromBytes("OfflinePlayer:<name>")}.
 *
 * <p>This is the basis of premium detection in {@code rlogin-paper}: if a
 * connecting player's real UUID does NOT match this computation, they came
 * through real Mojang verification (a proxy with Modern Forwarding forcing
 * online-mode, or the server itself running online-mode: true) and are
 * therefore genuinely premium.</p>
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
