package com.raimondarias.rlogin.paper.hybrid;

import org.bukkit.Bukkit;

/**
 * Soft integration (no hard dependency) with PacketEvents: only used by the
 * premium verification, which only a standalone online-mode:false server
 * has to do. If PacketEvents isn't installed there, this reports
 * unavailable and {@code MissingPacketEventsListener} takes over; behind a
 * proxy the answer is never even asked for.
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
