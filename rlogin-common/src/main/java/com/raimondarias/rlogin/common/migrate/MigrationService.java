package com.raimondarias.rlogin.common.migrate;

import com.raimondarias.rlogin.api.RLoginAccount;
import com.raimondarias.rlogin.api.db.Storage;
import com.raimondarias.rlogin.api.importer.ImportException;
import com.raimondarias.rlogin.api.importer.ImportedAccount;
import com.raimondarias.rlogin.api.importer.Importer;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ejecuta un {@link Importer} y vuelca el resultado en el {@link Storage}
 * activo. Nunca se dispara solo: siempre a petición explícita de un admin
 * vía {@code /rlogin migrate <plugin> <origen>}.
 */
public final class MigrationService {

    public record MigrationResult(int imported, int skippedExisting) {
    }

    private final Storage storage;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rlogin-migration");
        t.setDaemon(true);
        return t;
    });

    public MigrationService(Storage storage) {
        this.storage = storage;
    }

    public CompletableFuture<MigrationResult> importFrom(Importer importer, String source) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return importer.read(source);
            } catch (ImportException e) {
                throw new RuntimeException(e);
            }
        }, executor).thenCompose(this::persist);
    }

    private CompletableFuture<MigrationResult> persist(List<ImportedAccount> accounts) {
        CompletableFuture<MigrationResult> chain = CompletableFuture.completedFuture(new MigrationResult(0, 0));
        for (ImportedAccount imported : accounts) {
            chain = chain.thenCompose(result -> storage.findByUuid(imported.uuid()).thenCompose(existing -> {
                if (existing.isPresent()) {
                    return CompletableFuture.completedFuture(
                            new MigrationResult(result.imported(), result.skippedExisting() + 1));
                }
                Instant now = Instant.now();
                RLoginAccount account = new RLoginAccount(
                        imported.uuid(), imported.username(), imported.premium(),
                        imported.passwordHash(), imported.hashAlgo(), null, false,
                        imported.lastIp(), null, now, 0, null);
                return storage.save(account).thenApply(saved ->
                        new MigrationResult(result.imported() + 1, result.skippedExisting()));
            }));
        }
        return chain;
    }

    public void shutdown() {
        executor.shutdown();
    }
}
