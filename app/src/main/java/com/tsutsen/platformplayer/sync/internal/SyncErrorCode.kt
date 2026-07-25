package com.tsutsen.platformplayer.sync.internal

enum class SyncErrorCode(val value: Int) {
    ConnectionClosed(1),
    NotFound(2)
}