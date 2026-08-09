package com.raimondarias.rlogin.common.config;

import com.raimondarias.rlogin.common.auth.UuidType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * rLogin configuration. Loaded from {@code <dataFolder>/config.yml}, created
 * from a bundled default resource the first time.
 *
 * <p>Paper and Velocity ship <em>different</em> default config files
 * (Paper's has database/security/session/limbo/bedrock settings; Velocity's
 * only has what the proxy actually needs, plus the auth-lobby settings) —
 * see {@link #load(Path)} vs {@link #load(Path, String)}. Both are read
 * through this same class since the underlying YAML access is identical;
 * unknown keys are simply ignored, so each platform only sees the sections
 * relevant to it in its own file.</p>
 */
public final class RLoginConfig {

    private final YamlDocument doc;

    private RLoginConfig(YamlDocument doc) {
        this.doc = doc;
    }

    /** Paper/Folia default: {@code default-config.yml}. */
    public static RLoginConfig load(Path dataFolder) throws IOException {
        return load(dataFolder, "default-config.yml");
    }

    /** Loads (creating if missing) {@code <dataFolder>/config.yml} from the given bundled resource. */
    public static RLoginConfig load(Path dataFolder, String bundledResource) throws IOException {
        Path file = dataFolder.resolve("config.yml");
        return new RLoginConfig(YamlDocument.loadOrCreate(file, bundledResource));
    }

    // --- general ---
    public String language() {
        return doc.getString("general.language", "en");
    }

    public boolean debug() {
        return doc.getBoolean("general.debug", false);
    }

    // --- database (always on for Paper/Folia; optional opt-in for Velocity, see velocity-config.yml) ---
    public boolean databaseEnabled() {
        return doc.getBoolean("database.enabled", true);
    }

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

    // --- premium (shared: Paper standalone fallback + Velocity's PreLoginEvent decision) ---
    public boolean premiumAutoLogin() {
        return doc.getBoolean("premium.auto-login", true);
    }

    public int premiumApiTimeoutMs() {
        return doc.getInt("premium.api-timeout-ms", 3000);
    }

    /** {@code fail-open} (treat as cracked) or {@code fail-closed} (reject the connection). */
    public boolean premiumApiFailOpen() {
        return !doc.getString("premium.api-failure-policy", "fail-open").equalsIgnoreCase("fail-closed");
    }

    public int premiumCacheTtlMinutes() {
        return doc.getInt("premium.cache-ttl-minutes", 60);
    }

    public boolean protectPremiumNames() {
        return doc.getBoolean("premium.protect-premium-names", true);
    }

    /**
     * Standalone hybrid mode (Paper/Folia only, no Velocity needed): premium
     * accounts still auto-login and cracked accounts still get
     * /login-/register even on a single online-mode:false backend with no
     * proxy in front. Requires the separate PacketEvents plugin to be
     * installed — silently does nothing without it (fail-closed), see
     * PacketEventsSupport. Off by default: it's newer and more advanced than
     * the Velocity-based path, which remains the recommended default for
     * production networks.
     */
    public boolean standaloneHybridModeEnabled() {
        return doc.getBoolean("premium.standalone-hybrid-mode", false);
    }

    /** Which UUID connecting players end up with; see {@link UuidType}. Defaults to {@code real}. */
    public UuidType uuidType() {
        return UuidType.parse(doc.getString("premium.uuid-type", "real"));
    }

    // --- session (Paper/Folia only) ---
    public boolean rememberMeEnabled() {
        return doc.getBoolean("session.remember-me", true);
    }

    public int rememberMeMinutes() {
        return doc.getInt("session.remember-me-minutes", 30);
    }

    // --- security (Paper/Folia only) ---
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

    // --- limbo (Paper/Folia only) ---
    public boolean limboFreeze() {
        return doc.getBoolean("limbo.freeze", true);
    }

    public List<String> limboAllowedCommands() {
        return doc.getStringList("limbo.allowed-commands", List.of("/login", "/register"));
    }

    public int limboReminderIntervalSeconds() {
        return doc.getInt("limbo.reminder-interval-seconds", 5);
    }

    // --- bedrock (Paper/Folia only) ---
    public boolean floodgateAutoLogin() {
        return doc.getBoolean("bedrock.floodgate-auto-login", true);
    }

    public String floodgatePrefix() {
        return doc.getString("bedrock.prefix", ".");
    }

    // --- lobby (Velocity only) ---

    /** Backend name (from velocity.toml) every not-yet-authenticated player is routed to first. Empty = disabled. */
    public String authLobbyServer() {
        return doc.getString("lobby.auth-server", "");
    }

    /** Backend name authenticated players get sent/transferred to. Empty = disabled (respect velocity.toml's try order). */
    public String defaultLobbyServer() {
        return doc.getString("lobby.default-server", "");
    }

    // --- misc ---
    public boolean updateCheckerEnabled() {
        return doc.getBoolean("update-checker.enabled", true);
    }

    public boolean bstatsEnabled() {
        return doc.getBoolean("metrics.bstats", true);
    }
}
