package com.tsutsen.platformplayer.downloads

import android.content.Context
import com.tsutsen.platformplayer.api.http.ManagedHttpClient
import com.tsutsen.platformplayer.api.media.models.modifier.IRequestModifier
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Per-download dependencies shared by the strategy adapters.
 *
 * @property modifier plugin request modifier (auth headers/cookies), if any
 * @property isCancelled checked between chunks/segments
 */
class SegmentDownloadContext(
    val context: Context,
    val client: ManagedHttpClient,
    val modifier: IRequestModifier?,
    val isCancelled: () -> Boolean,
)

/**
 * One download strategy (HLS segments, DASH segments, UMP/SABR tracks, or a
 * progressive/byte-range file).
 */
interface SegmentDownloader {
    /** Downloads into [target] and returns the bytes written. */
    suspend fun download(target: File, onProgress: (Long, Long, Long) -> Unit): Long
}

/** AES-CBC segment decryption shared by the HLS and progressive strategies. */
internal fun decryptSegment(encryptedSegment: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val secretKey = SecretKeySpec(key, "AES")
    val ivSpec = IvParameterSpec(iv)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
    return cipher.doFinal(encryptedSegment)
}
