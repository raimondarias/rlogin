package com.raimondarias.rlogin.api.importer;

import java.util.List;

/**
 * SPI for migrating accounts from another auth plugin into rLogin.
 * {@code source} is usually a path to a file (AuthMe's SQLite) or a JDBC
 * URL (nLogin/JPremium's MySQL), depending on the implementation.
 */
public interface Importer {

    /** Short identifier used in the command, e.g. {@code authme}. */
    String id();

    /** Human-readable name for messages/logs, e.g. {@code AuthMe}. */
    String displayName();

    /** Reads and normalizes the accounts from the source plugin. */
    List<ImportedAccount> read(String source) throws ImportException;
}
