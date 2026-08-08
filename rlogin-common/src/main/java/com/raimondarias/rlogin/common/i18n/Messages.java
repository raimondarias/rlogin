package com.raimondarias.rlogin.common.i18n;

import com.raimondarias.rlogin.common.config.YamlDocument;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * rLogin messages, loaded from {@code <dataFolder>/messages_<lang>.yml}.
 * English is the primary/default language; every other bundled translation
 * (Spanish for now, more to come) is copied to disk on first run too, so an
 * admin can pick any of them via {@code general.language} and still edit it.
 *
 * <p>Returns text with Bukkit-style color codes ({@code &a}, already
 * converted to {@code §a}); each platform converts it to an Adventure
 * {@code Component} itself if it needs to.</p>
 */
public final class Messages {

    private static final String DEFAULT_LANGUAGE = "en";

    private final Map<String, String> flat;

    private Messages(Map<String, String> flat) {
        this.flat = flat;
    }

    public static Messages load(Path dataFolder, String language) {
        try {
            ensureBundled(dataFolder, "messages_es.yml");
            ensureBundled(dataFolder, "messages_en.yml");

            String resource = "messages_" + language + ".yml";
            Path file = dataFolder.resolve(resource);
            YamlDocument doc = Files.exists(file)
                    ? YamlDocument.loadOrCreate(file, resource)
                    : loadFallback(dataFolder);
            return new Messages(doc.flatten());
        } catch (IOException e) {
            throw new IllegalStateException("Could not load rLogin messages", e);
        }
    }

    private static YamlDocument loadFallback(Path dataFolder) throws IOException {
        Path fallback = dataFolder.resolve("messages_" + DEFAULT_LANGUAGE + ".yml");
        return Files.exists(fallback)
                ? YamlDocument.loadOrCreate(fallback, "messages_" + DEFAULT_LANGUAGE + ".yml")
                : YamlDocument.fromClasspath("messages_" + DEFAULT_LANGUAGE + ".yml");
    }

    private static void ensureBundled(Path dataFolder, String resource) throws IOException {
        Path target = dataFolder.resolve(resource);
        if (Files.notExists(target)) {
            Files.createDirectories(dataFolder);
            try (InputStream in = Messages.class.getClassLoader().getResourceAsStream(resource)) {
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
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return colorize(result);
    }

    public String get(String key) {
        return get(key, Map.of());
    }

    private static String colorize(String s) {
        return s.replace('&', '§');
    }
}
