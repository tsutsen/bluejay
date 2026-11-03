package com.tsutsen.platformplayer.core.datastore.model

data class OtherPreferences(
    /** Add newly saved videos to the start of Watch Later instead of the end. */
    val watchLaterAddStart: Boolean = false,
    /** Allow adding a video to a playlist it is already in. */
    val playlistAllowDups: Boolean = false,
)
