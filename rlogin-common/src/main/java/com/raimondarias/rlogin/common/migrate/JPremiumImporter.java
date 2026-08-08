package com.raimondarias.rlogin.common.migrate;

import com.raimondarias.rlogin.api.importer.ImportException;
import com.raimondarias.rlogin.api.importer.ImportedAccount;
import com.raimondarias.rlogin.api.importer.Importer;

import java.util.List;

/**
 * TODO (Fase 2): JPremium/LoginSecurity tienen esquemas propios (y
 * distintos entre sí). Falta implementar la lectura real; de momento se deja
 * el hueco explícito en vez de fingir que funciona.
 */
public final class JPremiumImporter implements Importer {

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
        throw new ImportException("El importador de JPremium/LoginSecurity todavía no está implementado (Fase 2). "
                + "Puedes usar AuthMeImporter como referencia para aportar uno vía PR.");
    }
}
