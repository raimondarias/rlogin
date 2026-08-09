package com.raimondarias.rlogin.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.slf4j.Logger;

import java.util.List;

/**
 * Closes the proxy when {@code online-mode} contradicts {@code auth-mode}.
 *
 * <p>With {@code online-mode = true} in {@code velocity.toml}, Velocity
 * authenticates every connection against Mojang before any plugin is
 * consulted, and simply drops the ones that fail. rLogin's whole premium
 * mechanism is per-player: it asks Mojang about each name and then forces
 * that one connection online or offline. None of that can happen if the
 * connection is already gone.</p>
 *
 * <p>So a network configured for {@code auth-mode: auto} or {@code offline}
 * with online-mode on looks completely healthy and quietly serves nobody it
 * was built for. This makes that visible in the only way an owner cannot
 * scroll past.</p>
 *
 * <p>Nothing here runs under {@code auth-mode: online} — a premium-only
 * network is exactly what {@code online-mode = true} is for, and letting
 * Velocity do the verifying is the cheapest way to get it.</p>
 */
public final class OnlineModeConflictListener {

    public OnlineModeConflictListener(Logger logger) {
        printBanner(logger);
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(kickScreen()));
    }

    /**
     * Built as components rather than from the message files on purpose: the
     * language settings live on the backends, and this proxy is refusing
     * connections precisely because no backend will ever be reached.
     */
    private static Component kickScreen() {
        return Component.text("Server misconfigured", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("This network has online-mode enabled, so players without a "
                        + "Minecraft account cannot connect at all.", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Tell an administrator to set online-mode to false in velocity.toml.",
                        NamedTextColor.GOLD));
    }

    /** Written as plain lines; {@link #printBanner} draws the box around them. */
    private static final List<String> BANNER_LINES = List.of(
            "rLogin cannot run: online-mode is enabled",
            "",
            "velocity.toml has online-mode = true, so this proxy rejects",
            "every player without a Minecraft account before rLogin ever",
            "sees them. Premium accounts are verified here per player, and",
            "that only works when Velocity leaves the decision to us.",
            "",
            "Set online-mode = false in velocity.toml, then restart.",
            "",
            "If this network really is for premium accounts only, say so",
            "instead: set auth-mode: online in rLogin's config.yml and",
            "online-mode = true becomes the right setup, not a mistake.",
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
        logger.error("");
        logger.error(edge);
        logger.error(pad("", width));
        for (String line : BANNER_LINES) {
            logger.error(pad(line, width));
        }
        logger.error(pad("", width));
        logger.error(edge);
        logger.error("");
    }

    private static String pad(String text, int width) {
        return "#  " + text + " ".repeat(width - text.length() - 4) + "#";
    }
}
