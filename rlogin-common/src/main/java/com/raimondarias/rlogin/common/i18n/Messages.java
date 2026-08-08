package com.raimondarias.rlogin.common.i18n;

import com.raimondarias.rlogin.common.config.YamlDocument;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Mensajes de rLogin, cargados desde {@code <dataFolder>/messages_<lang>.yml}.
 * Ambos idiomas embebidos (es/en) se copian a disco la primera vez para que
 * el administrador pueda editarlos.
 *
 * <p>Devuelve texto con códigos de color estilo Bukkit ({@code &a}, ya
 * convertidos a {@code §a}); cada plataforma se encarga de convertirlo a un
 * {@code Component} de Adventure si lo necesita.</p>
 */
public final class Messages {

    private static final String DEFAULT_LANGUAGE = "es";

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
            throw new IllegalStateException("No se pudieron cargar los mensajes de rLogin", e);
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
