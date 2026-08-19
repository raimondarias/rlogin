package com.raimondarias.rlogin.common.db;

import com.raimondarias.rlogin.api.RLoginAccount;
import com.raimondarias.rlogin.api.db.Storage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * JDBC logic shared between {@link SqliteStorage} and {@link MysqlStorage}.
 * Subclasses only contribute the HikariCP configuration and the table
 * creation DDL (which differs slightly between SQLite and MySQL); everything
 * else (SELECT/INSERT/UPDATE) is standard SQL valid on both engines.
 *
 * <p>Every operation runs on its own thread pool: never on Bukkit's
 * main/region thread, nor on Velocity's event loop.</p>
 */
public abstract class AbstractSqlStorage implements Storage {

    protected final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "rlogin-db");
        t.setDaemon(true);
        return t;
    });

    protected volatile HikariDataSource dataSource;

    protected abstract HikariConfig buildHikariConfig();

    protected abstract String createAccountsTableSql();

    protected abstract String createSessionsTableSql();

    protected abstract String createRecoveryCodesTableSql();

    /**
     * Creates the index that backs {@link #findByUsername} (which filters on
     * {@code LOWER(username)}, so the index must be functional on that
     * expression to be of any use). Engine-specific: MySQL has no
     * {@code IF NOT EXISTS} for indexes, SQLite does.
     */
    protected abstract void createUsernameIndex(Statement st) throws SQLException;

    @Override
    public CompletableFuture<Void> init() {
        return CompletableFuture.runAsync(() -> {
            dataSource = new HikariDataSource(buildHikariConfig());
            try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
                st.execute(createAccountsTableSql());
                st.execute(createSessionsTableSql());
                st.execute(createRecoveryCodesTableSql());
                createUsernameIndex(st);
            } catch (SQLException e) {
                throw new RuntimeException("Could not initialise rLogin's database", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<RLoginAccount>> findByUuid(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM rlogin_accounts WHERE uuid = ?";
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<RLoginAccount>empty();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<RLoginAccount>> findByUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM rlogin_accounts WHERE LOWER(username) = LOWER(?)";
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<RLoginAccount>empty();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<RLoginAccount> save(RLoginAccount account) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                boolean exists;
                try (PreparedStatement check = c.prepareStatement("SELECT 1 FROM rlogin_accounts WHERE uuid = ?")) {
                    check.setString(1, account.uuid().toString());
                    try (ResultSet rs = check.executeQuery()) {
                        exists = rs.next();
                    }
                }
                if (exists) {
                    update(c, account);
                } else {
                    insert(c, account);
                }
                return account;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private void insert(Connection c, RLoginAccount a) throws SQLException {
        String sql = """
                INSERT INTO rlogin_accounts
                (uuid, username, premium, password_hash, hash_algo, totp_secret, totp_enabled,
                 last_ip, last_login_at, registered_at, failed_attempts, locked_until)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, a, true);
            ps.executeUpdate();
        }
    }

    private void update(Connection c, RLoginAccount a) throws SQLException {
        String sql = """
                UPDATE rlogin_accounts SET
                username = ?, premium = ?, password_hash = ?, hash_algo = ?, totp_secret = ?,
                totp_enabled = ?, last_ip = ?, last_login_at = ?, registered_at = ?,
                failed_attempts = ?, locked_until = ? WHERE uuid = ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, a, false);
            ps.executeUpdate();
        }
    }

    private void bind(PreparedStatement ps, RLoginAccount a, boolean insert) throws SQLException {
        int i = 1;
        if (insert) {
            ps.setString(i++, a.uuid().toString());
        }
        ps.setString(i++, a.username());
        ps.setBoolean(i++, a.premium());
        ps.setString(i++, a.passwordHash());
        ps.setString(i++, a.hashAlgo());
        ps.setString(i++, a.totpSecret());
        ps.setBoolean(i++, a.totpEnabled());
        ps.setString(i++, a.lastIp());
        setNullableEpoch(ps, i++, a.lastLoginAt());
        setNullableEpoch(ps, i++, a.registeredAt());
        ps.setInt(i++, a.failedAttempts());
        setNullableEpoch(ps, i++, a.lockedUntil());
        if (!insert) {
            ps.setString(i, a.uuid().toString());
        }
    }

    private void setNullableEpoch(PreparedStatement ps, int index, Instant instant) throws SQLException {
        if (instant == null) {
            ps.setNull(index, java.sql.Types.BIGINT);
        } else {
            ps.setLong(index, instant.toEpochMilli());
        }
    }

    @Override
    public CompletableFuture<Void> delete(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM rlogin_accounts WHERE uuid = ?");
                 PreparedStatement codes = c.prepareStatement("DELETE FROM rlogin_recovery_codes WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
                // Nothing links the tables, so the codes would outlive the account and
                // still open a door into whatever is registered under that UUID next.
                codes.setString(1, uuid.toString());
                codes.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> saveSession(UUID uuid, String ip, String server, Instant expiresAt) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM rlogin_sessions WHERE uuid = ? AND ip = ?")) {
                    del.setString(1, uuid.toString());
                    del.setString(2, ip);
                    del.executeUpdate();
                }
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO rlogin_sessions (uuid, ip, server, created_at, expires_at) VALUES (?, ?, ?, ?, ?)")) {
                    ins.setString(1, uuid.toString());
                    ins.setString(2, ip);
                    ins.setString(3, server);
                    ins.setLong(4, Instant.now().toEpochMilli());
                    ins.setLong(5, expiresAt.toEpochMilli());
                    ins.executeUpdate();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> hasValidSession(UUID uuid, String ip, Instant now) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM rlogin_sessions WHERE uuid = ? AND ip = ? AND expires_at > ?";
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, ip);
                ps.setLong(3, now.toEpochMilli());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> replaceRecoveryCodes(UUID uuid, List<String> hashes) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                // Replaced as a set: issuing a new batch has to retire the old one, or a
                // player who regenerates their codes leaves the previous list still valid.
                try (PreparedStatement clear =
                             c.prepareStatement("DELETE FROM rlogin_recovery_codes WHERE uuid = ?")) {
                    clear.setString(1, uuid.toString());
                    clear.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO rlogin_recovery_codes (uuid, code_hash, used_at) VALUES (?, ?, NULL)")) {
                    for (String hash : hashes) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, hash);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<String>> unusedRecoveryCodeHashes(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT code_hash FROM rlogin_recovery_codes WHERE uuid = ? AND used_at IS NULL")) {
                ps.setString(1, uuid.toString());
                List<String> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getString("code_hash"));
                    }
                }
                return out;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> consumeRecoveryCode(UUID uuid, String hash) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE rlogin_recovery_codes SET used_at = ? WHERE uuid = ? AND code_hash = ?")) {
                ps.setLong(1, Instant.now().toEpochMilli());
                ps.setString(2, uuid.toString());
                ps.setString(3, hash);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> clearSession(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM rlogin_sessions WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> purgeExpiredSessions(Instant now) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM rlogin_sessions WHERE expires_at <= ?")) {
                ps.setLong(1, now.toEpochMilli());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private RLoginAccount map(ResultSet rs) throws SQLException {
        return new RLoginAccount(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("username"),
                rs.getBoolean("premium"),
                rs.getString("password_hash"),
                rs.getString("hash_algo"),
                rs.getString("totp_secret"),
                rs.getBoolean("totp_enabled"),
                rs.getString("last_ip"),
                toInstant(rs, "last_login_at"),
                toInstant(rs, "registered_at"),
                rs.getInt("failed_attempts"),
                toInstant(rs, "locked_until")
        );
    }

    private Instant toInstant(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : Instant.ofEpochMilli(v);
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
        executor.shutdown();
    }
}
