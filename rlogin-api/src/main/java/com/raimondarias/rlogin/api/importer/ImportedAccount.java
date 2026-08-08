package com.raimondarias.rlogin.api.importer;

import java.util.UUID;

/**
 * Normalized row read from another auth plugin's database (AuthMe, nLogin,
 * JPremium/LoginSecurity...) before turning it into an
 * {@link com.raimondarias.rlogin.api.RLoginAccount}.
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
