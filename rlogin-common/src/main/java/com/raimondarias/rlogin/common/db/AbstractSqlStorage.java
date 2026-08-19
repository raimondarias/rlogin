package com.raimondarias.rlogin.common.db;

import com.raimondarias.rlogin.api.RLoginAccount;
import com.raimondarias.rlogin.api.db.Storage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
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

    protected abstract String createLoginFailuresTableSql();

    protected abstract String createKnownIpsTableSql();

    protected abstract String createTransferTokensTableSql();

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
                st.execute(createLoginFailuresTableSql());
                st.execute(createKnownIpsTableSql());
                st.execute(createTransferTokensTableSql());
                createUsernameIndex(st);
                migrateSessionsTable(c);
                seedKnownIps(c);
            } catch (SQLException e) {
                throw new RuntimeException("Could not initialise rLogin's database", e);
            }
        }, executor);
    }

    /**
     * Older versions created {@code rlogin_sessions} without the
     * {@code token_hash} column, and {@code CREATE TABLE IF NOT EXISTS}
     * leaves an existing table alone — so the column is added here when
     * missing. The duplicate-column error is expected on every run after
     * the first.
     */
    private void migrateSessionsTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE rlogin_sessions ADD COLUMN token_hash VARCHAR(64)");
        } catch (SQLException e) {
            boolean duplicate = e.getErrorCode() == 1060 // MySQL: Duplicate column name
                    || String.valueOf(e.getMessage()).toLowerCase(java.util.Locale.ROOT).contains("duplicate column");
            if (!duplicate) {
                throw e;
            }
        }
    }

    /**
     * A device a player has already logged in from before the device-memory
     * feature existed should not suddenly look brand-new: their recorded
     * last address is trusted on upgrade, so nobody is forced through a
     * device confirmation for the machine they already play on.
     */
    private void seedKnownIps(Connection c) throws SQLException {
        try (PreparedStatement select = c.prepareStatement(
                "SELECT uuid, last_ip, last_login_at FROM rlogin_accounts "
                        + "WHERE last_ip IS NOT NULL AND last_login_at IS NOT NULL");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String ip = rs.getString("last_ip");
                long when = rs.getLong("last_login_at");
                try (PreparedStatement upd = c.prepareStatement(
                        "UPDATE rlogin_known_ips SET last_seen = ? WHERE uuid = ? AND ip = ?")) {
                    upd.setLong(1, when);
                    upd.setString(2, uuid);
                    upd.setString(3, ip);
                    if (upd.executeUpdate() == 0) {
                        try (PreparedStatement ins = c.prepareStatement(
                                "INSERT INTO rlogin_known_ips (uuid, ip, first_seen, last_seen) "
                                        + "VALUES (?, ?, ?, ?)")) {
                            ins.setString(1, uuid);
                            ins.setString(2, ip);
                            ins.setLong(3, when);
                            ins.setLong(4, when);
                            ins.executeUpdate();
                        }
                    }
                }
            }
        }
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
    public CompletableFuture<String> saveSession(UUID uuid, String ip, String server, Instant expiresAt) {
        return CompletableFuture.supplyAsync(() -> {
            // 256 bits of entropy, hex-encoded; only the SHA-256 of it is stored,
            // so a database read can never be replayed as a session.
            String token = generateToken();
            try (Connection c = dataSource.getConnection()) {
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM rlogin_sessions WHERE uuid = ? AND ip = ?")) {
                    del.setString(1, uuid.toString());
                    del.setString(2, ip);
                    del.executeUpdate();
                }
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO rlogin_sessions (uuid, ip, server, created_at, expires_at, token_hash) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
                    ins.setString(1, uuid.toString());
                    ins.setString(2, ip);
                    ins.setString(3, server);
                    ins.setLong(4, Instant.now().toEpochMilli());
                    ins.setLong(5, expiresAt.toEpochMilli());
                    ins.setString(6, sha256Hex(token));
                    ins.executeUpdate();
                }
                return token;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> consumeSessionToken(UUID uuid, String token, Instant now) {
        return CompletableFuture.supplyAsync(() -> {
            if (token == null || token.isBlank()) {
                return false;
            }
            // Deleting on match makes the token single-use with no extra step:
            // the same token can never be presented twice.
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM rlogin_sessions WHERE uuid = ? AND token_hash = ? AND expires_at > ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, sha256Hex(token));
                ps.setLong(3, now.toEpochMilli());
                return ps.executeUpdate() > 0;
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

    // --- Session transfer codes ---

    @Override
    public CompletableFuture<String> issueTransferToken(UUID uuid, Instant expiresAt) {
        return CompletableFuture.supplyAsync(() -> {
            // A transfer code is typed by hand in chat, so it is kept shorter than
            // the machine-to-machine session token — 128 bits is still far more
            // entropy than a short-lived, single-use code needs.
            byte[] bytes = new byte[16];
            TOKEN_RANDOM.nextBytes(bytes);
            String token = HexFormat.of().formatHex(bytes);
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO rlogin_transfer_tokens (uuid, token_hash, created_at, expires_at) "
                                 + "VALUES (?, ?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, sha256Hex(token));
                ps.setLong(3, Instant.now().toEpochMilli());
                ps.setLong(4, expiresAt.toEpochMilli());
                ps.executeUpdate();
                return token;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> consumeTransferToken(UUID uuid, String token, Instant now) {
        return CompletableFuture.supplyAsync(() -> {
            if (token == null || token.isBlank()) {
                return false;
            }
            // Deleting on match makes the code single-use with no extra step:
            // the same code can never be presented twice.
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM rlogin_transfer_tokens WHERE uuid = ? AND token_hash = ? AND expires_at > ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, sha256Hex(token));
                ps.setLong(3, now.toEpochMilli());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> purgeExpiredTransferTokens(Instant now) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM rlogin_transfer_tokens WHERE expires_at <= ?")) {
                ps.setLong(1, now.toEpochMilli());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    // --- Distributed login-failure limit ---

    @Override
    public CompletableFuture<Void> recordLoginFailure(String ip, String username, Instant now) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO rlogin_login_failures (ip, username, attempted_at) VALUES (?, ?, ?)")) {
                ps.setString(1, ip);
                ps.setString(2, username);
                ps.setLong(3, now.toEpochMilli());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> countLoginFailures(String ip, String username, Instant since) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT (SELECT COUNT(*) FROM rlogin_login_failures WHERE ip = ? AND attempted_at > ?) AS by_ip, "
                    + "(SELECT COUNT(*) FROM rlogin_login_failures WHERE username = ? AND attempted_at > ?) AS by_name";
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, ip);
                ps.setLong(2, since.toEpochMilli());
                ps.setString(3, username);
                ps.setLong(4, since.toEpochMilli());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    // Either dimension crossing the limit is enough to lock out.
                    return Math.max(rs.getInt("by_ip"), rs.getInt("by_name"));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Long> oldestLoginFailureWithin(String ip, String username, Instant since) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT MIN(attempted_at) AS oldest FROM rlogin_login_failures "
                    + "WHERE (ip = ? OR username = ?) AND attempted_at > ?";
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, ip);
                ps.setString(2, username);
                ps.setLong(3, since.toEpochMilli());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    long oldest = rs.getLong("oldest");
                    return rs.wasNull() ? -1L : oldest;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> clearLoginFailures(String ip, String username) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM rlogin_login_failures WHERE ip = ? OR username = ?")) {
                ps.setString(1, ip);
                ps.setString(2, username);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> purgeExpiredLoginFailures(Instant now) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM rlogin_login_failures WHERE attempted_at <= ?")) {
                ps.setLong(1, now.toEpochMilli());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    // --- Device memory ---

    @Override
    public CompletableFuture<Boolean> isKnownIp(UUID uuid, String ip) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT 1 FROM rlogin_known_ips WHERE uuid = ? AND ip = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, ip);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> rememberIp(UUID uuid, String ip, Instant now) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                try (PreparedStatement upd = c.prepareStatement(
                        "UPDATE rlogin_known_ips SET last_seen = ? WHERE uuid = ? AND ip = ?")) {
                    upd.setLong(1, now.toEpochMilli());
                    upd.setString(2, uuid.toString());
                    upd.setString(3, ip);
                    if (upd.executeUpdate() == 0) {
                        try (PreparedStatement ins = c.prepareStatement(
                                "INSERT INTO rlogin_known_ips (uuid, ip, first_seen, last_seen) "
                                        + "VALUES (?, ?, ?, ?)")) {
                            ins.setString(1, uuid.toString());
                            ins.setString(2, ip);
                            ins.setLong(3, now.toEpochMilli());
                            ins.setLong(4, now.toEpochMilli());
                            ins.executeUpdate();
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> pruneKnownIps(UUID uuid, int keep) {
        return CompletableFuture.runAsync(() -> {
            if (keep <= 0) {
                return;
            }
            try (Connection c = dataSource.getConnection()) {
                Long threshold = null;
                // The keep-th most recent last_seen; everything older goes.
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT last_seen FROM rlogin_known_ips WHERE uuid = ? "
                                + "ORDER BY last_seen DESC LIMIT 1 OFFSET ?")) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, Math.max(0, keep - 1));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            threshold = rs.getLong("last_seen");
                        }
                    }
                }
                if (threshold == null) {
                    return; // keep or fewer addresses: nothing to prune.
                }
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM rlogin_known_ips WHERE uuid = ? AND last_seen < ?")) {
                    del.setString(1, uuid.toString());
                    del.setLong(2, threshold);
                    del.executeUpdate();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private static String generateToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable on this JVM", e);
        }
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
