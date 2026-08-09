package com.raimondarias.rlogin.common.db;

import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.zaxxer.hikari.HikariConfig;

/**
 * MySQL/MariaDB storage, recommended when several Paper/Folia backends
 * need to share the same rLogin accounts.
 */
public final class MysqlStorage extends AbstractSqlStorage {

    private final RLoginConfig config;

    public MysqlStorage(RLoginConfig config) {
        this.config = config;
    }

    @Override
    protected HikariConfig buildHikariConfig() {
        HikariConfig hikari = new HikariConfig();
        String url = "jdbc:mysql://%s:%d/%s?useSSL=%s&autoReconnect=true&characterEncoding=utf8"
                .formatted(config.mysqlHost(), config.mysqlPort(), config.mysqlDatabase(), config.mysqlUseSsl());
        hikari.setJdbcUrl(url);
        hikari.setUsername(config.mysqlUsername());
        hikari.setPassword(config.mysqlPassword());
        hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikari.setMaximumPoolSize(Math.max(2, config.mysqlPoolSize()));
        hikari.setPoolName("rlogin-mysql");
        hikari.setConnectionTestQuery("SELECT 1");
        return hikari;
    }

    @Override
    protected String createAccountsTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS rlogin_accounts (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL UNIQUE,
                    username VARCHAR(16) NOT NULL,
                    premium BOOLEAN NOT NULL DEFAULT FALSE,
                    password_hash VARCHAR(255),
                    hash_algo VARCHAR(20),
                    totp_secret VARCHAR(64),
                    totp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                    last_ip VARCHAR(45),
                    last_login_at BIGINT,
                    registered_at BIGINT,
                    failed_attempts INT NOT NULL DEFAULT 0,
                    locked_until BIGINT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
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
