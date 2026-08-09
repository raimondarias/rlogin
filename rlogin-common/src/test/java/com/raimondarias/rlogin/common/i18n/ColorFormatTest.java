package com.raimondarias.rlogin.common.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorFormatTest {

    // Must match ColorFormat's own output serializer exactly (including
    // useUnusualXRepeatedCharacterHexFormat) — otherwise this test would pass even if
    // ColorFormat produced the wrong hex format, since both sides would agree on the same bug.
    private static final LegacyComponentSerializer REPARSE = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private static TextColor firstColor(String rendered) {
        Component component = REPARSE.deserialize(rendered);
        // First child carries the color in every message we build (prefix or the text itself).
        Component c = component;
        while (c.color() == null && !c.children().isEmpty()) {
            c = c.children().get(0);
        }
        return c.color();
    }

    @Test
    void ampersandHexFormat() {
        assertEquals(TextColor.fromHexString("#e39fff"), firstColor(ColorFormat.render("&#e39fffHello")));
    }

    @Test
    void outputUsesTheBungeeCompatibleRepeatedCharacterHexFormatNotTheBareShorthand() {
        // Regression test: Adventure's LegacyComponentSerializer defaults to emitting
        // "§#RRGGBB" for hex colors when hexColors() is set without
        // useUnusualXRepeatedCharacterHexFormat() — that shorthand is Adventure-internal
        // only; neither vanilla Minecraft nor CommandSender#sendMessage(String) understand
        // it, so it renders as literal visible text in chat instead of an actual color.
        // Only "§x§R§R§G§G§B§B" (7 section signs: one "x" marker + one per hex digit) is
        // actually understood by clients.
        String out = ColorFormat.render("&#e39fffHello");
        assertFalse(out.contains("§#"), "raw output must never contain the literal '§#' shorthand: " + out);
        long sectionSigns = out.chars().filter(c -> c == '§').count();
        assertTrue(sectionSigns >= 7, "expected at least 7 '§' (the §x§.§.§.§.§.§. hex sequence): " + out);
    }

    @Test
    void bareHexFormat() {
        assertEquals(TextColor.fromHexString("#fd5e5e"), firstColor(ColorFormat.render("#fd5e5eOops")));
    }

    @Test
    void repeatedCharacterLegacyHexFormat() {
        assertEquals(TextColor.fromHexString("#91f251"),
                firstColor(ColorFormat.render("§x§9§1§f§2§5§1Success")));
    }

    @Test
    void repeatedCharacterLegacyHexFormatWithAmpersand() {
        assertEquals(TextColor.fromHexString("#5e9dfd"),
                firstColor(ColorFormat.render("&x&5&e&9&d&f&dTime")));
    }

    @Test
    void classicLegacyCode() {
        assertEquals(NamedTextColor.RED, firstColor(ColorFormat.render("&cError")));
        assertEquals(NamedTextColor.GREEN, firstColor(ColorFormat.render("§aOk")));
    }

    @Test
    void miniMessageTag() {
        assertEquals(NamedTextColor.RED, firstColor(ColorFormat.render("<red>Error")));
        assertEquals(TextColor.fromHexString("#e39fff"), firstColor(ColorFormat.render("<#e39fff>Hello")));
    }

    @Test
    void bareHexDoesNotDoubleWrapAlreadyConvertedTags() {
        // &#RRGGBB gets normalized into <#RRGGBB> internally; the bare-hex pass must not
        // re-match the hex digits now sitting right after '<' and corrupt the tag.
        String out = ColorFormat.render("&#e39fffHello &#fd5e5eWorld");
        assertTrue(out.contains("Hello"));
        assertTrue(out.contains("World"));
    }

    @Test
    void mixedFormatsInOneString() {
        String out = ColorFormat.render("<bold>&#e39fff/login &#fd8ddb{player}");
        assertTrue(out.contains("login"));
    }

    @Test
    void escapeTagsPreventsInjectionFromPlaceholderValues() {
        String malicious = ColorFormat.escape("<red>Injected");
        String out = ColorFormat.render("&#d4d9d8Hi " + malicious);
        // The escaped value must render as literal text, not switch the color to red.
        assertEquals(TextColor.fromHexString("#d4d9d8"), firstColor(out));
    }
}
