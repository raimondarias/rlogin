package com.raimondarias.rlogin.api.importer;

import java.util.List;

/**
 * SPI para migrar cuentas desde otro plugin de autenticación hacia rLogin.
 * {@code source} suele ser una ruta a un fichero (SQLite de AuthMe) o una
 * URL JDBC (MySQL de nLogin/JPremium), según cada implementación.
 */
public interface Importer {

    /** Identificador corto usado en el comando, ej. {@code authme}. */
    String id();

    /** Nombre legible para mensajes/logs, ej. {@code AuthMe}. */
    String displayName();

    /** Lee y normaliza las cuentas del plugin de origen. */
    List<ImportedAccount> read(String source) throws ImportException;
}
