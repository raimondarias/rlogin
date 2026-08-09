package com.raimondarias.rlogin.paper.hybrid;

import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.List;
import java.util.logging.Logger;

/**
 * Closes the server when it is set up in the one way rLogin cannot make
 * safe: standalone, {@code online-mode: false}, and no PacketEvents.
 *
 * <p>On such a server nothing verifies anyone. rLogin's own passwords still
 * work, but premium players have no way to be recognised, so the choice is
 * between running half-broken and not running at all — and the third option,
 * disabling the plugin, is the worst of the three: it would leave an
 * offline-mode server with no authentication whatsoever, where anyone can
 * connect under any name, including an administrator's.</p>
 *
 * <p>So every connection is refused with an explanation the player can act
 * on (they'll tell the owner), and the console says the same thing loudly at
 * every startup. An admin who sees a locked server and a banner fixes it in
 * a minute; an admin who sees nothing wrong doesn't fix anything, which is
 * exactly how this shipped broken the first time.</p>
 */
public final class MissingPacketEventsListener implements Listener {

    private static final String DOWNLOAD = "https://modrinth.com/plugin/packetevents";
    private static final String LINK_PLACEHOLDER = "{link}";

    private final RLoginPaperPlugin plugin;

    private MissingPacketEventsListener(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    /** Registers the block and prints the banner. Call only when the setup genuinely requires PacketEvents. */
    public static void install(RLoginPaperPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new MissingPacketEventsListener(plugin), plugin);
        printBanner(plugin.getLogger());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickScreen());
    }

    /**
     * The message players get instead of the server. The {@code {link}}
     * placeholder becomes the URL itself, clickable and underlined, so it
     * can be opened straight from the disconnect screen rather than copied
     * off it by hand.
     */
    private Component kickScreen() {
        String template = plugin.messages().get("setup.packetevents-missing");
        int at = template.indexOf(LINK_PLACEHOLDER);
        if (at < 0) {
            // A translation without the placeholder still has to say something useful.
            return LegacyComponentSerializer.legacySection().deserialize(template)
                    .append(Component.newline())
                    .append(downloadLink());
        }
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        return legacy.deserialize(template.substring(0, at))
                .append(downloadLink())
                .append(legacy.deserialize(template.substring(at + LINK_PLACEHOLDER.length())));
    }

    /**
     * Underlined and clickable, but deliberately with no hover text.
     * ViaVersion cannot translate a {@code hoverEvent} on a login-disconnect
     * component for older clients — it throws {@code UnsupportedOperationException:
     * Not implemented} and floods the console on every refused connection.
     * The hover had nothing to add anyway: the link text is already the full
     * URL, so hovering showed what was on screen.
     */
    private static Component downloadLink() {
        return Component.text(DOWNLOAD)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(DOWNLOAD));
    }

    /** Written as plain lines; {@link #printBanner} draws the box around them. */
    private static final List<String> BANNER_LINES = List.of(
            "rLogin cannot run: PacketEvents is missing",
            "",
            "This server is standalone with online-mode: false, so rLogin",
            "has to verify premium accounts against Mojang itself, and it",
            "needs PacketEvents to do that.",
            "",
            "Install it, then restart:",
            DOWNLOAD,
            "",
            "Until then EVERY connection is refused. That is on purpose:",
            "turning rLogin off instead would leave this server with no",
            "authentication at all, and anyone could join as anyone.");

    /**
     * Deliberately loud and boxed: this has to survive being scrolled past in
     * a busy console, and the link has to be readable. The box is padded from
     * the text rather than by hand, so editing a line can't leave the border
     * ragged.
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
