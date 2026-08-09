package com.raimondarias.rlogin.paper.setup;

import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.List;
import java.util.logging.Logger;

/**
 * Closes the server when {@code online-mode} contradicts {@code auth-mode}.
 *
 * <p>With {@code online-mode: true} the server itself turns away every
 * connection Mojang doesn't vouch for, before rLogin is consulted at all.
 * There is no password prompt to fall back to and no plugin can add one:
 * the player is gone at the protocol level. So on a server configured for
 * {@code auth-mode: auto} or {@code offline} — both of which promise that
 * players without an account can register — the two settings describe
 * different servers, and the one that wins is the one rLogin cannot reach.</p>
 *
 * <p>The failure is otherwise invisible: everything looks healthy, the plugin
 * loads, premium players play normally, and the only symptom is that the
 * audience the owner installed rLogin for silently never arrives. Refusing
 * connections turns a silent misconfiguration into an obvious one, the same
 * way {@link com.raimondarias.rlogin.paper.hybrid.MissingPacketEventsListener}
 * does.</p>
 *
 * <p>{@code auth-mode: online} is the setup where {@code online-mode: true}
 * is exactly right — the owner has said this server is for premium accounts
 * only, and letting the server do that verifying is the cheapest way to get
 * it. Nothing here runs in that case.</p>
 */
public final class OnlineModeConflictListener implements Listener {

    private final RLoginPaperPlugin plugin;

    private OnlineModeConflictListener(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers the block and prints the banner if — and only if — the server
     * is running online-mode while promising to accept players who have no
     * account. Returns whether it did, so startup can report it.
     */
    public static boolean installIfConflicting(RLoginPaperPlugin plugin) {
        if (!plugin.getServer().getOnlineMode() || !plugin.config().authMode().allowsPasswords()) {
            return false;
        }
        plugin.getServer().getPluginManager().registerEvents(new OnlineModeConflictListener(plugin), plugin);
        printBanner(plugin.getLogger());
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                LegacyComponentSerializer.legacySection()
                        .deserialize(plugin.messages().get("setup.online-mode-conflict")));
    }

    /** Written as plain lines; {@link #printBanner} draws the box around them. */
    private static final List<String> BANNER_LINES = List.of(
            "rLogin cannot run: online-mode is enabled",
            "",
            "server.properties has online-mode=true, so this server rejects",
            "every player without a Minecraft account before rLogin ever",
            "sees them. There is nothing left for /register to do.",
            "",
            "Set online-mode=false in server.properties, then restart.",
            "",
            "If this server really is for premium accounts only, say so",
            "instead: set auth-mode: online in rLogin's config.yml and",
            "online-mode=true becomes the right setup, not a contradiction.",
            "",
            "Until one of those changes, EVERY connection is refused.");

    /**
     * Deliberately loud and boxed: this has to survive being scrolled past in
     * a busy console. The box is padded from the text rather than by hand, so
     * editing a line can't leave the border ragged.
     */
    private static void printBanner(Logger logger) {
        int width = BANNER_LINES.stream().mapToInt(String::length).max().orElse(0) + 6;
        String edge = "#".repeat(width);
        logger.severe("");
        logger.severe(edge);
        logger.severe(pad("", width));
        for (String line : BANNER_LINES) {
            logger.severe(pad(line, width));
        }
        logger.severe(pad("", width));
        logger.severe(edge);
        logger.severe("");
    }

    private static String pad(String text, int width) {
        return "#  " + text + " ".repeat(width - text.length() - 4) + "#";
    }
}
