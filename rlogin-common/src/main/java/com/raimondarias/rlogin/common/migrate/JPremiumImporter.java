package com.raimondarias.rlogin.common.migrate;

import com.raimondarias.rlogin.api.importer.ImportException;
import com.raimondarias.rlogin.api.importer.ImportedAccount;
import com.raimondarias.rlogin.api.importer.Importer;

import java.util.List;

/**
 * TODO (Phase 2): JPremium/LoginSecurity each have their own (and mutually
 * different) schemas. Reading against a real schema still needs
 * implementing; this leaves the gap explicit instead of pretending it works.
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
        throw new ImportException("The JPremium/LoginSecurity importer isn't implemented yet (Phase 2). "
                + "Feel free to use AuthMeImporter as a reference and contribute one via PR.");
    }
}
