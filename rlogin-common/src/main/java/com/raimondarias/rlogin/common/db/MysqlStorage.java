package com.raimondarias.rlogin.common.db;

import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.zaxxer.hikari.HikariConfig;

import java.sql.SQLException;
import java.sql.Statement;

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

    /**
     * MySQL has no {@code CREATE INDEX IF NOT EXISTS} (that is MariaDB-only
     * syntax), so the index is created best-effort and a duplicate-key error
     * (1061) is treated as "already there". The index is functional on
     * {@code LOWER(username)}, which is what {@code findByUsername} actually
     * filters on — a plain index on the column would never be used.
     */
    @Override
    protected void createUsernameIndex(Statement st) throws SQLException {
        try {
            st.execute("CREATE INDEX idx_rlogin_accounts_username "
                    + "ON rlogin_accounts ((LOWER(username)))");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1061) { // 1061 = Duplicate key name
                throw e;
            }
        }
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
                    token_hash VARCHAR(64),
                    PRIMARY KEY (uuid, ip)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
    }

    @Override
    protected String createLoginFailuresTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS rlogin_login_failures (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    ip VARCHAR(45) NOT NULL,
                    username VARCHAR(16) NOT NULL,
                    attempted_at BIGINT NOT NULL,
                    INDEX idx_failures_ip (ip, attempted_at),
                    INDEX idx_failures_name (username, attempted_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
    }

    @Override
    protected String createKnownIpsTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS rlogin_known_ips (
                    uuid VARCHAR(36) NOT NULL,
                    ip VARCHAR(45) NOT NULL,
                    first_seen BIGINT NOT NULL,
                    last_seen BIGINT NOT NULL,
                    PRIMARY KEY (uuid, ip)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
    }

    @Override
    protected String createTransferTokensTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS rlogin_transfer_tokens (
                    uuid VARCHAR(36) NOT NULL,
                    token_hash VARCHAR(64) NOT NULL,
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    INDEX idx_transfer_uuid (uuid),
                    INDEX idx_transfer_expiry (expires_at)
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
