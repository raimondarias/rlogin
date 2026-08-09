package com.raimondarias.rlogin.common.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Knows which command lines carry a secret — a password, a password
 * confirmation, or a TOTP code/secret — and how to render one safely.
 *
 * <p>This exists because the server writes every command a player runs
 * straight to the console and to {@code logs/latest.log}, verbatim:
 * {@code Steve issued server command: /register hunter2 hunter2}. That line
 * is emitted <em>before</em> {@code PlayerCommandPreprocessEvent} is fired,
 * so no plugin can prevent it by cancelling the command — it has to be
 * stopped at the logging layer instead (see {@code CommandLogFilter} in
 * rlogin-paper), and this class is the part that decides what to stop.</p>
 *
 * <p>Pure string logic on purpose: no Bukkit, no logging framework, so the
 * rules can be tested directly. The platform passes in the real aliases the
 * server registered, so renaming a command in {@code plugin.yml} can't
 * quietly leave a hole here.</p>
 */
public final class SensitiveCommands {

    /** Exactly what the server prints between the player's name and the command. */
    private static final String LOG_MARKER = " issued server command: ";

    /** What the arguments are replaced with; short, and obviously not a password. */
    public static final String MASK = "***";

    /** Base names of the commands that take a secret as an argument. */
    public static final List<String> PASSWORD_COMMANDS =
            List.of("login", "register", "changepassword", "2fa");

    /** The umbrella command: only sensitive when followed by one of {@link #PASSWORD_COMMANDS}. */
    public static final List<String> ROOT_COMMANDS = List.of("rlogin");

    private final Set<String> passwordLabels;
    private final Set<String> rootLabels;

    private SensitiveCommands(Set<String> passwordLabels, Set<String> rootLabels) {
        this.passwordLabels = passwordLabels;
        this.rootLabels = rootLabels;
    }

    /**
     * @param passwordLabels every label (name <em>and</em> alias) that takes a secret directly
     * @param rootLabels     every label of the umbrella command, e.g. {@code rlogin} and {@code rl}
     */
    public static SensitiveCommands of(Collection<String> passwordLabels, Collection<String> rootLabels) {
        return new SensitiveCommands(lowercased(passwordLabels), lowercased(rootLabels));
    }

    /** Fallback when the platform can't be asked: the names and aliases rLogin ships with. */
    public static SensitiveCommands withDefaults() {
        return of(List.of("login", "l", "register", "reg", "changepassword", "changepass", "2fa"),
                List.of("rlogin", "rl"));
    }

    /**
     * Whether this command line would put a secret in the log.
     *
     * <p>A bare {@code /login} with no arguments is not sensitive — there's
     * nothing in it to leak, and keeping those visible means the log still
     * shows that someone is trying to authenticate.</p>
     *
     * @param commandLine with or without the leading slash, e.g. {@code /login hunter2}
     */
    public boolean revealsSecret(String commandLine) {
        String[] parts = split(commandLine);
        if (parts.length < 2) {
            return false; // No arguments -> no secret.
        }
        String label = parts[0];
        if (passwordLabels.contains(label)) {
            return true;
        }
        // "/rlogin login <password>" reaches the same code as "/login <password>",
        // so it has to be caught here too — but "/rlogin reload" must stay readable.
        return rootLabels.contains(label) && parts.length >= 3 && PASSWORD_COMMANDS.contains(parts[1]);
    }

    /** The same line with every argument replaced by {@link #MASK}; the command itself stays readable. */
    public String mask(String commandLine) {
        String[] parts = split(commandLine);
        if (parts.length < 2) {
            return commandLine;
        }
        String prefix = commandLine.startsWith("/") ? "/" : "";
        if (rootLabels.contains(parts[0]) && PASSWORD_COMMANDS.contains(parts[1])) {
            return prefix + parts[0] + " " + parts[1] + " " + MASK;
        }
        return prefix + parts[0] + " " + MASK;
    }

    /**
     * Pulls the command out of a server log line, or null if that line isn't
     * one. Matching on the server's own wording keeps this independent of
     * which logger or format the fork happens to use.
     */
    public static String commandInLogMessage(String logMessage) {
        if (logMessage == null) {
            return null;
        }
        int at = logMessage.indexOf(LOG_MARKER);
        return at < 0 ? null : logMessage.substring(at + LOG_MARKER.length());
    }

    /** Splits into [label, arg, arg...] with the slash and any {@code plugin:} namespace stripped. */
    private static String[] split(String commandLine) {
        String line = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        String[] parts = line.trim().split("\\s+");
        if (parts.length > 0) {
            // "/minecraft:login pw" and "/rlogin:login pw" run the same command as "/login pw".
            int namespace = parts[0].indexOf(':');
            parts[0] = parts[0].substring(namespace + 1).toLowerCase(Locale.ROOT);
        }
        if (parts.length > 1) {
            parts[1] = parts[1].toLowerCase(Locale.ROOT);
        }
        return parts;
    }

    private static Set<String> lowercased(Collection<String> values) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(value.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(result);
    }
}
