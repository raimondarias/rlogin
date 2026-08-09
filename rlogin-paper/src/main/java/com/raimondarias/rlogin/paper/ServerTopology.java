package com.raimondarias.rlogin.paper;

import com.raimondarias.rlogin.common.config.YamlDocument;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Who, if anyone, has already checked a connecting player against Mojang by
 * the time rLogin sees them. This is what decides whether rLogin has to do
 * that verification itself — it is not a preference, it's a fact about how
 * the server is set up, so it is detected rather than configured.
 *
 * <p>An earlier version of rLogin made it a config switch
 * ({@code premium.standalone-hybrid-mode}) that defaulted to off. The result
 * was predictable: on a real server nobody found it, premium players were
 * asked for a password, and the feature looked broken. A setting whose only
 * correct value is "whatever matches your setup" is not a setting.</p>
 */
public enum ServerTopology {

    /**
     * {@code online-mode: true}: the server does the Mojang handshake on its
     * own, every player is already verified, and there is nothing for rLogin
     * to add. Cracked players can't connect at all here.
     */
    ONLINE_MODE,

    /**
     * A Velocity or BungeeCord proxy sits in front and forwards an identity
     * it already verified. rLogin trusts that and stays out of the way —
     * doing the handshake again from here would mean verifying twice.
     */
    BEHIND_PROXY,

    /**
     * Nobody has verified anything: a single {@code online-mode: false}
     * server with nothing in front of it. This is the only case where rLogin
     * must run Mojang's verification itself, and therefore the only case
     * where PacketEvents is needed.
     */
    STANDALONE_OFFLINE;

    /** Whether rLogin has to verify premium accounts itself here. */
    public boolean needsOwnVerification() {
        return this == STANDALONE_OFFLINE;
    }

    /**
     * Reads the server's own configuration rather than asking the admin.
     * Both proxy settings live in files the server always writes, so this
     * works on any fork without touching version-specific internals.
     */
    public static ServerTopology detect(JavaPlugin plugin) {
        if (Bukkit.getOnlineMode()) {
            return ONLINE_MODE;
        }
        Path serverRoot = serverRoot(plugin);
        if (serverRoot != null && (velocityForwarding(serverRoot) || bungeeCordForwarding(serverRoot))) {
            return BEHIND_PROXY;
        }
        // Also the answer when the layout is unrecognisable: assuming "no proxy" makes rLogin
        // verify for itself, which is safe. Assuming the opposite would trust a proxy that
        // might not be there and let anyone in under any name.
        return STANDALONE_OFFLINE;
    }

    /**
     * {@code <server root>/plugins/rLogin} — two levels up from the data
     * folder, which has to be made absolute first: Bukkit hands it out as a
     * path relative to the working directory, so walking up from it directly
     * runs out of parents and yields null.
     */
    private static Path serverRoot(JavaPlugin plugin) {
        Path pluginsDir = plugin.getDataFolder().getAbsoluteFile().toPath().getParent();
        return pluginsDir == null ? null : pluginsDir.getParent();
    }

    private static boolean velocityForwarding(Path serverRoot) {
        return readBoolean(serverRoot.resolve("config").resolve("paper-global.yml"), "proxies.velocity.enabled");
    }

    private static boolean bungeeCordForwarding(Path serverRoot) {
        return readBoolean(serverRoot.resolve("spigot.yml"), "settings.bungeecord");
    }

    /**
     * Missing or unreadable means "not configured", which is the honest
     * reading: a server that never wrote the file certainly isn't forwarding
     * through it. Erring this way makes rLogin verify when in doubt, rather
     * than trust a proxy that may not exist.
     */
    private static boolean readBoolean(Path file, String key) {
        if (Files.notExists(file)) {
            return false;
        }
        try {
            return YamlDocument.read(file).getBoolean(key, false);
        } catch (Exception e) {
            return false;
        }
    }
}
