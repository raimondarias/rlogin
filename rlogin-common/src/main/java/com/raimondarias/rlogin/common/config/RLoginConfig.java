package com.raimondarias.rlogin.common.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Configuración de rLogin, común a Velocity y Paper/Folia. Se carga desde
 * {@code <dataFolder>/config.yml}, creado a partir de {@code default-config.yml}
 * la primera vez.
 */
public final class RLoginConfig {

    private final YamlDocument doc;

    private RLoginConfig(YamlDocument doc) {
        this.doc = doc;
    }

    public static RLoginConfig load(Path dataFolder) throws IOException {
        Path file = dataFolder.resolve("config.yml");
        return new RLoginConfig(YamlDocument.loadOrCreate(file, "default-config.yml"));
    }

    // --- general ---
    public String language() {
        return doc.getString("general.language", "es");
    }

    public boolean debug() {
        return doc.getBoolean("general.debug", false);
    }

    // --- database ---
    public String databaseType() {
        return doc.getString("database.type", "sqlite");
    }

    public String sqliteFile() {
        return doc.getString("database.sqlite.file", "rlogin.db");
    }

    public String mysqlHost() {
        return doc.getString("database.mysql.host", "127.0.0.1");
    }

    public int mysqlPort() {
        return doc.getInt("database.mysql.port", 3306);
    }

    public String mysqlDatabase() {
        return doc.getString("database.mysql.database", "rlogin");
    }

    public String mysqlUsername() {
        return doc.getString("database.mysql.username", "root");
    }

    public String mysqlPassword() {
        return doc.getString("database.mysql.password", "");
    }

    public boolean mysqlUseSsl() {
        return doc.getBoolean("database.mysql.use-ssl", false);
    }

    public int mysqlPoolSize() {
        return doc.getInt("database.mysql.pool-size", 10);
    }

    // --- premium ---
    public boolean premiumAutoLogin() {
        return doc.getBoolean("premium.auto-login", true);
    }

    public int premiumApiTimeoutMs() {
        return doc.getInt("premium.api-timeout-ms", 3000);
    }

    /** {@code fail-open} (tratar como cracked) o {@code fail-closed} (rechazar). */
    public boolean premiumApiFailOpen() {
        return !doc.getString("premium.api-failure-policy", "fail-open").equalsIgnoreCase("fail-closed");
    }

    public int premiumCacheTtlMinutes() {
        return doc.getInt("premium.cache-ttl-minutes", 60);
    }

    public boolean protectPremiumNames() {
        return doc.getBoolean("premium.protect-premium-names", true);
    }

    // --- session ---
    public boolean rememberMeEnabled() {
        return doc.getBoolean("session.remember-me", true);
    }

    public int rememberMeMinutes() {
        return doc.getInt("session.remember-me-minutes", 30);
    }

    // --- security ---
    public boolean bruteforceEnabled() {
        return doc.getBoolean("security.bruteforce.enabled", true);
    }

    public int bruteforceMaxAttempts() {
        return doc.getInt("security.bruteforce.max-attempts", 5);
    }

    public int bruteforceLockoutSeconds() {
        return doc.getInt("security.bruteforce.lockout-seconds", 60);
    }

    public double bruteforceLockoutMultiplier() {
        return doc.getDouble("security.bruteforce.lockout-multiplier", 2.0);
    }

    public int bruteforceMaxLockoutSeconds() {
        return doc.getInt("security.bruteforce.max-lockout-seconds", 3600);
    }

    public boolean totpEnabled() {
        return doc.getBoolean("security.totp.enabled", true);
    }

    public String totpIssuer() {
        return doc.getString("security.totp.issuer", "rLogin");
    }

    public int passwordMinLength() {
        return doc.getInt("security.password.min-length", 5);
    }

    public int passwordMaxLength() {
        return doc.getInt("security.password.max-length", 30);
    }

    public int bcryptCost() {
        return doc.getInt("security.password.bcrypt-cost", 10);
    }

    // --- limbo ---
    public boolean limboFreeze() {
        return doc.getBoolean("limbo.freeze", true);
    }

    public List<String> limboAllowedCommands() {
        return doc.getStringList("limbo.allowed-commands", List.of("/login", "/register"));
    }

    public int limboReminderIntervalSeconds() {
        return doc.getInt("limbo.reminder-interval-seconds", 5);
    }

    // --- bedrock ---
    public boolean floodgateAutoLogin() {
        return doc.getBoolean("bedrock.floodgate-auto-login", true);
    }

    public String floodgatePrefix() {
        return doc.getString("bedrock.prefix", ".");
    }

    // --- misc ---
    public boolean updateCheckerEnabled() {
        return doc.getBoolean("update-checker.enabled", true);
    }

    public boolean bstatsEnabled() {
        return doc.getBoolean("metrics.bstats", true);
    }
}
