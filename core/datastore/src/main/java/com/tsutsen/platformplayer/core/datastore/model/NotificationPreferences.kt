package com.tsutsen.platformplayer.core.datastore.model

data class NotificationPreferences(
    /** Schedule a notification for planned (upcoming) content. */
    val plannedContentNotification: Boolean = false,
)
