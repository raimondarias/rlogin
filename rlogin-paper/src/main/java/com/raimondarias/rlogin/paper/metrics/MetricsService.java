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

    /**
     * The id bStats assigns when the plugin is registered at
     * <a href="https://bstats.org/getting-started">bstats.org</a>.
     *
     * <p>Left unset on purpose: rLogin hasn't been registered there yet, and
     * shipping someone else's id would file this server's stats under
     * someone else's plugin. Until it's replaced with the real number,
     * {@link #startIfEnabled} does nothing at all.</p>
     */
    private static final int BSTATS_PLUGIN_ID = 0;

    private MetricsService() {
    }

    /** No-ops when metrics are off in config, or while {@link #BSTATS_PLUGIN_ID} is still unset. */
    public static void startIfEnabled(RLoginPaperPlugin plugin) {
        if (!plugin.config().bstatsEnabled()) {
            return;
        }
        if (BSTATS_PLUGIN_ID <= 0) {
            plugin.getLogger().info("Metrics are enabled in config but rLogin has no bStats plugin id yet, "
                    + "so nothing is being sent. (Register at bstats.org and set BSTATS_PLUGIN_ID.)");
            return;
        }
        try {
            Metrics metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);
            metrics.addCustomChart(new SimplePie("database_type", () -> plugin.config().databaseType()));
            metrics.addCustomChart(new SimplePie("standalone_hybrid_mode",
                    () -> String.valueOf(plugin.config().standaloneHybridModeEnabled())));
            metrics.addCustomChart(new SimplePie("uuid_type",
                    () -> plugin.config().uuidType().name().toLowerCase(Locale.ROOT)));
            metrics.addCustomChart(new SimplePie("language", () -> plugin.config().language()));
        } catch (RuntimeException | LinkageError e) {
            // Statistics are never worth a failed startup.
            plugin.getLogger().warning("Could not start metrics: " + e);
        }
    }
}
