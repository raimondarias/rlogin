package com.raimondarias.rlogin.common.config;

import com.raimondarias.rlogin.common.auth.AuthMode;
import com.raimondarias.rlogin.common.auth.UuidType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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

    /**
     * Loads {@code <dataFolder>/config.yml}, creating it the first time and
     * bringing it up to date on every later run.
     *
     * @param addedSettings collects the settings this version introduced that
     *                      the file didn't have yet, so the caller can say so
     *                      in the console. An upgrade that silently changes
     *                      what a server does is worse than one that mentions it.
     */
    public static RLoginConfig load(Path dataFolder, String bundledResource, List<String> addedSettings)
            throws IOException {
        Path file = dataFolder.resolve("config.yml");
        YamlDocument doc = YamlDocument.loadOrCreate(file, bundledResource);
        addedSettings.addAll(ConfigMigrator.migrate(file, bundledResource));
        return addedSettings.isEmpty() ? new RLoginConfig(doc)
                : new RLoginConfig(YamlDocument.read(file)); // Re-read so the new keys are live now.
    }

    /** Loads (creating if missing) {@code <dataFolder>/config.yml} from the given bundled resource. */
    public static RLoginConfig load(Path dataFolder, String bundledResource) throws IOException {
        return load(dataFolder, bundledResource, new ArrayList<>());
    }

    /**
     * Who is allowed on this server; see {@link AuthMode}. Top-level and
     * first in the file on purpose: it is the one decision an admin has to
     * make consciously, and everything else has a sensible default.
     */
    public AuthMode authMode() {
        return AuthMode.parse(doc.getString("general.auth-mode", "auto"));
    }

    // --- general ---
    public String language() {
        return doc.getString("general.language", "en");
    }

    public boolean debug() {
        return doc.getBoolean("general.debug", false);
    }

    // --- database (always on for Paper/Folia; optional opt-in for Velocity, see velocity-config.yml) ---
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

    /** A max of 0 turns the registration cap off entirely. */
    public boolean registrationLimitEnabled() {
        return registrationsMaxPerIp() > 0;
    }

    public int registrationsMaxPerIp() {
        return doc.getInt("security.registration.max-per-ip", 3);
    }

    public int registrationsWindowMinutes() {
        return doc.getInt("security.registration.window-minutes", 60);
    }

    public boolean recoveryCodesEnabled() {
        return doc.getBoolean("security.recovery.enabled", true);
    }

    public int recoveryCodeCount() {
        return doc.getInt("security.recovery.codes", 5);
    }

    public boolean rejectCommonPasswords() {
        return doc.getBoolean("security.password.reject-common", true);
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

    public int limboLoginTimeoutSeconds() {
        return doc.getInt("limbo.login-timeout-seconds", 60);
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

    // --- backend + redirect (Velocity only) ---
    //
    // The proxy's whole configuration. Everything else about rLogin — passwords,
    // 2FA, sessions, spawns, languages, UUIDs — belongs to the backends, and the
    // proxy deliberately knows nothing about any of it.

    /** Servers running rLogin, i.e. the ones that can ask a player to log in. */
    public List<String> loginServers() {
        return doc.getStringList("login-servers.servers", List.of());
    }

    /** Whether to overrule Velocity's own first-server choice so nobody reaches a server before logging in. */
    public boolean enforceLoginServers() {
        return doc.getBoolean("login-servers.enforce", true);
    }

    /** What to do with a player once a backend reports them logged in. */
    public AfterLogin afterLoginAction() {
        return AfterLogin.parse(doc.getString("after-login.action", "stay"));
    }

    /** Where {@link AfterLogin#SEND} sends them; one is picked at random. */
    public List<String> afterLoginServers() {
        return doc.getStringList("after-login.servers", List.of());
    }

    /** Servers {@link AfterLogin#PREVIOUS} should never return anyone to. */
    public List<String> neverReturnTo() {
        return doc.getStringList("after-login.never-return-to", List.of());
    }

    /** Pause before moving a player between servers, so the switch never lands mid-handshake. */
    public int switchDelayMs() {
        return doc.getInt("timing.switch-delay", 500);
    }

    /** Pause before retrying a server switch that failed. */
    public int retryDelayMs() {
        return doc.getInt("timing.retry-delay", 5000);
    }

    // --- misc ---
    public boolean updateCheckerEnabled() {
        return doc.getBoolean("update-checker.enabled", true);
    }

    public boolean bstatsEnabled() {
        return doc.getBoolean("metrics.bstats", true);
    }
}
