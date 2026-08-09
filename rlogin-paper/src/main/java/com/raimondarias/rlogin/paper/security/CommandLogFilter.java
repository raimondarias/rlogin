package com.raimondarias.rlogin.paper.security;

import com.raimondarias.rlogin.common.security.SensitiveCommands;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ParameterizedMessage;

/**
 * Stops passwords from reaching the console and {@code logs/latest.log}.
 *
 * <p>The server logs every command a player runs, verbatim and in the clear
 * — {@code Steve issued server command: /register hunter2 hunter2} — and it
 * does so <b>before</b> firing {@code PlayerCommandPreprocessEvent}:</p>
 *
 * <pre>
 *   if (SpigotConfig.logCommands) {
 *       LOGGER.info("{} issued server command: {}", player.getScoreboardName(), command);
 *   }
 *   // ...only now is the event plugins can cancel fired
 * </pre>
 *
 * <p>So there is nothing to cancel and no event to intercept: by the time
 * any plugin is consulted, the password has already been written. The only
 * place left to stop it is the logging pipeline itself, which is what this
 * is — a Log4j2 filter on the root logger, the same approach AuthMe has
 * used for years. It runs for every log record on the server, so it is
 * written to reject non-matching records on a single {@code indexOf} before
 * doing anything else.</p>
 *
 * <p>Only the offending record is dropped, never the audit trail:
 * {@link CommandAuditListener} re-logs the same command with its arguments
 * masked, so an admin still sees who ran what.</p>
 *
 * <p><b>Installed once per JVM and never removed:</b> Log4j2's
 * {@code Logger} offers {@code addFilter} but no {@code removeFilter}, so
 * on plugin disable the filter stays attached and is switched off with
 * {@link #setActive} instead — and a re-enable reuses the same instance
 * rather than stacking a second one.</p>
 */
public final class CommandLogFilter extends AbstractFilter {

    private static CommandLogFilter installed;

    private final SensitiveCommands sensitiveCommands;
    private volatile boolean active = true;

    private CommandLogFilter(SensitiveCommands sensitiveCommands) {
        this.sensitiveCommands = sensitiveCommands;
    }

    /**
     * Attaches the filter to the root logger the first time, and re-arms the
     * existing one on any later call.
     *
     * @return false if this server's logging backend isn't Log4j2 — the
     *         caller should then warn that passwords stay visible, because
     *         silently doing nothing about a leak is worse than the leak.
     */
    public static synchronized boolean install(SensitiveCommands sensitiveCommands) {
        if (installed != null) {
            installed.setActive(true);
            return true;
        }
        try {
            Logger rootLogger = (Logger) LogManager.getRootLogger();
            CommandLogFilter filter = new CommandLogFilter(sensitiveCommands);
            rootLogger.addFilter(filter);
            installed = filter;
            return true;
        } catch (ClassCastException | LinkageError e) {
            return false;
        }
    }

    /** Switches the installed filter off (or back on); safe when nothing was installed. */
    public static synchronized void setInstalledActive(boolean active) {
        if (installed != null) {
            installed.setActive(active);
        }
    }

    private void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public Result filter(LogEvent event) {
        return event == null ? Result.NEUTRAL : decide(event.getMessage());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message message, Throwable t) {
        return decide(message);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object message, Throwable t) {
        return decide(message == null ? null : message.toString());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String message, Object... params) {
        // The server logs this one parameterized, so the password is in params, not in the format.
        return isCandidate(message) ? decide(new ParameterizedMessage(message, params).getFormattedMessage())
                : Result.NEUTRAL;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1) {
        return filter(logger, level, marker, message, new Object[]{p0, p1});
    }

    private Result decide(Message message) {
        if (message == null) {
            return Result.NEUTRAL;
        }
        String format = message.getFormat();
        // A null format (reusable/pre-formatted messages) means the cheap check can't be
        // trusted, so fall through to the formatted text rather than skipping the record.
        if (format != null && !isCandidate(format)) {
            return Result.NEUTRAL;
        }
        return decide(message.getFormattedMessage());
    }

    private Result decide(String formattedMessage) {
        if (!active || formattedMessage == null) {
            return Result.NEUTRAL;
        }
        String command = SensitiveCommands.commandInLogMessage(formattedMessage);
        return command != null && sensitiveCommands.revealsSecret(command) ? Result.DENY : Result.NEUTRAL;
    }

    /**
     * Cheap pre-check so the overwhelming majority of log records — every
     * single one this server writes that isn't a command — cost one
     * substring search and nothing more.
     */
    private boolean isCandidate(String rawOrFormat) {
        return active && rawOrFormat != null && rawOrFormat.contains("issued server command");
    }
}
