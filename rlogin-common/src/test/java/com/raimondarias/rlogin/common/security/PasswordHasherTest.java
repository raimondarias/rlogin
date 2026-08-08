package com.raimondarias.rlogin.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(4); // coste mínimo: tests rápidos

    @Test
    void hashThenVerifySucceeds() {
        String hash = hasher.hash("correcto-caballo-batería-grapadora");
        assertTrue(hasher.verify("correcto-caballo-batería-grapadora", hash));
    }

    @Test
    void verifyFailsWithWrongPassword() {
        String hash = hasher.hash("miContraseña123");
        assertFalse(hasher.verify("otraContraseña", hash));
    }

    @Test
    void sameSaltNeverRepeatsAcrossHashes() {
        String h1 = hasher.hash("misma-contraseña");
        String h2 = hasher.hash("misma-contraseña");
        assertNotEquals(h1, h2); // bcrypt genera una salt distinta cada vez
        assertTrue(hasher.verify("misma-contraseña", h1));
        assertTrue(hasher.verify("misma-contraseña", h2));
    }

    @Test
    void verifyFailsGracefullyWithNullOrEmptyHash() {
        assertFalse(hasher.verify("cualquiera", null));
        assertFalse(hasher.verify("cualquiera", ""));
    }

    @Test
    void costBelowMinimumIsClampedInsteadOfRejected() {
        // 0 se sube al mínimo (4) en vez de fallar; probamos con un coste bajo real
        // para no hacer el test lento (31 sería computacionalmente carísimo).
        PasswordHasher tooLow = new PasswordHasher(0);
        assertTrue(tooLow.verify("x", tooLow.hash("x")));
    }
}
