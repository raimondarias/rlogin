package com.raimondarias.rlogin.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {

    private static final String RESOURCE = "default-config.yml";

    @Test
    void addsNewSettingsWithoutTouchingTheOnesAlreadyThere(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.yml");
        // An old config: two settings, both deliberately not the default.
        Files.writeString(file, """
                premium:
                  protect-premium-names: false
                security:
                  bruteforce:
                    max-attempts: 3
                """, StandardCharsets.UTF_8);

        List<String> added = ConfigMigrator.migrate(file, RESOURCE);

        assertFalse(added.isEmpty(), "a config missing most of the file should gain settings");
        Map<String, Object> merged = parse(file);
        assertEquals(false, nested(merged, "premium", "protect-premium-names"),
                "the admin's own value must survive the merge");
        assertEquals(3, nested(merged, "security", "bruteforce", "max-attempts"));
        assertEquals("real", nested(merged, "premium", "uuid-type"), "new settings arrive with their default");
    }

    @Test
    void writesSectionsThatTheOldFileNeverHad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, "premium:\n  auto-login: true\n", StandardCharsets.UTF_8);

        ConfigMigrator.migrate(file, RESOURCE);

        // A nested key whose whole section was absent is the case that produces invalid
        // YAML if the section header isn't written first.
        Map<String, Object> merged = parse(file);
        assertEquals("sqlite", nested(merged, "database", "type"));
        assertEquals(10, nested(merged, "security", "password", "bcrypt-cost"));
    }

    @Test
    void keepsListsIntact(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, "premium:\n  auto-login: true\n", StandardCharsets.UTF_8);

        ConfigMigrator.migrate(file, RESOURCE);

        Object commands = nested(parse(file), "limbo", "allowed-commands");
        assertTrue(commands instanceof List<?> list && list.contains("/login"),
                "a list setting has to arrive with its items, not as an empty key: " + commands);
    }

    @Test
    void carriesTheExplanationAcrossToo(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, "premium:\n  auto-login: true\n", StandardCharsets.UTF_8);

        ConfigMigrator.migrate(file, RESOURCE);

        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.contains("#"), "settings are useless without the comments that explain them");
    }

    @Test
    void doesNothingToAFileThatIsAlreadyCurrent(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.yml");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            Files.copy(in, file);
        }
        String before = Files.readString(file, StandardCharsets.UTF_8);

        List<String> added = ConfigMigrator.migrate(file, RESOURCE);

        assertTrue(added.isEmpty(), "nothing to add: " + added);
        assertEquals(before, Files.readString(file, StandardCharsets.UTF_8),
                "an up-to-date file must not be rewritten at all");
    }

    @Test
    void leavesAMissingFileToWhoeverCreatesIt(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.yml");

        assertTrue(ConfigMigrator.migrate(file, RESOURCE).isEmpty());
        assertFalse(Files.exists(file), "migrating must never create the file itself");
    }

    private static Map<String, Object> parse(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object nested(Map<String, Object> root, String... path) {
        Object current = root;
        for (String key : path) {
            current = ((Map<String, Object>) current).get(key);
        }
        return current;
    }
}
