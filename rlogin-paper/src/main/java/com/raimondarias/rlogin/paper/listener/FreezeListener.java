package com.raimondarias.rlogin.paper.listener;

import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Congela a los jugadores no autenticados: pueden ver el mundo pero no
 * moverse (salvo mirar alrededor), interactuar, recibir/hacer daño, abrir
 * inventarios ni escribir comandos fuera de la lista blanca de
 * {@code limbo.allowed-commands}. Se puede desactivar del todo con
 * {@code limbo.freeze: false} en config.yml.
 */
public final class FreezeListener implements Listener {

    private final RLoginPaperPlugin plugin;

    public FreezeListener(RLoginPaperPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isFrozen(UUID uuid) {
        return plugin.config().limboFreeze() && !plugin.authSessions().isAuthenticated(uuid);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.messages().get("limbo.action-blocked"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        String usedLabel = event.getMessage().split(" ")[0].toLowerCase(Locale.ROOT);
        List<String> allowed = plugin.config().limboAllowedCommands();
        boolean permitted = usedLabel.equals("/rlogin")
                || allowed.stream().anyMatch(cmd -> cmd.toLowerCase(Locale.ROOT).equals(usedLabel));
        if (!permitted) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.messages().get("limbo.action-blocked"));
        }
    }
}
