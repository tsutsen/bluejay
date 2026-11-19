package com.tsutsen.platformplayer.sabr

import android.util.Base64
import com.google.protobuf.ByteString
import com.tsutsen.platformplayer.logging.Logger

/**
 * Lazily decodes the po token exactly once. Verbatim from SabrSession's
 * resolvePoToken/decodeBase64Lenient.
 */
internal class SabrPoTokenResolver(private val poTokenRaw: String?) {
    @Volatile private var poTokenDecoded: ByteString? = null
    @Volatile private var poTokenResolved = false

    fun resolve(): ByteString? {
        if (poTokenResolved) return poTokenDecoded
        poTokenResolved = true

        val token = poTokenRaw
        if (token == null) {
            SabrSession.sabrLog("poToken: none set on source")
            return null
        }

        poTokenDecoded = decodeBase64Lenient(token)?.let { ByteString.copyFrom(it) }
        if (poTokenDecoded == null)
            Logger.e(TAG, "Po token is not valid base64; requests will be unattested and YouTube will " +
                "block the session once the attestation grace period expires")

        SabrSession.sabrLog("poToken: len=${token.length} decodedBytes=${poTokenDecoded?.size() ?: 0} prefix='${token.take(12)}'")
        return poTokenDecoded
    }

    private fun decodeBase64Lenient(value: String): ByteArray? {
        val flags = Base64.NO_WRAP or Base64.NO_PADDING
        try {
            return Base64.decode(value, Base64.URL_SAFE or flags)
        } catch (_: Throwable) {
        }
        return try {
            Base64.decode(value.replace('-', '+').replace('_', '/'), Base64.DEFAULT)
        } catch (ex: Throwable) {
            Logger.w(TAG, "Not valid base64 in either alphabet: '${value.take(16)}...' (${value.length} chars)", ex)
            null
        }
    }

    companion object {
        private const val TAG = "SabrSession"
    }
}
