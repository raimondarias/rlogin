package com.raimondarias.rlogin.common.migrate;

import com.raimondarias.rlogin.api.importer.ImportException;
import com.raimondarias.rlogin.api.importer.ImportedAccount;
import com.raimondarias.rlogin.api.importer.Importer;
import com.raimondarias.rlogin.common.security.AuthMeLegacyHash;
import com.raimondarias.rlogin.common.util.OfflineUuid;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports accounts from AuthMe's {@code authme} table.
 *
 * <p>{@code source} accepts two forms:</p>
 * <ul>
 *   <li>a path to its SQLite file (e.g. {@code plugins/AuthMe/authme.db})</li>
 *   <li>a full JDBC URL if AuthMe uses MySQL, with username/password
 *       included (e.g. {@code jdbc:mysql://user:pass@host:3306/authme})</li>
 * </ul>
 *
 * <p>Only recognizes bcrypt and SHA256 hash formats (AuthMe's default
 * algorithm, {@code $SHA$salt$hash}). Other algorithms (MD5, WHIRLPOOL,
 * XAUTH...) are still imported, but those accounts won't be able to log in
 * until the player registers again — this is reported in the import log.</p>
 */
public final class AuthMeImporter implements Importer {

    @Override
    public String id() {
        return "authme";
    }

    @Override
    public String displayName() {
        return "AuthMe";
    }

    @Override
    public List<ImportedAccount> read(String source) throws ImportException {
        String jdbcUrl = source.startsWith("jdbc:") ? source : "jdbc:sqlite:" + Path.of(source).toAbsolutePath();
        List<ImportedAccount> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT username, realname, password, ip FROM authme")) {
            while (rs.next()) {
                String username = firstNonBlank(rs.getString("realname"), rs.getString("username"));
                if (username == null || username.isBlank()) {
                    continue;
                }
                String stored = rs.getString("password");
                String algo = detectAlgo(stored);
                out.add(new ImportedAccount(username, OfflineUuid.of(username), false, stored, algo, rs.getString("ip")));
            }
        } catch (SQLException e) {
            throw new ImportException("Could not read AuthMe's database: " + e.getMessage(), e);
        }
        return out;
    }

    private static String detectAlgo(String stored) {
        if (AuthMeLegacyHash.matches(stored)) {
            return "authme-sha256";
        }
        if (stored != null && (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$"))) {
            return "bcrypt";
        }
        return "unsupported";
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}
