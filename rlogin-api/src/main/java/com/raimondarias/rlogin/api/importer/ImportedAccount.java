package com.raimondarias.rlogin.api.importer;

import java.util.UUID;

/**
 * Fila normalizada leída desde la base de datos de otro plugin de
 * autenticación (AuthMe, nLogin, JPremium/LoginSecurity...) antes de
 * convertirla en un {@link com.raimondarias.rlogin.api.RLoginAccount}.
 */
public record ImportedAccount(
        String username,
        UUID uuid,
        boolean premium,
        String passwordHash,
        String hashAlgo,
        String lastIp
) {
}
