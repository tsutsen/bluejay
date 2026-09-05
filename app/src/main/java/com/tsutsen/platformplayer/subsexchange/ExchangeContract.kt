package com.tsutsen.platformplayer.subsexchange

import com.tsutsen.platformplayer.serializers.OffsetDateTimeStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
class ExchangeContract(
    @SerialName("ID")
    var id: String,
    @SerialName("Requests")
    var requests: List<ChannelRequest>,
    @SerialName("Provided")
    var provided: List<String> = listOf(),
    @SerialName("Required")
    var required: List<String> = listOf(),
    @SerialName("Expire")
    @kotlinx.serialization.Serializable(with = OffsetDateTimeStringSerializer::class)
    var expired: OffsetDateTime = OffsetDateTime.MIN,
    @SerialName("ContractVersion")
    var contractVersion: Int = 1
)