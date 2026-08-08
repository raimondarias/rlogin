package com.raimondarias.rlogin.common.migrate;

import com.raimondarias.rlogin.api.importer.ImportedAccount;
import com.raimondarias.rlogin.common.util.OfflineUuid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthMeImporterTest {

    private void createFakeAuthMeDatabase(Path dbFile) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE authme (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username VARCHAR(255) NOT NULL,
                        realname VARCHAR(255) NOT NULL,
                        password VARCHAR(255) NOT NULL,
                        ip VARCHAR(40)
                    )
                    """);
            st.execute("INSERT INTO authme (username, realname, password, ip) VALUES "
                    + "('steve', 'Steve', '$SHA$abc123$deadbeefcafe', '127.0.0.1')");
            st.execute("INSERT INTO authme (username, realname, password, ip) VALUES "
                    + "('alex', 'Alex', '$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuv', '127.0.0.2')");
            st.execute("INSERT INTO authme (username, realname, password, ip) VALUES "
                    + "('herobrine', 'Herobrine', 'md5:notsupported', '127.0.0.3')");
        }
    }

    @Test
    void readsAndClassifiesAccountsByHashFormat(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("authme.db");
        createFakeAuthMeDatabase(dbFile);

        List<ImportedAccount> accounts = new AuthMeImporter().read(dbFile.toString());
        assertEquals(3, accounts.size());

        ImportedAccount steve = findByUsername(accounts, "Steve");
        assertEquals("authme-sha256", steve.hashAlgo());
        assertEquals(OfflineUuid.of("Steve"), steve.uuid());
        assertEquals("127.0.0.1", steve.lastIp());
        assertTrue(!steve.premium());

        ImportedAccount alex = findByUsername(accounts, "Alex");
        assertEquals("bcrypt", alex.hashAlgo());

        ImportedAccount herobrine = findByUsername(accounts, "Herobrine");
        assertEquals("unsupported", herobrine.hashAlgo());
    }

    private ImportedAccount findByUsername(List<ImportedAccount> accounts, String username) {
        return accounts.stream()
                .filter(a -> a.username().equals(username))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find " + username));
    }
}
