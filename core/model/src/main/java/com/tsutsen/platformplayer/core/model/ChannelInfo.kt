package com.tsutsen.platformplayer.core.model

/**
 * Engine-agnostic channel description.
 */
data class ChannelInfo(
    val url: String,
    val name: String,
    val thumbnail: String?,
    val banner: String?,
    val subscribers: Long,
    val description: String?,
    val links: Map<String, String>,
    val isSubscribed: Boolean,
    val notifyEnabled: Boolean = false,
    /**
     * Icon of the source (plugin) this channel comes from — null when the
     * app has a single enabled source (the badge is pointless then).
     */
    val sourceIconUrl: String? = null,
)
