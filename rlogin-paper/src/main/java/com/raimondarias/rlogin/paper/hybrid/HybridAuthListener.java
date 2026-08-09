package com.raimondarias.rlogin.paper.hybrid;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.crypto.MinecraftEncryptionUtil;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientEncryptionResponse;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientLoginStart;
import com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerEncryptionRequest;
import com.raimondarias.rlogin.common.auth.AccountService;
import com.raimondarias.rlogin.common.auth.MojangSessionVerifier;
import com.raimondarias.rlogin.api.RLoginAccount;
import com.raimondarias.rlogin.common.auth.PremiumChecker;
import com.raimondarias.rlogin.common.auth.ProfileProperty;
import com.raimondarias.rlogin.common.auth.UuidType;
import com.raimondarias.rlogin.common.config.RLoginConfig;
import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import io.netty.channel.ChannelPipeline;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Lets premium accounts auto-login on a single standalone backend
 * (online-mode: false, no proxy in front). Turned on automatically
 * wherever nothing else verifies the connection — see {@link
 * com.raimondarias.rlogin.paper.ServerTopology}. Cracked accounts are
 * completely unaffected by this class either way.
 *
 * <p>Technique (the same one AuthMeReloaded's PacketEvents-based "direct
 * offline-mode premium bypass" uses): intercept {@code LOGIN_START}, hold
 * it, and ask Mojang whether the connecting name is a real premium account
 * ({@link PremiumChecker}, the same lookup rlogin-velocity already does
 * for Modern Forwarding fallback). If not, the exact same login attempt is
 * silently re-injected — vanilla processes it exactly as if this listener
 * didn't exist, cracked accounts see zero difference. If it is premium,
 * this plugin performs the encryption handshake a proxy or an online-mode
 * server would normally do: send a synthetic {@code EncryptionRequest} (the
 * client shows its own "Encrypting..." screen and calls Mojang's
 * {@code /session/minecraft/join} on its own — nothing rLogin does induces
 * that, it's the vanilla client's standard behavior for this exchange),
 * decrypt the response, turn on AES/CFB8 encryption on the raw connection
 * ({@link CipherHandlers}), and verify the session with Mojang's
 * {@code hasJoined} ({@link MojangSessionVerifier}) — proving the
 * connecting client actually owns the account, not just that the username
 * exists. Only then is the username marked verified
 * ({@link HybridVerificationTracker}) and the login resumed.
 *
 * <p><b>Identity:</b> what Mojang returns in that last step is the account's
 * real UUID and its signed skin, and both are handed to the server through
 * {@link NmsConnectionAccess} before the login is resumed — so the player
 * lands with their genuine Mojang UUID and skin, exactly as if a proxy had
 * forwarded them (it's the same server-side mechanism). {@code JoinListener}
 * then recognises them as premium the same way it already recognises a
 * Velocity-forwarded connection: the UUID simply isn't the offline one.
 * {@link HybridVerificationTracker} stays as the backstop for server builds
 * where that identity can't be applied — there the player still auto-logins,
 * just with the offline UUID.</p>
 *
 * <p>Fails closed at every step: any error (Mojang timeout, decryption
 * failure, an unexpected client response) falls back to letting the
 * connection through as a normal cracked login — this never blocks a
 * connection, worst case it just doesn't get the premium fast-path for
 * that one attempt.</p>
 */
public final class HybridAuthListener extends PacketListenerAbstract {

    private record Pending(String username, String ip, byte[] verifyToken, ClientVersion clientVersion, UUID loginUuid) {
    }

    private final Logger logger;
    /**
     * Held instead of the services themselves: {@code /rlogin reload}
     * replaces them, and a listener that cached the old ones would go on
     * querying a database that has already been closed.
     */
    private final RLoginPaperPlugin plugin;
    private final MojangSessionVerifier sessionVerifier;
    private final HybridVerificationTracker tracker;
    private final NmsConnectionAccess connectionAccess;
    private final AbortedPremiumHandshakes aborted = new AbortedPremiumHandshakes();
    private final KeyPair serverKeyPair;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    private HybridAuthListener(RLoginPaperPlugin plugin, MojangSessionVerifier sessionVerifier,
                                HybridVerificationTracker tracker) throws Exception {
        this.logger = plugin.getLogger();
        this.plugin = plugin;
        this.sessionVerifier = sessionVerifier;
        this.tracker = tracker;
        this.connectionAccess = new NmsConnectionAccess(logger);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        this.serverKeyPair = generator.generateKeyPair();
    }

    /**
     * Builds and registers the listener with PacketEvents when this server
     * is the one that has to verify premium accounts; returns {@code null}
     * (does nothing else) when something else already did.
     *
     * <p>Deliberately the only place in this codebase that touches {@link
     * PacketEvents#getAPI()} directly outside this already-guarded class —
     * {@code RLoginPaperPlugin} never references PacketEvents types itself,
     * it only calls this.</p>
     */
    public static HybridAuthListener setUpIfNeeded(RLoginPaperPlugin plugin, PremiumChecker premiumChecker,
                                                     HybridVerificationTracker tracker) {
        if (!plugin.config().authMode().verifiesWithMojang()) {
            // auth-mode: offline. Nothing is checked against Mojang anywhere, so there is
            // no handshake to run and PacketEvents is not needed on this server.
            return null;
        }
        if (!plugin.topology().needsOwnVerification()) {
            // Someone already verified this connection (online-mode, or a proxy in front).
            // Doing it again from here would mean verifying twice, so there is nothing to set up
            // and PacketEvents is not needed on this server at all.
            return null;
        }
        try {
            MojangSessionVerifier sessionVerifier = new MojangSessionVerifier(plugin.config());
            HybridAuthListener listener = new HybridAuthListener(plugin, sessionVerifier, tracker);
            PacketEvents.getAPI().getEventManager().registerListener(listener);
            plugin.getLogger().info("Premium auto-login is active: premium accounts join without a password, "
                    + "cracked accounts use /login. UUID type: "
                    + plugin.config().uuidType().name().toLowerCase(Locale.ROOT) + ".");
            return listener;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not start premium verification: " + e);
            return null;
        }
    }

    /** Unregisters from PacketEvents and stops the Mojang HTTP client. Safe to call even if setup failed/was skipped. */
    public void shutdown() {
        try {
            PacketEvents.getAPI().getEventManager().unregisterListener(this);
        } catch (Exception ignored) {
            // Best-effort: the server is shutting down either way.
        }
        sessionVerifier.shutdown();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Login.Client.LOGIN_START) {
            onLoginStart(event);
        } else if (event.getPacketType() == PacketType.Login.Client.ENCRYPTION_RESPONSE) {
            onEncryptionResponse(event);
        }
    }

    private void onLoginStart(PacketReceiveEvent event) {
        WrapperLoginClientLoginStart packet = new WrapperLoginClientLoginStart(event);
        String username = packet.getUsername();
        User user = event.getUser();
        ClientVersion clientVersion = user.getClientVersion();
        UUID loginUuid = packet.getPlayerUUID().orElse(null); // 1.20.2+ only; forwarded as-is either way
        String ip = ipOf(user);
        String key = connectionKey(user);

        // Hold this connection here; re-injected below either unchanged (cracked) or once the
        // encryption handshake + Mojang verification finishes (premium).
        event.setCancelled(true);

        shouldAskForPremiumProof(username, ip).thenAccept(askForProof -> {
            if (!askForProof) {
                resumeLogin(user, username, clientVersion, loginUuid);
                return;
            }
            byte[] verifyToken = new byte[4];
            new SecureRandom().nextBytes(verifyToken);
            pending.put(key, new Pending(username, ip, verifyToken, clientVersion, loginUuid));
            user.sendPacket(new WrapperLoginServerEncryptionRequest("", serverKeyPair.getPublic(), verifyToken, true));
        }).exceptionally(e -> {
            logger.warning("[hybrid-auth] Could not decide how to handle " + username + ", treating as cracked: " + e);
            resumeLogin(user, username, clientVersion, loginUuid);
            return null;
        });
    }

    /**
     * Decides, <b>before</b> committing to an {@code EncryptionRequest},
     * whether this connection should be asked to prove it owns the name.
     *
     * <p>It has to be decided here and nowhere later: a client with no valid
     * Mojang session abandons the connection by itself once the request goes
     * out (see {@link AbortedPremiumHandshakes}), so there is no fallback
     * afterwards. The rules are ordered by how much each one actually
     * proves, strongest first:</p>
     *
     * <ol>
     *   <li>A verified premium account already exists for this name — its
     *       owner must always be able to get back in, even if somebody else
     *       has since registered that name as cracked. Checked first, and
     *       deliberately not overridable by anything below it.</li>
     *   <li>The last attempt from this exact address died mid-handshake:
     *       don't walk them into the same wall twice.</li>
     *   <li>A cracked account for this name, connecting from the address it
     *       last logged in from: that's the regular password user, so skip
     *       the handshake entirely rather than bouncing them every session.</li>
     *   <li>Nothing local applies — ask Mojang, and if the name is premium,
     *       let a first-time owner in with no typing at all.</li>
     * </ol>
     */
    private CompletableFuture<Boolean> shouldAskForPremiumProof(String username, String ip) {
        return plugin.accountService().findByUsername(username).thenCompose(existing -> {
            if (existing.isPresent() && existing.get().premium()) {
                return decided(username, true, "a premium account already exists for this name");
            }
            if (aborted.recentlyAbortedBy(username, ip)) {
                return decided(username, false, "their previous attempt from " + ip + " failed the handshake");
            }
            if (existing.isPresent() && ip.equals(existing.get().lastIp())) {
                return decided(username, false, "a cracked account for this name last logged in from " + ip);
            }
            return plugin.premiumChecker().lookup(username).thenCompose(lookup ->
                    decided(username, lookup.isPremium(), "Mojang says this name is "
                            + (lookup.isPremium() ? "premium" : lookup.status().name().toLowerCase(Locale.ROOT))));
        });
    }

    /** Records which rule of the decision table fired, which is the first thing worth knowing when a login misbehaves. */
    private CompletableFuture<Boolean> decided(String username, boolean askForProof, String because) {
        debug(username + ": " + (askForProof ? "asking for premium proof" : "treating as cracked") + " - " + because);
        return CompletableFuture.completedFuture(askForProof);
    }

    private void debug(String message) {
        if (plugin.config().debug()) {
            logger.info("[hybrid-auth][debug] " + message);
        }
    }

    /**
     * The only signal that a client couldn't authenticate: it hung up
     * between our {@code EncryptionRequest} and its {@code EncryptionResponse}.
     * A connection that got that far and then vanished is a client that
     * failed Mojang's {@code joinServer} on its own side.
     */
    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        Pending awaited = pending.remove(connectionKey(event.getUser()));
        if (awaited == null) {
            return; // Either not ours, or the handshake already completed and cleaned up.
        }
        aborted.record(awaited.username(), awaited.ip());
        aborted.purgeExpired();
        logger.info("[hybrid-auth] " + awaited.username() + " could not prove ownership of that premium name "
                + "(the client gave up before answering). Their next attempt from " + awaited.ip()
                + " will be served as a normal cracked login.");
    }

    private void onEncryptionResponse(PacketReceiveEvent event) {
        User user = event.getUser();
        Pending awaited = pending.remove(connectionKey(user));
        if (awaited == null) {
            return; // Not a handshake we started (or already handled) — leave it alone.
        }
        event.setCancelled(true);

        WrapperLoginClientEncryptionResponse response = new WrapperLoginClientEncryptionResponse(event);
        Optional<byte[]> encryptedVerifyToken = response.getEncryptedVerifyToken();
        if (encryptedVerifyToken.isEmpty()) {
            // Signed-nonce (chat-signing) variant instead of a plain verify token — we only
            // support the plain form; fall back rather than fail the connection.
            logger.warning("[hybrid-auth] " + awaited.username() + " responded with a signed nonce instead of "
                    + "a verify token; skipping premium verification for this connection.");
            resumeLogin(user, awaited.username(), awaited.clientVersion(), awaited.loginUuid());
            return;
        }

        SecretKey sharedSecret;
        byte[] decryptedVerifyToken;
        try {
            sharedSecret = response.getSecretKey(serverKeyPair.getPrivate());
            decryptedVerifyToken = MinecraftEncryptionUtil.decryptRSA(serverKeyPair.getPrivate(), encryptedVerifyToken.get());
        } catch (Exception e) {
            logger.warning("[hybrid-auth] Could not decrypt " + awaited.username() + "'s response, falling back to cracked login: " + e);
            resumeLogin(user, awaited.username(), awaited.clientVersion(), awaited.loginUuid());
            return;
        }
        if (!Arrays.equals(decryptedVerifyToken, awaited.verifyToken())) {
            logger.warning("[hybrid-auth] Verify token mismatch for " + awaited.username() + " - falling back to cracked login.");
            resumeLogin(user, awaited.username(), awaited.clientVersion(), awaited.loginUuid());
            return;
        }

        // The client already switches to encrypted mode the instant it sends this packet, so
        // our side must be ready before we send or receive anything else here — synchronous,
        // before the (async) Mojang round-trip below.
        if (!enableEncryption(user, sharedSecret)) {
            resumeLogin(user, awaited.username(), awaited.clientVersion(), awaited.loginUuid());
            return;
        }

        String serverIdHash = MojangSessionVerifier.serverIdHash("", sharedSecret, serverKeyPair.getPublic());
        sessionVerifier.hasJoined(awaited.username(), serverIdHash).thenAccept(verified -> {
            if (verified.isEmpty()) {
                logger.warning("[hybrid-auth] Mojang did not confirm " + awaited.username() + "'s session - resuming as cracked.");
                resumeLogin(user, awaited.username(), awaited.clientVersion(), awaited.loginUuid());
                return;
            }
            // Encryption is already on for this connection either way; the login continues
            // normally once the identity below is staged — and not a moment before, since
            // the server fixes the profile the instant it sees LOGIN_START again.
            applyVerifiedIdentity(user, awaited.username(), verified.get()).thenRun(() ->
                    reinjectLoginStart(user, awaited.username(), awaited.clientVersion(), awaited.loginUuid()));
        });
    }

    /**
     * Stages the identity Mojang just vouched for onto the connection, so the
     * server builds this player's profile from their real account rather than
     * from their name. Everything here has to happen before {@link
     * #resumeLogin} — once the re-injected LOGIN_START is processed, the
     * profile is already fixed.
     *
     * <p>The skin is applied whichever {@link UuidType} is in use: looking
     * like yourself doesn't depend on which UUID the server files you
     * under, and Mojang already handed it to us in the same response.</p>
     */
    private CompletableFuture<Void> applyVerifiedIdentity(User user, String username,
                                                           MojangSessionVerifier.VerifiedProfile profile) {
        plugin.premiumChecker().rememberPremium(username, profile.uuid());
        // Set unconditionally: this is what JoinListener falls back to on any server build
        // where the UUID below can't be applied (there, the player still auto-logins).
        tracker.markVerified(username);

        return identityFor(username, profile).thenAccept(identity -> {
            if (identity == null) {
                logger.info("[hybrid-auth] " + username + " verified as premium; keeping the offline UUID "
                        + "(uuid-type: cracked).");
                connectionAccess.applyIdentity(pipelineOf(user), null, profile.properties());
                return;
            }
            if (connectionAccess.applyIdentity(pipelineOf(user), identity, profile.properties())) {
                logger.info("[hybrid-auth] " + username + " verified as premium, joining as " + identity + ".");
            } else {
                logger.info("[hybrid-auth] " + username + " verified as premium, joining with their offline UUID "
                        + "(the intended one couldn't be applied on this server build).");
            }
        });
    }

    /**
     * The UUID this connection should join with, or null to leave the
     * server's own offline default alone.
     *
     * @param verified the Mojang profile if this connection proved ownership,
     *                 null for a cracked login — {@link UuidType#RANDOM}
     *                 applies to both, {@link UuidType#REAL} only to the former.
     */
    private CompletableFuture<UUID> identityFor(String username, MojangSessionVerifier.VerifiedProfile verified) {
        return switch (plugin.config().uuidType()) {
            case REAL -> CompletableFuture.completedFuture(verified == null ? null : verified.uuid());
            case CRACKED -> CompletableFuture.completedFuture(null);
            // Generated once per name and then read back off the account, so it survives
            // restarts without needing anywhere else to store it.
            case RANDOM -> plugin.accountService().findByUsername(username).thenApply(existing -> {
                UUID identity = existing.map(RLoginAccount::uuid).orElseGet(UUID::randomUUID);
                debug(username + ": uuid-type random -> " + identity
                        + (existing.isPresent() ? " (reused from their account)" : " (new, first time this name connects)"));
                return identity;
            });
        };
    }

    /** @return false if it failed (caller falls back to a normal, unencrypted cracked login). */
    private boolean enableEncryption(User user, SecretKey sharedSecret) {
        ChannelPipeline pipeline = pipelineOf(user);
        if (pipeline == null) {
            logger.warning("[hybrid-auth] This connection has no netty pipeline to encrypt.");
            return false;
        }
        // Preferred: hand the secret to the server itself, so the cipher handlers end up in
        // the same place, and are the same implementation, as in a real online-mode login.
        if (connectionAccess.enableEncryption(pipeline, sharedSecret)) {
            return true;
        }
        try {
            IvParameterSpec iv = new IvParameterSpec(sharedSecret.getEncoded());
            Cipher decryptCipher = Cipher.getInstance("AES/CFB8/NoPadding");
            decryptCipher.init(Cipher.DECRYPT_MODE, sharedSecret, iv);
            Cipher encryptCipher = Cipher.getInstance("AES/CFB8/NoPadding");
            encryptCipher.init(Cipher.ENCRYPT_MODE, sharedSecret, iv);

            pipeline.addBefore("splitter", "rlogin-decrypt", new CipherHandlers.Decrypt(decryptCipher));
            pipeline.addBefore("prepender", "rlogin-encrypt", new CipherHandlers.Encrypt(encryptCipher));
            return true;
        } catch (Exception e) {
            logger.warning("[hybrid-auth] Could not enable encryption on the connection: " + e);
            return false;
        }
    }

    private static ChannelPipeline pipelineOf(User user) {
        Object channel = user.getChannel();
        return channel == null ? null : (ChannelPipeline) ChannelHelper.getPipeline(channel);
    }

    /**
     * Resumes a login that is <em>not</em> getting the premium treatment —
     * either because it was never asked to prove anything, or because it
     * tried and failed. Under {@link UuidType#RANDOM} that still means
     * staging an identity first, since that mode applies to cracked players
     * too; every other mode leaves them on the server's own offline UUID.
     */
    private void resumeLogin(User user, String username, ClientVersion clientVersion, UUID loginUuid) {
        if (plugin.config().uuidType() != UuidType.RANDOM) {
            reinjectLoginStart(user, username, clientVersion, loginUuid);
            return;
        }
        identityFor(username, null).thenAccept(identity -> {
            connectionAccess.applyIdentity(pipelineOf(user), identity, List.of());
            reinjectLoginStart(user, username, clientVersion, loginUuid);
        });
    }

    /** Hands LOGIN_START back to the server so vanilla completes the login normally from here on. */
    private void reinjectLoginStart(User user, String username, ClientVersion clientVersion, UUID loginUuid) {
        user.receivePacketSilently(new WrapperLoginClientLoginStart(clientVersion, username, null, loginUuid));
    }

    private static String ipOf(User user) {
        InetSocketAddress address = user.getAddress();
        return address == null ? "unknown" : address.getAddress().getHostAddress();
    }

    /** Address+port, so two players behind the same IP are never confused for each other. */
    private static String connectionKey(User user) {
        InetSocketAddress address = user.getAddress();
        return address == null ? "unknown" : address.getAddress().getHostAddress() + ":" + address.getPort();
    }
}
