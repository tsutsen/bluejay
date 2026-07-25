package com.tsutsen.platformplayer.sync.internal

enum class ContentEncoding(val value: UByte) {
    Raw(0u),
    Gzip(1u)
}