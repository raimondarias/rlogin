package com.raimondarias.rlogin.common.i18n;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesTest {

    private static final List<String> ALL_BUNDLED_CODES = List.of(
            "en", "es", "pt_BR", "de", "fr", "ru", "zh_CN", "pl", "it",
            "nl", "tr", "uk", "ja", "ko", "ar", "vi", "id"
    );

    @Test
    void loadCreatesMessagesSubfolderWithEveryBundledLanguage(@TempDir Path dataFolder) {
        Messages.load(dataFolder, "en");

        Path messagesDir = dataFolder.resolve("messages");
        for (String code : ALL_BUNDLED_CODES) {
            assertTrue(Files.exists(messagesDir.resolve("lang_" + code + ".yml")), "missing lang_" + code + ".yml");
        }
    }

    @Test
    void customLanguageFileIsLoadedAsIsAndNeverOverwritten(@TempDir Path dataFolder) throws IOException {
        // "pt" (not the bundled "pt_BR") stands in for a language rLogin doesn't ship at all.
        Path messagesDir = dataFolder.resolve("messages");
        Files.createDirectories(messagesDir);
        Files.writeString(messagesDir.resolve("lang_pt.yml"), "prefix: \"\"\nlogin:\n  usage: \"Olá\"\n");

        Messages messages = Messages.load(dataFolder, "pt");

        assertTrue(messages.get("login.usage").contains("Olá"));
        assertTrue(Files.exists(messagesDir.resolve("lang_pt.yml"))); // untouched, not replaced by a bundled default
    }

    @Test
    void getRendersHexColorsAndSubstitutesPlaceholders(@TempDir Path dataFolder) {
        Messages messages = Messages.load(dataFolder, "en");

        String rendered = messages.get("login.wrong-password", Map.of("attempts", "3"));

        assertTrue(rendered.contains("3"));
        assertFalse(rendered.contains("{attempts}"));
        assertFalse(rendered.contains("&#")); // fully converted to § legacy output, no raw hex markup left over
    }

    @Test
    void unknownKeyFallsBackToTheKeyItself(@TempDir Path dataFolder) {
        Messages messages = Messages.load(dataFolder, "en");

        String rendered = messages.get("does.not.exist");

        assertTrue(rendered.contains("does.not.exist"));
    }

    @Test
    void everyBundledLanguageParsesAndRendersEveryKeyWithoutErrors(@TempDir Path dataFolder) {
        for (String code : ALL_BUNDLED_CODES) {
            Messages messages = Messages.load(dataFolder, code);

            // Spot-check every message family, including placeholder substitution — this would
            // throw (bad YAML, unbalanced MiniMessage/hex markup) if a translation was malformed.
            assertFalse(messages.get("login.wrong-password", Map.of("attempts", "2")).isBlank(), code);
            assertFalse(messages.get("register.password-too-short", Map.of("min", "5")).isBlank(), code);
            assertFalse(messages.get("security.locked-out", Map.of("seconds", "30")).isBlank(), code);
            assertFalse(messages.get("totp.setup-secret", Map.of("secret", "ABC123")).isBlank(), code);
            assertFalse(messages.get("admin.info", Map.of(
                    "player", "Steve", "premium", "yes", "totp", "no", "last-login", "2026-01-01")).isBlank(), code);
        }
    }
}
