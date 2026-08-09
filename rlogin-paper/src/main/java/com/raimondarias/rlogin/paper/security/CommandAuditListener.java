package com.raimondarias.rlogin.paper.security;

import com.raimondarias.rlogin.common.security.SensitiveCommands;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.logging.Logger;

/**
 * Puts back what {@link CommandLogFilter} takes away.
 *
 * <p>The filter drops the server's own log line for any command carrying a
 * password, which would otherwise leave no trace at all that the command
 * ran — turning a password leak into a hole in the audit trail. So this
 * logs the same event with the arguments masked:</p>
 *
 * <pre>
 *   Steve issued server command: /register hunter2 hunter2   (dropped)
 *   [rLogin] Steve ran /register ***                         (logged instead)
 * </pre>
 *
 * <p>Runs at {@link EventPriority#MONITOR} and ignores cancelled events, so
 * it records what the server actually went on to execute rather than what
 * was typed — a command another plugin (or rLogin's own freeze) rejected is
 * not something an admin needs in the log.</p>
 *
 * <p>The wording is deliberately <em>not</em> the server's "issued server
 * command:", which is the exact string the filter matches on: keeping them
 * different is what makes it impossible for this line to be caught by the
 * filter it exists to compensate for.</p>
 */
public final class CommandAuditListener implements Listener {

    private final Logger logger;
    private final SensitiveCommands sensitiveCommands;

    public CommandAuditListener(Logger logger, SensitiveCommands sensitiveCommands) {
        this.logger = logger;
        this.sensitiveCommands = sensitiveCommands;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage();
        if (sensitiveCommands.revealsSecret(command)) {
            logger.info(event.getPlayer().getName() + " ran " + sensitiveCommands.mask(command));
        }
    }
}
