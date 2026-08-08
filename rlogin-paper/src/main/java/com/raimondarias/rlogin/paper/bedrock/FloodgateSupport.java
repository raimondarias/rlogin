package com.raimondarias.rlogin.paper.bedrock;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Soft integration (no hard dependency) with Floodgate: if the plugin is
 * present on the server, its API is used via reflection to tell whether a
 * player is coming from Bedrock and treat them as premium auto-login. If
 * it's not installed, this class simply does nothing.
 */
public final class FloodgateSupport {

    private final boolean available;
    private final Object api;
    private final Method isFloodgatePlayer;

    public FloodgateSupport() {
        Object apiInstance = null;
        Method method = null;
        boolean ok = false;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            apiInstance = getInstance.invoke(null);
            method = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            ok = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            // Floodgate isn't installed: that's fine, it's simply not used.
        }
        this.available = ok;
        this.api = apiInstance;
        this.isFloodgatePlayer = method;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isFloodgatePlayer(UUID uuid) {
        if (!available) {
            return false;
        }
        try {
            return (boolean) isFloodgatePlayer.invoke(api, uuid);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
