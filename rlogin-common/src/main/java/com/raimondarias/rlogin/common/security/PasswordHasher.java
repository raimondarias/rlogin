package com.raimondarias.rlogin.common.security;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Hashing de contraseñas con bcrypt (algoritmo recomendado para auth de
 * plugins de Minecraft: lento a propósito, con coste configurable y salt
 * integrada en el propio hash).
 */
public final class PasswordHasher {

    public static final String ALGO_ID = "bcrypt";

    private final int cost;

    public PasswordHasher(int cost) {
        this.cost = Math.min(31, Math.max(4, cost));
    }

    public String hash(String plain) {
        return BCrypt.withDefaults().hashToString(cost, plain.toCharArray());
    }

    public boolean verify(String plain, String hash) {
        if (hash == null || hash.isEmpty()) {
            return false;
        }
        return BCrypt.verifyer().verify(plain.toCharArray(), hash).verified;
    }
}
