package com.raimondarias.rlogin.api.db;

import com.raimondarias.rlogin.api.RLoginAccount;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SPI de persistencia de rLogin. Todas las operaciones son asíncronas: nunca
 * deben llamarse desde el hilo principal/de región de Bukkit ni desde el
 * event loop de Velocity.
 *
 * <p>Implementaciones incluidas: {@code SqliteStorage} y {@code MysqlStorage}
 * (módulo {@code rlogin-common}). Un addon de terceros puede aportar la suya
 * implementando esta interfaz.</p>
 */
public interface Storage extends AutoCloseable {

    /** Crea el esquema si no existe y deja el pool de conexiones listo. */
    CompletableFuture<Void> init();

    CompletableFuture<Optional<RLoginAccount>> findByUuid(UUID uuid);

    CompletableFuture<Optional<RLoginAccount>> findByUsername(String username);

    CompletableFuture<RLoginAccount> save(RLoginAccount account);

    CompletableFuture<Void> delete(UUID uuid);

    // --- Sesión "recuérdame" ---

    CompletableFuture<Void> saveSession(UUID uuid, String ip, String server, Instant expiresAt);

    CompletableFuture<Boolean> hasValidSession(UUID uuid, String ip, Instant now);

    CompletableFuture<Void> clearSession(UUID uuid);

    CompletableFuture<Void> purgeExpiredSessions(Instant now);

    @Override
    void close();
}
