package com.raimondarias.rlogin.common.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Very thin wrapper over a YAML document loaded with SnakeYAML, with
 * dot-separated path access (e.g. {@code "database.mysql.host"}). Not an
 * ORM or object mapper on purpose: rLogin only ever needs to read config
 * and messages, nothing more.
 */
public final class YamlDocument {

    private final Map<String, Object> root;

    private YamlDocument(Map<String, Object> root) {
        this.root = root;
    }

    /** Loads the file if it exists; otherwise creates it from the bundled resource. */
    public static YamlDocument loadOrCreate(Path file, String classpathResource) throws IOException {
        if (Files.notExists(file)) {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (InputStream in = YamlDocument.class.getClassLoader().getResourceAsStream(classpathResource)) {
                if (in == null) {
                    throw new IOException("Bundled resource not found: " + classpathResource);
                }
                Files.copy(in, file);
            }
        }
        try (InputStream in = Files.newInputStream(file)) {
            return new YamlDocument(readMap(in));
        }
    }

    /**
     * Reads a file that already exists and is not rLogin's to create — the
     * server's own {@code spigot.yml} and {@code paper-global.yml}, read to
     * find out whether a proxy is in front. Never writes anything.
     */
    public static YamlDocument read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return new YamlDocument(readMap(in));
        }
    }

    public static YamlDocument fromClasspath(String classpathResource) throws IOException {
        try (InputStream in = YamlDocument.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IOException("Bundled resource not found: " + classpathResource);
            }
            return new YamlDocument(readMap(in));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readMap(InputStream in) {
        Object loaded = new Yaml().load(in);
        return loaded instanceof Map ? (Map<String, Object>) loaded : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Object resolve(String path) {
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(part);
        }
        return current;
    }

    public String getString(String path, String def) {
        Object v = resolve(path);
        return v != null ? String.valueOf(v) : def;
    }

    public int getInt(String path, int def) {
        Object v = resolve(path);
        return v instanceof Number n ? n.intValue() : def;
    }

    public long getLong(String path, long def) {
        Object v = resolve(path);
        return v instanceof Number n ? n.longValue() : def;
    }

    public double getDouble(String path, double def) {
        Object v = resolve(path);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    public boolean getBoolean(String path, boolean def) {
        Object v = resolve(path);
        return v instanceof Boolean b ? b : def;
    }

    public List<String> getStringList(String path, List<String> def) {
        Object v = resolve(path);
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                out.add(String.valueOf(o));
            }
            return out;
        }
        return def;
    }

    /** Flattens the document to dot-separated keys -> text value (used for messages). */
    public Map<String, String> flatten() {
        Map<String, String> out = new LinkedHashMap<>();
        flattenInto("", root, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private void flattenInto(String prefix, Map<String, Object> map, Map<String, String> out) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flattenInto(key, (Map<String, Object>) nested, out);
            } else if (value != null) {
                out.put(key, String.valueOf(value));
            }
        }
    }
}
