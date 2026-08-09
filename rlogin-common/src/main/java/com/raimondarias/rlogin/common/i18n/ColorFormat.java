package com.raimondarias.rlogin.common.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns a message written in any mix of the three color formats rLogin
 * accepts into a legacy ({@code §}-code) string ready for
 * {@code CommandSender#sendMessage(String)} — with full hex fidelity even
 * for input that never used hex: a named color and a legacy code both
 * round-trip through hex on the way out, so the client always renders the
 * exact color, never a lossy 16-color downgrade.
 *
 * <p>Accepted input, freely mixed in the same string:</p>
 * <ul>
 *   <li><b>MiniMessage</b> (Kyori) — {@code <red>}, {@code <#e39fff>}, {@code <bold>}...</li>
 *   <li><b>Legacy codes</b> (classic) — {@code &a} / {@code §a}, including
 *       style codes ({@code &l}, {@code &n}...) and {@code &r} to reset.</li>
 *   <li><b>Hex</b> (BungeeCord/Spigot config style) — {@code &#RRGGBB},
 *       {@code §x§R§R§G§G§B§B} (or its {@code &x&...} variant), or a bare
 *       {@code #RRGGBB}.</li>
 * </ul>
 *
 * <p>Implementation: every accepted syntax is first normalized into
 * MiniMessage tags via regex, then parsed once with {@link MiniMessage}, then
 * serialized back down to a hex-capable legacy string. This keeps
 * {@link Messages#get(String, Map)} returning a plain {@code String}, so
 * nothing calling it needs to change.</p>
 */
final class ColorFormat {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_OUT = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            // Without this, hexColors() serializes as "§#RRGGBB" — Adventure's own shorthand,
            // which vanilla Minecraft/Bukkit does NOT understand. This switches the output to
            // the BungeeCord/Spigot-compatible "§x§R§R§G§G§B§B" repeated-character format,
            // which is what every client and CommandSender#sendMessage(String) actually parses.
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    // &#RRGGBB or §#RRGGBB
    private static final Pattern PREFIXED_HEX = Pattern.compile("[&§]#([0-9A-Fa-f]{6})");
    // &x&R&R&G&G&B&B or §x§R§R§G§G§B§B — BungeeCord's "repeated character" hex format.
    private static final Pattern REPEATED_HEX = Pattern.compile(
            "[&§]x[&§]([0-9A-Fa-f])[&§]([0-9A-Fa-f])[&§]([0-9A-Fa-f])[&§]([0-9A-Fa-f])[&§]([0-9A-Fa-f])[&§]([0-9A-Fa-f])");
    // Bare #RRGGBB, left over once the two forms above are gone (so it never
    // re-matches hex already turned into a <#RRGGBB> tag: that's preceded by '<').
    private static final Pattern BARE_HEX = Pattern.compile("(?<![0-9A-Fa-f#<])#([0-9A-Fa-f]{6})(?![0-9A-Fa-f])");
    // &a / §a single-character legacy codes: colors, styles, and reset.
    private static final Pattern LEGACY_CODE = Pattern.compile("[&§]([0-9A-Fa-fK-Ok-oRr])");

    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset")
    );

    private ColorFormat() {
    }

    static String render(String raw) {
        Component component = MINI_MESSAGE.deserialize(normalize(raw));
        return LEGACY_OUT.serialize(component);
    }

    /** Escapes MiniMessage tag syntax in a value about to be substituted into a message, so it can never inject formatting. */
    static String escape(String value) {
        return value == null ? "" : MINI_MESSAGE.escapeTags(value);
    }

    private static String normalize(String raw) {
        String s = raw;
        s = PREFIXED_HEX.matcher(s).replaceAll(m -> "<#" + m.group(1) + ">");
        s = REPEATED_HEX.matcher(s).replaceAll(m -> "<#"
                + m.group(1) + m.group(2) + m.group(3) + m.group(4) + m.group(5) + m.group(6) + ">");
        s = BARE_HEX.matcher(s).replaceAll(m -> "<#" + m.group(1) + ">");
        s = LEGACY_CODE.matcher(s).replaceAll(m -> {
            String tag = LEGACY_TAGS.get(Character.toLowerCase(m.group(1).charAt(0)));
            return tag != null ? "<" + tag + ">" : m.group();
        });
        return s;
    }
}
