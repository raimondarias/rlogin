package com.raimondarias.rlogin.api;

/** Por qué se considera autenticado a un jugador en un momento dado. */
public enum AuthReason {
    /** Velocity verificó la cuenta contra Mojang vía Modern Forwarding (forceOnlineMode). */
    PREMIUM_FORWARDED,
    /** Se comprobó como premium consultando directamente la API de Mojang (modo standalone). */
    PREMIUM_MOJANG_API,
    /** El servidor entero corre en online-mode: true; todos los que llegan ya están verificados. */
    PREMIUM_SERVER_ONLINE_MODE,
    /** Jugador Bedrock autenticado vía Floodgate. */
    FLOODGATE,
    /** Escribió /login con la contraseña correcta. */
    PASSWORD,
    /** Sesión "recuérdame" válida por IP+UUID dentro de la ventana configurada. */
    REMEMBERED_SESSION,
    /** Un administrador forzó el login manualmente. */
    FORCED_BY_ADMIN
}
