package com.raimondarias.rlogin.common.security;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Password hashing with bcrypt (the recommended algorithm for Minecraft
 * auth plugins: deliberately slow, configurable cost, salt embedded in the
 * hash itself).
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
