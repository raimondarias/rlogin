package com.raimondarias.rlogin.velocity.metrics;

import com.raimondarias.rlogin.common.config.RLoginConfig;
import org.bstats.velocity.Metrics;
import org.bstats.charts.SimplePie;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * Anonymous usage statistics via <a href="https://bstats.org">bStats</a> for
 * the proxy half of rLogin, which is registered separately from the Paper
 * one — a network of six backends and one proxy is one proxy install, and
 * counting it as seven would say something untrue about how many people run
 * this.
 *
 * <p>No player data, no IPs, no account information: the charts below are the
 * complete list of what is reported.</p>
 *
 * <p>Opt-out lives in two places, and either one is enough: {@code
 * metrics.bstats} in rLogin's proxy config, and bStats' own server-wide
 * config.</p>
 */
public final class VelocityMetricsService {

    /** rLogin (Velocity)'s own id at bstats.org, distinct from the Paper plugin's. */
    private static final int BSTATS_PLUGIN_ID = 33272;

    private VelocityMetricsService() {
    }

    /** No-ops when metrics are off in config, or in bStats' own config. */
    public static void startIfEnabled(Metrics.Factory factory, Object plugin, RLoginConfig config, Logger logger) {
        if (!config.bstatsEnabled()) {
            return;
        }
        try {
            Metrics metrics = factory.make(plugin, BSTATS_PLUGIN_ID);
            metrics.addCustomChart(new SimplePie("auth_mode",
                    () -> config.authMode().name().toLowerCase(Locale.ROOT)));
            // What networks actually do with a player once they are in, which is the
            // part of the proxy config there is any real choice about.
            metrics.addCustomChart(new SimplePie("after_login",
                    () -> config.afterLoginAction().name().toLowerCase(Locale.ROOT)));
            metrics.addCustomChart(new SimplePie("login_servers",
                    () -> String.valueOf(config.loginServers().size())));
        } catch (RuntimeException | LinkageError e) {
            // Statistics are never worth a failed startup.
            logger.warn("Could not start metrics: {}", e.toString());
        }
    }
}
