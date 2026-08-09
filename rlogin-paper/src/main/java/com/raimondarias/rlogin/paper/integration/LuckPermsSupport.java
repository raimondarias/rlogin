package com.raimondarias.rlogin.paper.integration;

import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Soft integration with LuckPerms: used when it is installed, absent without
 * a trace when it isn't.
 *
 * <p>The boundary matters. Every LuckPerms type lives in {@link
 * LuckPermsHook}, which is only ever loaded after the check below passes —
 * naming one of its classes here would make this class fail to load on a
 * server without the plugin, which is the usual way a "soft" dependency turns
 * out to be hard.</p>
 *
 * <p>Note that rLogin's own permissions — {@code rlogin.admin} and
 * {@code rlogin.bypass} — need none of this. They go through Bukkit's
 * permission API, which LuckPerms already answers.</p>
 */
public final class LuckPermsSupport {

    public enum TransferResult {
        /** Permissions were copied onto the new UUID. */
        MOVED,
        /** The old UUID had nothing worth copying. */
        NOTHING_TO_MOVE,
        /** The new UUID already has permissions; refused rather than overwrite them. */
        TARGET_NOT_EMPTY,
        /** LuckPerms is not installed. */
        UNAVAILABLE,
        /** Something went wrong; the console has it. */
        FAILED
    }

    private final RLoginPaperPlugin plugin;
    private LuckPermsHook hook;

    public LuckPermsSupport(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        try {
            this.hook = new LuckPermsHook(plugin);
            plugin.getLogger().info("LuckPerms detected: rlogin:authenticated is available as a context.");
        } catch (RuntimeException | LinkageError e) {
            // Present but not ready, or a version whose API moved. Not a reason to
            // hold up a server that was working fine a moment ago.
            plugin.getLogger().warning("LuckPerms is installed but could not be hooked: " + e);
        }
    }

    public boolean isAvailable() {
        return hook != null;
    }

    /** Tells LuckPerms this player's authentication state changed. No-op without it. */
    public void refreshContext(Player player) {
        if (hook != null) {
            hook.refreshContext(player);
        }
    }

    /**
     * Copies a player's rank onto the UUID an account was just moved to.
     *
     * <p>Called after {@code /rlogin changeuuid}, which moves the rLogin
     * account but cannot move what belongs to other plugins.</p>
     */
    public CompletableFuture<TransferResult> transferPermissions(UUID from, UUID to) {
        if (hook == null) {
            return CompletableFuture.completedFuture(TransferResult.UNAVAILABLE);
        }
        return hook.transfer(from, to);
    }

    public void shutdown() {
        if (hook != null) {
            hook.shutdown();
            hook = null;
        }
    }
}
