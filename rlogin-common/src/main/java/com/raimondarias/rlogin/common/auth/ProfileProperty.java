package com.raimondarias.rlogin.common.auth;

/**
 * One signed property of a Mojang profile, as returned by {@code hasJoined}
 * — in practice always {@code textures} (the player's real premium skin and
 * cape, base64-encoded, plus Mojang's signature over it).
 *
 * <p>Kept as a plain record here so {@code rlogin-common} stays free of any
 * server-specific type: {@code rlogin-paper} converts these into authlib's
 * own {@code Property} at the last moment, reflectively.</p>
 *
 * @param signature Mojang's signature over {@code value}; null for an
 *                  unsigned property (the client rejects unsigned textures,
 *                  so in practice this is only null for other properties).
 */
public record ProfileProperty(String name, String value, String signature) {
}
