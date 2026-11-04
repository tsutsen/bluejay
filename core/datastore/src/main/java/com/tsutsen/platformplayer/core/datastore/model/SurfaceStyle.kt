package com.tsutsen.platformplayer.core.datastore.model

import kotlinx.serialization.Serializable

/**
 * User overrides for one named surface (Settings > Appearance > Surface
 * styles). Null fields fall through to the active theme (Material You /
 * custom theme). Textures are a later phase; this carries color + outline.
 * Surface names live in
 * [com.tsutsen.platformplayer.core.designsystem.theme.SurfaceKeys].
 */
@Serializable
data class SurfaceStyle(
    /** Container color override (ARGB), null = theme default. */
    val colorArgb: Long? = null,
    /** Border color override (ARGB); null = no outline unless width set. */
    val outlineColorArgb: Long? = null,
    /** Border width in dp; 0/absent = no outline. */
    val outlineWidthDp: Float? = null,
)
