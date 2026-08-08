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
 * Importa cuentas desde la tabla {@code authme} del plugin AuthMe.
 *
 * <p>{@code source} admite dos formas:</p>
 * <ul>
 *   <li>una ruta a su fichero SQLite (ej. {@code plugins/AuthMe/authme.db})</li>
 *   <li>una URL JDBC completa si AuthMe usa MySQL, con usuario/contraseña
 *       incluidos (ej. {@code jdbc:mysql://user:pass@host:3306/authme})</li>
 * </ul>
 *
 * <p>Solo reconoce los formatos de hash bcrypt y SHA256 (el algoritmo por
 * defecto de AuthMe, {@code $SHA$salt$hash}). Otros algoritmos (MD5,
 * WHIRLPOOL, XAUTH...) se importan igualmente pero no podrán iniciar sesión
 * hasta que el jugador se registre de nuevo — se avisa en el log de
 * importación.</p>
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
            throw new ImportException("No se pudo leer la base de datos de AuthMe: " + e.getMessage(), e);
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
