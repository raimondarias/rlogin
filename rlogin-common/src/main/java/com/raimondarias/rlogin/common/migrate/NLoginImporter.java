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
 * Reads nLogin's account table.
 *
 * <p>nLogin keeps accounts in {@code nlogin_users} (older builds:
 * {@code nlogin}), with the name in {@code last_name} or {@code name}, the
 * hash in {@code password}, and — the useful part — the real Mojang UUID in
 * {@code unique_id} for accounts it verified as premium. That column is read
 * when present, so a premium player keeps the identity they already had
 * rather than arriving as a new offline account.</p>
 *
 * <p>Both the table name and the column names vary between nLogin versions,
 * so both are discovered rather than assumed, and an unrecognised layout is
 * reported as such instead of failing with a bare SQL error.</p>
 */
public final class NLoginImporter implements Importer {

    private static final List<String> TABLE_CANDIDATES = List.of("nlogin_users", "nlogin");
    private static final List<String> NAME_COLUMNS = List.of("last_name", "name", "username", "player_name");
    private static final List<String> HASH_COLUMNS = List.of("password", "hash", "password_hash");
    private static final List<String> UUID_COLUMNS = List.of("unique_id", "uuid", "mojang_id");
    private static final List<String> IP_COLUMNS = List.of("last_ip", "ip", "register_ip");

    @Override
    public String id() {
        return "nlogin";
    }

    @Override
    public String displayName() {
        return "nLogin";
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
                if (nameColumn == null || hashColumn == null) {
                    throw new ImportException("Found the table " + table + ", but not the columns this importer "
                            + "needs (a name and a password). This nLogin version may store them differently; "
                            + "please open an issue with your nLogin version.");
                }
                String uuidColumn = firstPresent(meta, UUID_COLUMNS);
                String ipColumn = firstPresent(meta, IP_COLUMNS);

                while (rs.next()) {
                    String username = rs.getString(nameColumn);
                    if (username == null || username.isBlank()) {
                        continue;
                    }
                    String stored = rs.getString(hashColumn);
                    UUID uuid = readUuid(rs, uuidColumn, username);
                    // A row carrying a real Mojang UUID is one nLogin verified as premium;
                    // anything else is a password account, whatever else is on the row.
                    boolean premium = !uuid.equals(OfflineUuid.of(username));
                    out.add(new ImportedAccount(username, uuid, premium, stored, detectAlgo(stored),
                            ipColumn == null ? null : rs.getString(ipColumn)));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new ImportException("Could not read nLogin's database: " + e.getMessage(), e);
        }
    }

    /**
     * nLogin's own default is BCrypt. Older installs may carry SHA-256/512,
     * which rLogin cannot verify — those accounts still come across so the
     * player keeps their name and identity, and are reported so you know who
     * has to register again.
     */
    private static String detectAlgo(String stored) {
        if (stored == null || stored.isBlank()) {
            return "unsupported";
        }
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            return "bcrypt";
        }
        return "unsupported";
    }

    private static UUID readUuid(ResultSet rs, String column, String username) throws SQLException {
        if (column != null) {
            String raw = rs.getString(column);
            if (raw != null && !raw.isBlank()) {
                try {
                    return UUID.fromString(raw.contains("-") ? raw : dashed(raw));
                } catch (IllegalArgumentException ignored) {
                    // Unparseable: fall through to the offline UUID rather than drop the row.
                }
            }
        }
        return OfflineUuid.of(username);
    }

    /** nLogin stores UUIDs undashed in some versions. */
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
        throw new ImportException("No nLogin table found. Looked for: " + String.join(", ", TABLE_CANDIDATES));
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
