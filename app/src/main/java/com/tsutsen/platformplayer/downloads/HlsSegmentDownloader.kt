package com.tsutsen.platformplayer.downloads

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.StatisticsCallback
import com.futo.polycentric.core.hexStringToByteArray
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.parsers.HLS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resumeWithException

/** HLS download strategy: fetches segments (decrypting AES-128 when needed) and concatenates them. */
class HlsSegmentDownloader(
    private val ctx: SegmentDownloadContext,
    private val name: String,
    private val hlsUrl: String,
) : SegmentDownloader {
    private suspend fun combineSegments(context: Context, segmentFiles: List<File>, targetFile: File) = withContext(Dispatchers.IO) {
        require(segmentFiles.isNotEmpty()) { "segmentFiles must not be empty" }

        suspendCancellableCoroutine { continuation ->
            val concatInput = buildString {
                append("concat:")
                append(
                    segmentFiles.joinToString("|") { file ->
                        file.absolutePath
                    }
                )
            }

            val cmd = "-i \"$concatInput\" -c copy \"${targetFile.absolutePath}\""

            val statisticsCallback = StatisticsCallback { _ ->
                //No callback
            }

            val executorService = Executors.newSingleThreadExecutor()

            val session = FFmpegKit.executeAsync(
                cmd,
                { completedSession ->
                    executorService.shutdown()

                    if (ReturnCode.isSuccess(completedSession.returnCode)) {
                        continuation.resumeWith(Result.success(Unit))
                    } else {
                        val errorMessage = if (ReturnCode.isCancel(completedSession.returnCode)) {
                            "Command cancelled"
                        } else {
                            "Command failed with state '${completedSession.state}' " +
                                    "and return code ${completedSession.returnCode}, " +
                                    "stack trace ${completedSession.failStackTrace}"
                        }
                        continuation.resumeWithException(RuntimeException(errorMessage))
                    }
                },
                { log ->
                    Logger.v(VideoDownload.TAG, log.message)
                },
                statisticsCallback,
                executorService
            )

            continuation.invokeOnCancellation {
                session.cancel()
                executorService.shutdownNow()
            }
        }
    }

    override suspend fun download(target: File, onProgress: (Long, Long, Long) -> Unit): Long =
        downloadHlsSource(target, onProgress)

    private suspend fun downloadHlsSource(targetFile: File, onProgress: (Long, Long, Long) -> Unit): Long {
        if (targetFile.exists())
            targetFile.delete()

        var downloadedTotalLength = 0L

        fun downloadBytes(url: String, rangeStart: Long? = null, rangeLength: Long? = null): ByteArray {
            val headers = mutableMapOf<String, String>()

            if (rangeStart != null) {
                if (rangeLength != null && rangeLength > 0) {
                    val end = rangeStart + rangeLength - 1
                    headers["Range"] = "bytes=$rangeStart-$end"
                } else {
                    headers["Range"] = "bytes=$rangeStart-"
                }
            }

            val resp = ctx.client.get(url, headers, ctx.modifier)
            if (!resp.isOk) {
                resp.body?.close()
                throw IllegalStateException("Failed to download HLS resource ($url): HTTP ${resp.code}")
            }

            val body = resp.body ?: throw IllegalStateException("Failed to download HLS resource ($url): Empty body")
            val bytes = body.bytes()
            body.close()
            return bytes
        }

        fun buildSequenceIv(sequenceNumber: Long): ByteArray {
            return ByteBuffer.allocate(16)
                .putLong(0L)
                .putLong(sequenceNumber)
                .array()
        }

        val segmentFiles = arrayListOf<File>()
        try {
            val playlistResp = ctx.client.get(hlsUrl, mutableMapOf(), ctx.modifier)

            if (!playlistResp.isOk) {
                playlistResp.close();
                throw IllegalStateException("Failed to get variant playlist: ${playlistResp.code}");
            }

            val vpContent = playlistResp.body?.string()
                ?: throw IllegalStateException("Variant playlist content is empty")

            val variantPlaylist = HLS.parseVariantPlaylist(vpContent, hlsUrl)
            val hlsDec = variantPlaylist.decryptionInfo
            val useDecryption = hlsDec != null && !hlsDec.method.equals("NONE", ignoreCase = true)
            var keyBytes: ByteArray? = null
            var staticIvBytes: ByteArray? = null

            if (useDecryption) {
                if (!hlsDec.method.equals("AES-128", ignoreCase = true)) {
                    throw UnsupportedOperationException("HLS decryption method '${hlsDec.method}' is not supported.")
                }

                val keyUrl = hlsDec.keyUrl ?: throw IllegalStateException("Encrypted HLS playlist without key URI is not supported.")

                keyBytes = downloadBytes(keyUrl)
                if (!hlsDec.iv.isNullOrEmpty()) {
                    staticIvBytes = hlsDec.iv.hexStringToByteArray()
                }
            }

            val mediaSequence = variantPlaylist.mediaSequence ?: 0L
            val rangeOffsets = mutableMapOf<String, Long>()

            if (!variantPlaylist.mapUrl.isNullOrEmpty()) {
                if (ctx.isCancelled()) throw CancellationException("Cancelled")

                Logger.i(VideoDownload.TAG, "Downloading HLS initialization map")

                var mapRangeStart: Long? = null
                var mapRangeLength: Long? = null

                if (variantPlaylist.mapBytesLength > 0) {
                    mapRangeLength = variantPlaylist.mapBytesLength

                    val mapUrl = variantPlaylist.mapUrl
                    if (variantPlaylist.mapBytesStart >= 0) {
                        mapRangeStart = variantPlaylist.mapBytesStart
                        rangeOffsets[mapUrl] =
                            variantPlaylist.mapBytesStart + variantPlaylist.mapBytesLength
                    } else {
                        val offset = rangeOffsets[mapUrl] ?: 0L
                        mapRangeStart = offset
                        rangeOffsets[mapUrl] = offset + variantPlaylist.mapBytesLength
                    }
                }

                var mapBytes = downloadBytes(variantPlaylist.mapUrl!!, mapRangeStart, mapRangeLength)

                if (useDecryption) {
                    val kb = keyBytes ?: throw IllegalStateException("Decryption key bytes are missing.")
                    val iv = staticIvBytes
                        ?: throw UnsupportedOperationException("Encrypted EXT-X-MAP without explicit IV is not supported.")
                    mapBytes = decryptSegment(mapBytes, kb, iv)
                }

                if (mapBytes.size.toLong() > Int.MAX_VALUE) {
                    throw IllegalStateException("HLS MAP segment too large to handle.")
                }

                val segmentFile = File(ctx.context.cacheDir, "segment-${UUID.randomUUID()}")
                val outStr = segmentFile.outputStream()
                try {
                    segmentFiles.add(segmentFile)
                    outStr.write(mapBytes)
                    outStr.flush()
                } finally {
                    outStr.close()
                }
                downloadedTotalLength += mapBytes.size
            }

            val totalSegments = variantPlaylist.segments.size
            var mediaSegmentIndex = 0

            var bytesSinceLastSpeedUpdate = 0L
            var lastSpeedUpdateTime = System.currentTimeMillis()
            var lastSpeed = 0L

            variantPlaylist.segments.forEachIndexed { index, segment ->
                if (segment !is HLS.MediaSegment) return@forEachIndexed
                if (ctx.isCancelled()) throw CancellationException("Cancelled")

                Logger.i(VideoDownload.TAG, "Download '$name' segment $index sequential")

                var rangeStart: Long? = null
                var rangeLength: Long? = null

                if (segment.bytesLength > 0) {
                    rangeLength = segment.bytesLength

                    val urlKey = segment.uri
                    if (segment.bytesStart >= 0) {
                        rangeStart = segment.bytesStart
                        rangeOffsets[urlKey] = segment.bytesStart + segment.bytesLength
                    } else {
                        val offset = rangeOffsets[urlKey] ?: 0L
                        rangeStart = offset
                        rangeOffsets[urlKey] = offset + segment.bytesLength
                    }
                }

                var segmentBytes = downloadBytes(segment.uri, rangeStart, rangeLength)

                if (useDecryption) {
                    val kb = keyBytes ?: throw IllegalStateException("Decryption key bytes are missing.")
                    val ivBytes = if (staticIvBytes != null) {
                        staticIvBytes
                    } else {
                        val sequenceNumber = mediaSequence + mediaSegmentIndex
                        buildSequenceIv(sequenceNumber)
                    }

                    segmentBytes = decryptSegment(segmentBytes, kb, ivBytes)
                }

                val segmentLength = segmentBytes.size.toLong()
                if (segmentLength > Int.MAX_VALUE) {
                    throw IllegalStateException("HLS media segment too large to handle.")
                }

                val avgLen = if (index == 0) {
                    segmentLength
                } else {
                    if (index > 0) downloadedTotalLength / index else segmentLength
                }
                val expectedTotal = avgLen * (totalSegments - 1) + segmentLength

                val segmentFile = File(ctx.context.cacheDir, "segment-${UUID.randomUUID()}")
                val outStr = segmentFile.outputStream()
                try {
                    segmentFiles.add(segmentFile)
                    outStr.write(segmentBytes)
                } finally {
                    outStr.close()
                }
                downloadedTotalLength += segmentLength

                bytesSinceLastSpeedUpdate += segmentLength
                val now = System.currentTimeMillis()
                val elapsed = now - lastSpeedUpdateTime
                if (elapsed >= 500 && bytesSinceLastSpeedUpdate > 0) {
                    lastSpeed = (bytesSinceLastSpeedUpdate * 1000L / elapsed)
                    bytesSinceLastSpeedUpdate = 0
                    lastSpeedUpdateTime = now
                }

                onProgress(expectedTotal, downloadedTotalLength, lastSpeed)
                mediaSegmentIndex++
            }

            combineSegments(ctx.context, segmentFiles, targetFile)
            Logger.i(VideoDownload.TAG, "Finished HLS Source for $name")
        } catch (ioex: IOException) {
            if (targetFile.exists())
                targetFile.delete()
            if (ioex.message?.contains("ENOSPC") == true)
                throw Exception("Not enough space on device", ioex)
            else
                throw ioex
        } catch (ex: Throwable) {
            if (targetFile.exists())
                targetFile.delete()
            throw ex
        }
        finally {
            for (segmentFile in segmentFiles) {
                segmentFile.delete()
            }
        }

        return downloadedTotalLength
    }
}
