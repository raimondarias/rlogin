package com.raimondarias.rlogin.common.db;

import com.zaxxer.hikari.HikariConfig;

import java.nio.file.Path;

/**
 * SQLite storage: zero configuration, great for a single server. A single
 * file at {@code <dataFolder>/<database.sqlite.file>}.
 */
public final class SqliteStorage extends AbstractSqlStorage {

    private final Path dbFile;

    public SqliteStorage(Path dbFile) {
        this.dbFile = dbFile;
    }

    @Override
    protected HikariConfig buildHikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        // SQLite only allows one writer at a time; a pool size of 1 avoids
        // "database is locked" errors under concurrency.
        config.setMaximumPoolSize(1);
        config.setPoolName("rlogin-sqlite");
        config.setConnectionTestQuery("SELECT 1");
        return config;
    }

    @Override
    protected String createAccountsTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS rlogin_accounts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid VARCHAR(36) NOT NULL UNIQUE,
                    username VARCHAR(16) NOT NULL,
                    premium INTEGER NOT NULL DEFAULT 0,
                    password_hash VARCHAR(255),
                    hash_algo VARCHAR(20),
                    totp_secret VARCHAR(64),
                    totp_enabled INTEGER NOT NULL DEFAULT 0,
                    last_ip VARCHAR(45),
                    last_login_at BIGINT,
                    registered_at BIGINT,
                    failed_attempts INTEGER NOT NULL DEFAULT 0,
                    locked_until BIGINT
                )
                """;
    }

    @Override
    protected String createSessionsTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS rlogin_sessions (
                    uuid VARCHAR(36) NOT NULL,
                    ip VARCHAR(45) NOT NULL,
                    server VARCHAR(64),
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    PRIMARY KEY (uuid, ip)
                )
                """;
    }

    @Override
    protected String createRecoveryCodesTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS rlogin_recovery_codes (
                    uuid VARCHAR(36) NOT NULL,
                    code_hash VARCHAR(255) NOT NULL,
                    used_at BIGINT,
                    PRIMARY KEY (uuid, code_hash)
                )
                """;
    }
}
