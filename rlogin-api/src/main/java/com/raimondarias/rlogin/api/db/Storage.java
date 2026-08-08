package com.raimondarias.rlogin.api.db;

import com.raimondarias.rlogin.api.RLoginAccount;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * rLogin's persistence SPI. Every operation is asynchronous: none of them
 * should ever be called from Bukkit's main/region thread, nor from
 * Velocity's event loop.
 *
 * <p>Bundled implementations: {@code SqliteStorage} and {@code MysqlStorage}
 * (module {@code rlogin-common}). A third-party addon can bring its own by
 * implementing this interface.</p>
 */
public interface Storage extends AutoCloseable {

    /** Creates the schema if missing and gets the connection pool ready. */
    CompletableFuture<Void> init();

    CompletableFuture<Optional<RLoginAccount>> findByUuid(UUID uuid);

    CompletableFuture<Optional<RLoginAccount>> findByUsername(String username);

    CompletableFuture<RLoginAccount> save(RLoginAccount account);

    CompletableFuture<Void> delete(UUID uuid);

    // --- "Remember me" session ---

    CompletableFuture<Void> saveSession(UUID uuid, String ip, String server, Instant expiresAt);

    CompletableFuture<Boolean> hasValidSession(UUID uuid, String ip, Instant now);

    CompletableFuture<Void> clearSession(UUID uuid);

    CompletableFuture<Void> purgeExpiredSessions(Instant now);

    @Override
    void close();
}
