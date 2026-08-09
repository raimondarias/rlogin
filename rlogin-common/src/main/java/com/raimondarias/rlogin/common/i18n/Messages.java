package com.raimondarias.rlogin.common.i18n;

import com.raimondarias.rlogin.common.config.YamlDocument;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * rLogin messages, loaded from {@code <dataFolder>/messages/lang_<code>.yml}.
 * English is the primary/default language; every other {@link #BUNDLED_LANGUAGES
 * bundled translation} is copied to disk on first run too, so an admin can
 * pick any of them via {@code general.language} and still edit it.
 *
 * <p>Fully custom languages work too: drop a {@code lang_<code>.yml} of your
 * own in the {@code messages/} folder (any code — it doesn't need to be one
 * rLogin ships) and point {@code general.language} at that same code.
 * {@link #load} only auto-creates the bundled files; any other file found on
 * disk is loaded as-is, never overwritten.</p>
 *
 * <p>Each message may freely mix MiniMessage tags, legacy {@code &}/{@code §}
 * codes, and hex colors ({@code &#RRGGBB}, {@code §x§R§R§G§G§B§B}, bare
 * {@code #RRGGBB}) — see {@link ColorFormat}. {@link #get(String, Map)}
 * always returns a plain legacy-formatted {@code String} (with hex support),
 * ready for {@code CommandSender#sendMessage(String)} on either platform.</p>
 */
public final class Messages {

    private static final String DEFAULT_LANGUAGE = "en";

    /** Every language rLogin ships a translation for out of the box. */
    private static final List<String> BUNDLED_LANGUAGES = List.of(
            "en", "es", "pt_BR", "de", "fr", "ru", "zh_CN", "pl", "it",
            "nl", "tr", "uk", "ja", "ko", "ar", "vi", "id"
    );

    private final Map<String, String> flat;

    private Messages(Map<String, String> flat) {
        this.flat = flat;
    }

    public static Messages load(Path dataFolder, String language) {
        try {
            Path messagesDir = dataFolder.resolve("messages");
            for (String code : BUNDLED_LANGUAGES) {
                ensureBundled(messagesDir, "lang_" + code + ".yml");
            }

            String resource = "lang_" + language + ".yml";
            Path file = messagesDir.resolve(resource);
            YamlDocument doc = Files.exists(file)
                    ? YamlDocument.loadOrCreate(file, "messages/" + resource)
                    : loadFallback(messagesDir);
            return new Messages(doc.flatten());
        } catch (IOException e) {
            throw new IllegalStateException("Could not load rLogin messages", e);
        }
    }

    private static YamlDocument loadFallback(Path messagesDir) throws IOException {
        Path fallback = messagesDir.resolve("lang_" + DEFAULT_LANGUAGE + ".yml");
        return Files.exists(fallback)
                ? YamlDocument.loadOrCreate(fallback, "messages/lang_" + DEFAULT_LANGUAGE + ".yml")
                : YamlDocument.fromClasspath("messages/lang_" + DEFAULT_LANGUAGE + ".yml");
    }

    private static void ensureBundled(Path messagesDir, String resource) throws IOException {
        Path target = messagesDir.resolve(resource);
        if (Files.notExists(target)) {
            Files.createDirectories(messagesDir);
            try (InputStream in = Messages.class.getClassLoader().getResourceAsStream("messages/" + resource)) {
                if (in != null) {
                    Files.copy(in, target);
                }
            }
        }
    }

    public String get(String key, Map<String, String> placeholders) {
        String raw = flat.getOrDefault(key, key);
        String prefix = flat.getOrDefault("prefix", "");
        String result = raw.contains("{prefix}") ? raw.replace("{prefix}", prefix) : raw;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", ColorFormat.escape(entry.getValue()));
            }
        }
        return ColorFormat.render(result);
    }

    public String get(String key) {
        return get(key, Map.of());
    }
}
