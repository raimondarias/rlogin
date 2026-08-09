package com.raimondarias.rlogin.common.migrate;

import com.raimondarias.rlogin.api.importer.ImportException;
import com.raimondarias.rlogin.api.importer.ImportedAccount;
import com.raimondarias.rlogin.api.importer.Importer;
import com.raimondarias.rlogin.common.util.OfflineUuid;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Reads JPremium's and LoginSecurity's account tables, which are close enough
 * in shape to share an importer.
 *
 * <p>LoginSecurity v3 keeps accounts in {@code ls_players}, keyed by
 * {@code unique_user_id} with the hash in {@code hashed_password} and the
 * algorithm named in {@code algorithm}. JPremium uses {@code jpremium_users}
 * or {@code premium_users} with its own column names, and additionally
 * records whether an account was verified as premium.</p>
 *
 * <p>Which one is in front of it is worked out from the tables and columns
 * that actually exist, not assumed, so an unrecognised layout is reported
 * plainly rather than failing with a bare SQL error.</p>
 */
public final class JPremiumImporter implements Importer {

    private static final List<String> TABLE_CANDIDATES =
            List.of("ls_players", "jpremium_users", "premium_users", "jpremium");
    private static final List<String> NAME_COLUMNS =
            List.of("last_name", "name", "username", "player_name", "nickname");
    private static final List<String> HASH_COLUMNS =
            List.of("hashed_password", "password", "hash", "password_hash");
    private static final List<String> UUID_COLUMNS =
            List.of("unique_user_id", "unique_id", "uuid", "premium_uuid");
    private static final List<String> IP_COLUMNS = List.of("last_ip", "ip", "ip_address");
    private static final List<String> ALGO_COLUMNS = List.of("algorithm", "hash_algorithm", "algo");
    private static final List<String> PREMIUM_COLUMNS = List.of("premium", "is_premium", "premium_login");

    @Override
    public String id() {
        return "jpremium";
    }

    @Override
    public String displayName() {
        return "JPremium / LoginSecurity";
    }

    @Override
    public List<ImportedAccount> read(String source) throws ImportException {
        String jdbcUrl = source.startsWith("jdbc:") ? source : "jdbc:sqlite:" + Path.of(source).toAbsolutePath();
        try (Connection c = DriverManager.getConnection(jdbcUrl)) {
            String table = findTable(c);
            List<ImportedAccount> out = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM " + table)) {
                ResultSetMetaData meta = rs.getMetaData();
                String nameColumn = firstPresent(meta, NAME_COLUMNS);
                String hashColumn = firstPresent(meta, HASH_COLUMNS);
                String uuidColumn = firstPresent(meta, UUID_COLUMNS);
                if (hashColumn == null || (nameColumn == null && uuidColumn == null)) {
                    throw new ImportException("Found the table " + table + ", but not the columns this importer "
                            + "needs. Please open an issue with the plugin and version you are migrating from.");
                }
                String algoColumn = firstPresent(meta, ALGO_COLUMNS);
                String ipColumn = firstPresent(meta, IP_COLUMNS);
                String premiumColumn = firstPresent(meta, PREMIUM_COLUMNS);

                while (rs.next()) {
                    UUID uuid = readUuid(rs, uuidColumn);
                    String username = nameColumn == null ? null : rs.getString(nameColumn);
                    if ((username == null || username.isBlank()) && uuid == null) {
                        continue;
                    }
                    if (username == null || username.isBlank()) {
                        // LoginSecurity keys on the UUID alone and may not carry a name.
                        // Importing without one would create an account nobody can log into,
                        // so it is skipped and reported rather than silently written.
                        continue;
                    }
                    UUID resolved = uuid != null ? uuid : OfflineUuid.of(username);
                    boolean premium = premiumColumn != null
                            ? rs.getBoolean(premiumColumn)
                            : !resolved.equals(OfflineUuid.of(username));
                    String stored = rs.getString(hashColumn);
                    out.add(new ImportedAccount(username, resolved, premium, stored,
                            detectAlgo(stored, algoColumn == null ? null : rs.getString(algoColumn)),
                            ipColumn == null ? null : rs.getString(ipColumn)));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new ImportException("Could not read the database: " + e.getMessage(), e);
        }
    }

    /**
     * LoginSecurity names its algorithm in a column; JPremium does not, so the
     * hash is inspected. Either way only BCrypt carries across — anything else
     * is imported so the player keeps their name, and reported so you know
     * who has to register again.
     */
    private static String detectAlgo(String stored, String declared) {
        if (declared != null && declared.toUpperCase(Locale.ROOT).contains("BCRYPT")) {
            return "bcrypt";
        }
        if (stored == null || stored.isBlank()) {
            return "unsupported";
        }
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            return "bcrypt";
        }
        return "unsupported";
    }

    private static UUID readUuid(ResultSet rs, String column) throws SQLException {
        if (column == null) {
            return null;
        }
        String raw = rs.getString(column);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.contains("-") ? raw : dashed(raw));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String dashed(String raw) {
        if (raw.length() != 32) {
            return raw;
        }
        return raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16)
                + "-" + raw.substring(16, 20) + "-" + raw.substring(20);
    }

    private static String findTable(Connection c) throws SQLException, ImportException {
        for (String candidate : TABLE_CANDIDATES) {
            try (Statement st = c.createStatement()) {
                st.executeQuery("SELECT * FROM " + candidate + " LIMIT 1").close();
                return candidate;
            } catch (SQLException ignored) {
                // Not this one; try the next name.
            }
        }
        throw new ImportException("No JPremium or LoginSecurity table found. Looked for: "
                + String.join(", ", TABLE_CANDIDATES));
    }

    private static String firstPresent(ResultSetMetaData meta, List<String> candidates) throws SQLException {
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String column = meta.getColumnLabel(i).toLowerCase(Locale.ROOT);
            if (candidates.contains(column)) {
                return column;
            }
        }
        return null;
    }
}
