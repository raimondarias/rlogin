package com.raimondarias.rlogin.common.migrate;

import com.raimondarias.rlogin.api.importer.ImportException;
import com.raimondarias.rlogin.api.importer.ImportedAccount;
import com.raimondarias.rlogin.api.importer.Importer;

import java.util.List;

/**
 * TODO (Fase 2): nLogin guarda las cuentas en su propia tabla
 * ({@code nlogin_playerdata} en MySQL, o H2 embebido si es standalone) con
 * columnas y formato de hash (bcrypt) propios. Falta implementar la lectura
 * real contra un esquema de nLogin de verdad; de momento se deja el hueco
 * explícito en vez de fingir que funciona.
 */
public final class NLoginImporter implements Importer {

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
        throw new ImportException("El importador de nLogin todavía no está implementado (Fase 2). "
                + "Puedes usar AuthMeImporter como referencia para aportar uno vía PR.");
    }
}
