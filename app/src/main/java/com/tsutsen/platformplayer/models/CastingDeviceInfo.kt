package com.tsutsen.platformplayer.models

import com.tsutsen.platformplayer.casting.CastProtocolType

@kotlinx.serialization.Serializable
class CastingDeviceInfo(
    var name: String,
    var type: CastProtocolType,
    var addresses: Array<String>,
    var port: Int
)