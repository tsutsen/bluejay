package com.tsutsen.platformplayer.core.datastore.model

data class BrowsingPreferences(
    /** Trust the CA bundle shipped with downloaded plugins. */
    val useDownloadedCABundle: Boolean = false,
    /** In-memory cache of platform video details. */
    val videoCache: Boolean = true,
)
