package com.raimondarias.rlogin.paper.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player creates an account with {@code /register}, once and
 * only on the server where they typed it.
 *
 * <p>Distinct from {@link RLoginAuthenticateEvent}, which also fires for this
 * player a moment earlier: this one marks a genuinely new account, which is
 * what you want for a starter kit, a welcome broadcast, or a first-join
 * tutorial. Premium players never fire it — they are let in without ever
 * registering — so treat it as "new password account", not "new player".</p>
 *
 * <p>Not cancellable: the account exists by the time listeners run. Fired on
 * the player's own thread.</p>
 */
public final class RLoginRegisterEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;

    public RLoginRegisterEvent(Player player) {
        this.player = player;
    }

    public Player player() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
