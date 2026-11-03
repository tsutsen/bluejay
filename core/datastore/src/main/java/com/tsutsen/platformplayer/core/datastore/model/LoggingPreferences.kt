package com.tsutsen.platformplayer.core.datastore.model

data class LoggingPreferences(
    /** File-log level (0=NONE .. 4=VERBOSE). The legacy Settings default is 3 on debug, 0 on release builds. */
    val logLevel: Int = 0,
)
