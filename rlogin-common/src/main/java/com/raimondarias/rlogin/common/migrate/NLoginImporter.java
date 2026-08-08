package com.raimondarias.rlogin.common.migrate;

import com.raimondarias.rlogin.api.importer.ImportException;
import com.raimondarias.rlogin.api.importer.ImportedAccount;
import com.raimondarias.rlogin.api.importer.Importer;

import java.util.List;

/**
 * TODO (Phase 2): nLogin stores accounts in its own table
 * ({@code nlogin_playerdata} on MySQL, or embedded H2 when standalone) with
 * its own columns and hash format (bcrypt). Reading against a real nLogin
 * schema still needs implementing; this leaves the gap explicit instead of
 * pretending it works.
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
        throw new ImportException("The nLogin importer isn't implemented yet (Phase 2). "
                + "Feel free to use AuthMeImporter as a reference and contribute one via PR.");
    }
}
