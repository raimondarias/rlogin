package com.raimondarias.rlogin.paper.hybrid;

import org.bukkit.Bukkit;

/**
 * Soft integration (no hard dependency) with PacketEvents: only used by the
 * optional standalone hybrid-auth mode ({@code premium.standalone-hybrid-mode}
 * in config.yml). If PacketEvents isn't installed (or failed to enable),
 * this reports unavailable and nothing else in this package is ever
 * touched — same pattern as {@code FloodgateSupport}.
 */
public final class PacketEventsSupport {

    private PacketEventsSupport() {
    }

    public static boolean isAvailable() {
        try {
            return Bukkit.getPluginManager().isPluginEnabled("packetevents")
                    && Class.forName("com.github.retrooper.packetevents.PacketEvents") != null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }
}
