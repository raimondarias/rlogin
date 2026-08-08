package com.raimondarias.rlogin.paper.bedrock;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Integración blanda (sin dependencia obligatoria) con Floodgate: si el
 * plugin está presente en el servidor, se usa su API vía reflexión para
 * saber si un jugador viene de Bedrock y tratarlo como premium auto-login.
 * Si no está instalado, esta clase simplemente no hace nada.
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
            // Floodgate no está instalado: no pasa nada, simplemente no se usa.
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
