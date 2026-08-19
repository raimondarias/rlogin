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
 *
 * <p>The methods added in later versions have {@code default} no-op bodies
 * so an existing third-party storage keeps compiling. Two of them default to
 * the <em>permissive</em> side on purpose: {@link #countLoginFailures} (no
 * failures means no lockout) and {@link #isKnownIp} (every address is known,
 * so the new-device confirmation never fires). Implement them to opt in to
 * the distributed brute-force limit and the device memory; the bundled
 * storages implement all of them.</p>
 */
public interface Storage extends AutoCloseable {

    /** Creates the schema if missing and gets the connection pool ready. */
    CompletableFuture<Void> init();

    CompletableFuture<Optional<RLoginAccount>> findByUuid(UUID uuid);

    CompletableFuture<Optional<RLoginAccount>> findByUsername(String username);

    CompletableFuture<RLoginAccount> save(RLoginAccount account);

    CompletableFuture<Void> delete(UUID uuid);

    // --- "Remember me" session ---

    /**
     * Creates (or replaces) a session and returns its one-time reconnect
     * token in the clear. The token is the only thing the player can present
     * later to prove the session is theirs; only its hash is stored, so a
     * database read cannot be replayed. A {@code null} return means the
     * storage has no token support and the caller should not promise one.
     */
    CompletableFuture<String> saveSession(UUID uuid, String ip, String server, Instant expiresAt);

    /** Whether a session for this UUID+address is still valid. */
    CompletableFuture<Boolean> hasValidSession(UUID uuid, String ip, Instant now);

    /**
     * Spends a session token. Single-use: the row is deleted, so the same
     * token cannot be presented twice.
     */
    CompletableFuture<Boolean> consumeSessionToken(UUID uuid, String token, Instant now);

    CompletableFuture<Void> clearSession(UUID uuid);

    // --- Session transfer codes (cross-device sign-in) ---

    /**
     * Creates a short-lived, single-use transfer code for this account and
     * returns it in the clear (only its hash is stored, so a database read
     * can never be replayed as a code). A player who runs {@code /session}
     * on a device they already trust mints one of these and redeems it on a
     * new device instead of typing the password there. {@code null} means
     * the storage doesn't support codes.
     */
    default CompletableFuture<String> issueTransferToken(UUID uuid, Instant expiresAt) {
        return CompletableFuture.completedFuture(null);
    }

    /** Spends a transfer code; false when unknown, already used, or expired. */
    default CompletableFuture<Boolean> consumeTransferToken(UUID uuid, String token, Instant now) {
        return CompletableFuture.completedFuture(false);
    }

    /** Drops transfer codes that have aged out of every window. */
    default CompletableFuture<Void> purgeExpiredTransferTokens(Instant now) {
        return CompletableFuture.completedFuture(null);
    }

    // --- Distributed login-failure limit ---

    /** Records one failed login for this address and this account name. */
    default CompletableFuture<Void> recordLoginFailure(String ip, String username, Instant now) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * How many failures this address or this name has accumulated in the
     * window — the larger of the two, since either one crossing the limit
     * is enough to lock out. {@code 0} (the default) means the storage
     * doesn't track failures, so no lockout is ever computed from it.
     */
    default CompletableFuture<Integer> countLoginFailures(String ip, String username, Instant since) {
        return CompletableFuture.completedFuture(0);
    }

    /**
     * Epoch milliseconds of the oldest recorded failure within the window,
     * or {@code -1} when there is none. Used to tell a locked-out player how
     * long is actually left instead of a full window.
     */
    default CompletableFuture<Long> oldestLoginFailureWithin(String ip, String username, Instant since) {
        return CompletableFuture.completedFuture(-1L);
    }

    /** Drops every recorded failure for this address or name (a successful login). */
    default CompletableFuture<Void> clearLoginFailures(String ip, String username) {
        return CompletableFuture.completedFuture(null);
    }

    /** Drops failures that have aged out of every window. */
    default CompletableFuture<Void> purgeExpiredLoginFailures(Instant now) {
        return CompletableFuture.completedFuture(null);
    }

    // --- Device memory (known addresses per account) ---

    /**
     * Whether this account has ever authenticated from this address.
     * Defaults to {@code true}: a storage that doesn't track addresses
     * should not force every player through a new-device confirmation.
     */
    default CompletableFuture<Boolean> isKnownIp(UUID uuid, String ip) {
        return CompletableFuture.completedFuture(true);
    }

    /** Records that this account authenticated from this address. */
    default CompletableFuture<Void> rememberIp(UUID uuid, String ip, Instant now) {
        return CompletableFuture.completedFuture(null);
    }

    /** Keeps only the {@code keep} most recently seen addresses for this account. */
    default CompletableFuture<Void> pruneKnownIps(UUID uuid, int keep) {
        return CompletableFuture.completedFuture(null);
    }

    CompletableFuture<Void> purgeExpiredSessions(Instant now);

    @Override
    void close();
}
