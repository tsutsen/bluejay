package com.tsutsen.platformplayer.core.data.playback

/**
 * Marker for playback source errors that a fresh resolution can recover
 * from.
 *
 * The SABR engine marks po-token rejections (`SabrBlockedException`) and
 * server-requested player-response reloads (`SabrReloadRequiredException`)
 * with this interface: a re-resolve of the same video mints a fresh
 * attestation and playback can continue at the same position.
 * `PlayerRepositoryImpl` reacts to these by silently re-resolving instead
 * of surfacing a dead player.
 *
 * Lives in core:data (not the sabr package) because the player repository
 * must detect it without depending on the app module.
 */
interface RecoverableSourceError
