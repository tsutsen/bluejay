package com.tsutsen.platformplayer.core.model

/**
 * An enabled content source (client/plugin): id is the client id, name the
 * display name, iconUrl the plugin's own icon (may be null).
 */
data class SourceInfo(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
)
