package com.raimondarias.rlogin.paper.integration;

import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.node.Node;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Everything that touches LuckPerms types, kept in one class so the rest of
 * rLogin never loads them. Constructed only by {@link LuckPermsSupport},
 * after it has confirmed the plugin is installed.
 *
 * <p>Two things are worth integrating, and this does both.</p>
 *
 * <p><b>A context for whether the player has logged in.</b> A frozen player
 * still <em>has</em> every permission their rank grants. rLogin blocks its own
 * commands and movement, but any other plugin that asks LuckPerms directly
 * sees a fully-privileged player who has not proved who they are. The
 * {@code rlogin:authenticated} context lets an owner scope permissions to
 * people who actually logged in, which is a thing rLogin cannot do for them
 * from the outside.</p>
 *
 * <p><b>Carrying a rank across a UUID change.</b> {@code /rlogin changeuuid}
 * moves the rLogin account; LuckPerms keys its data by UUID, so without this
 * the player arrives under their new identity with no rank. That gap was
 * already documented in the command's own warning — this closes it.</p>
 */
final class LuckPermsHook {

    /** Namespaced so it cannot collide with a server's own context keys. */
    static final String CONTEXT_KEY = "rlogin:authenticated";

    private final RLoginPaperPlugin plugin;
    private final LuckPerms luckPerms;
    private final ContextCalculator<Player> calculator;

    LuckPermsHook(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
        this.luckPerms = LuckPermsProvider.get();
        this.calculator = new ContextCalculator<>() {
            @Override
            public void calculate(Player target, ContextConsumer consumer) {
                consumer.accept(CONTEXT_KEY,
                        String.valueOf(plugin.authSessions().isAuthenticated(target.getUniqueId())));
            }

            @Override
            public ContextSet estimatePotentialContexts() {
                return ImmutableContextSet.builder()
                        .add(CONTEXT_KEY, "true")
                        .add(CONTEXT_KEY, "false")
                        .build();
            }
        };
        luckPerms.getContextManager().registerCalculator(calculator);
    }

    /**
     * Tells LuckPerms the answer changed. Without this the context is only
     * recalculated on its own schedule, so a player who just logged in would
     * keep being treated as unauthenticated for a while — which is the exact
     * window this is meant to close.
     */
    void refreshContext(Player player) {
        try {
            luckPerms.getContextManager().signalContextUpdate(player);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not refresh the LuckPerms context: " + e);
        }
    }

    /**
     * Copies a player's LuckPerms data onto another UUID.
     *
     * <p>Copied rather than moved: the source is left alone. If something is
     * wrong with the target — the wrong UUID typed, the wrong player — the
     * original rank is still where it was, and deleting it is a decision an
     * administrator can make afterwards with LuckPerms' own commands.</p>
     *
     * <p>Refuses when the target already carries permissions of its own, so
     * an accidental changeuuid cannot quietly overwrite somebody's rank.</p>
     */
    CompletableFuture<LuckPermsSupport.TransferResult> transfer(UUID from, UUID to) {
        return luckPerms.getUserManager().loadUser(from).thenCompose(source -> {
            if (source.getNodes().isEmpty() && source.getPrimaryGroup().equals("default")) {
                return CompletableFuture.completedFuture(LuckPermsSupport.TransferResult.NOTHING_TO_MOVE);
            }
            return luckPerms.getUserManager().loadUser(to).thenCompose(target -> {
                if (!target.getNodes().isEmpty() || !target.getPrimaryGroup().equals("default")) {
                    return CompletableFuture.completedFuture(LuckPermsSupport.TransferResult.TARGET_NOT_EMPTY);
                }
                for (Node node : source.getNodes()) {
                    target.data().add(node);
                }
                target.setPrimaryGroup(source.getPrimaryGroup());
                return luckPerms.getUserManager().saveUser(target)
                        .thenApply(ignored -> LuckPermsSupport.TransferResult.MOVED);
            });
        }).exceptionally(error -> {
            plugin.getLogger().warning("Could not copy LuckPerms data: " + error);
            return LuckPermsSupport.TransferResult.FAILED;
        });
    }

    void shutdown() {
        try {
            luckPerms.getContextManager().unregisterCalculator(calculator);
        } catch (RuntimeException e) {
            // Shutting down anyway; a failure here has nowhere useful to go.
        }
    }
}
