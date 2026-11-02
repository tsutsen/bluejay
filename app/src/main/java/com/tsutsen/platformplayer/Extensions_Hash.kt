package com.tsutsen.platformplayer

/**
 * Local replacements for helper extensions that went away with the removal of
 * the bundled social-identity dependencies. Generic base64/hex/hash helpers.
 */

/** Standard Kotlin-style ordered combine (base * 31 + next), null-safe. */
fun combineHashCodes(hashes: List<Int?>): Int {
    var result = hashes.firstOrNull() ?: 0
    for (i in 1 until hashes.size) {
        result = result * 31 + (hashes[i] ?: 0)
    }
    return result
}

/** Decodes an uppercase/lowercase hex string into bytes. */
fun String.hexStringToByteArray(): ByteArray {
    require(length % 2 == 0) { "Hex string must have an even length" }
    return ByteArray(length / 2) { i ->
        ((Character.digit(this[i * 2], 16) shl 4) + Character.digit(this[i * 2 + 1], 16)).toByte()
    }
}
