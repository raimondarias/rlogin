package com.raimondarias.rlogin.paper.metrics;

import com.raimondarias.rlogin.paper.RLoginPaperPlugin;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

import java.util.Locale;

/**
 * Anonymous usage statistics via <a href="https://bstats.org">bStats</a>:
 * how many servers run rLogin, on what, and with which of its modes turned
 * on. No player data, no IPs, no account information — the charts below are
 * the complete list of what rLogin reports.
 *
 * <p>Opt-out lives in two places, and either one is enough: {@code
 * metrics.bstats} in rLogin's own config, and bStats' server-wide
 * {@code plugins/bStats/config.yml}.</p>
 */
public final class MetricsService {

    /** rLogin's own id at <a href="https://bstats.org">bstats.org</a>. */
    private static final int BSTATS_PLUGIN_ID = 33271;

    private MetricsService() {
    }

    /** No-ops when metrics are off in config, or in bStats' own server-wide config. */
    public static void startIfEnabled(RLoginPaperPlugin plugin) {
        if (!plugin.config().bstatsEnabled()) {
            return;
        }
        try {
            Metrics metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);
            metrics.addCustomChart(new SimplePie("database_type", () -> plugin.config().databaseType()));
            // Which fork people actually run. bStats already reports "Paper" for every
            // Paper-based server, which hides Purpur and Pufferfish entirely -- and
            // whether a fork-specific report is worth chasing depends on knowing that.
            metrics.addCustomChart(new SimplePie("server_software", () -> plugin.getServer().getName()));
            // Which of the three setups servers actually run, which is the single most
            // useful thing to know when deciding what to support.
            metrics.addCustomChart(new SimplePie("server_topology",
                    () -> plugin.topology().name().toLowerCase(Locale.ROOT)));
            metrics.addCustomChart(new SimplePie("auth_mode",
                    () -> plugin.config().authMode().name().toLowerCase(Locale.ROOT)));
            metrics.addCustomChart(new SimplePie("uuid_type",
                    () -> plugin.config().uuidType().name().toLowerCase(Locale.ROOT)));
            metrics.addCustomChart(new SimplePie("language", () -> plugin.config().language()));
        } catch (RuntimeException | LinkageError e) {
            // Statistics are never worth a failed startup.
            plugin.getLogger().warning("Could not start metrics: " + e);
        }
    }
}
