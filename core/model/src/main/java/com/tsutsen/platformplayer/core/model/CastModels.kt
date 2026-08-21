package com.tsutsen.platformplayer.core.model

/**
 * A cast receiver (discovered via NSD or added manually).
 *
 * @param name NSD name of the receiver
 * @param type "fcast", "chromecast" or "airplay"
 * @param addresses IP addresses the receiver was seen on
 * @param port the receiver's cast port
 */
data class CastDevice(
    val name: String,
    val type: String,
    val addresses: List<String>,
    val port: Int,
) {
    val id: String get() = name
}

/**
 * Snapshot of the casting subsystem, observed by the player UI.
 */
data class CastState(
    val isCasting: Boolean = false,
    val isConnecting: Boolean = false,
    val activeDevice: CastDevice? = null,
    val discoveredDevices: List<CastDevice> = emptyList(),
    val rememberedDevices: List<CastDevice> = emptyList(),
)
