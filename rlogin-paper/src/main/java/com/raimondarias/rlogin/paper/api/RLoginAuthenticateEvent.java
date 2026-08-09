package com.raimondarias.rlogin.paper.api;

import com.raimondarias.rlogin.api.AuthReason;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired once a player is authenticated and free to play, whatever got them
 * there — a password, a premium handshake, a restored session, a recovery
 * code, or an administrator.
 *
 * <p>This is the moment other plugins actually want. Listening to
 * {@code PlayerJoinEvent} on a server running rLogin gives you a player who
 * is frozen and cannot use what you hand them; by the time this fires, they
 * can. Give kits here, restore inventories here, start your scoreboard here.</p>
 *
 * <p><b>Not cancellable</b>, deliberately. It reports something that already
 * happened — the session is open before any listener runs — and an event that
 * looked cancellable but left the player authenticated would be worse than
 * one that never offered. To refuse a player, use the permission system or
 * kick them from this event.</p>
 *
 * <p>Always fired on the player's own thread, so it is safe to touch the
 * world from a listener, on Folia as well as Paper.</p>
 *
 * <pre>{@code
 * @EventHandler
 * public void onAuthenticate(RLoginAuthenticateEvent event) {
 *     if (event.reason().isAutomaticPremium()) {
 *         event.player().sendMessage("Welcome back, premium player.");
 *     }
 * }
 * }</pre>
 */
public final class RLoginAuthenticateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final AuthReason reason;
    private final boolean firstServerOfSession;

    public RLoginAuthenticateEvent(Player player, AuthReason reason, boolean firstServerOfSession) {
        this.player = player;
        this.reason = reason;
        this.firstServerOfSession = firstServerOfSession;
    }

    public Player player() {
        return player;
    }

    /** Which of the several ways in was used. Never null. */
    public AuthReason reason() {
        return reason;
    }

    /**
     * Whether the player has just arrived on the network, as opposed to
     * switching to this server from another backend.
     *
     * <p>Worth checking before anything a player should see once: on a proxy
     * network this event fires on every backend they land on, and a welcome
     * message that ignores this greets them again on each hop. Always
     * {@code true} on a standalone server.</p>
     */
    public boolean isFirstServerOfSession() {
        return firstServerOfSession;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
