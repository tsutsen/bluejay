package com.tsutsen.platformplayer.di

import android.util.Log
import com.tsutsen.platformplayer.api.http.ManagedHttpClient
import com.tsutsen.platformplayer.core.data.repository.SeekThumbnailFrame
import com.tsutsen.platformplayer.core.data.repository.SeekThumbnailProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Plugin-independent storyboard scrub previews for YouTube.
 *
 * The signed YouTube plugin (v366) does not expose the seek-thumbnail
 * provider that newer versions attach to their details object, so this
 * fallback fetches the player response from the public innertube ANDROID
 * endpoint and parses `storyboards.playerStoryboardSpecRenderer.spec`
 * directly — the same sprite-sheet data the plugin path would surface.
 * Sheets live on i.ytimg.com, signed with the spec's sigh parameter.
 */
object YouTubeStoryboardProvider {

    private const val TAG = "YouTubeStoryboard"

    private const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"

    private val VIDEO_ID_REGEX = Regex("""(?:[?&]v=|youtu\.be/)([A-Za-z0-9_-]{11})""")

    fun videoIdOf(contentUrl: String): String? =
        VIDEO_ID_REGEX.find(contentUrl)?.groupValues?.get(1)

    /** Fetches the storyboard spec and wraps it; null when unavailable. */
    suspend fun fetch(contentUrl: String): SeekThumbnailProvider? =
        withContext(Dispatchers.IO) {
            runCatching {
                val videoId = videoIdOf(contentUrl) ?: return@runCatching null
                val body = JSONObject().apply {
                    put(
                        "context",
                        JSONObject().apply {
                            put(
                                "client",
                                JSONObject().apply {
                                    put("clientName", "ANDROID")
                                    put("clientVersion", "19.09.37")
                                    put("androidSdkVersion", 30)
                                    put("hl", "en")
                                },
                            )
                        },
                    )
                    put("videoId", videoId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }
                val response =
                    ManagedHttpClient.shared.post(
                        PLAYER_URL,
                        body.toString(),
                        hashMapOf("Content-Type" to "application/json"),
                    )
                response.use {
                    if (!it.isOk) {
                        Log.w(TAG, "Storyboard player request failed: ${it.code}")
                        return@runCatching null
                    }
                    val json = JSONObject(it.body!!.string())
                    val spec =
                        json.getJSONObject("storyboards")
                            .getJSONObject("playerStoryboardSpecRenderer")
                            .getString("spec")
                    buildProvider(videoId, spec) ?: run {
                        Log.i(TAG, "No usable storyboard level for $videoId")
                        null
                    }
                }
            }.onFailure { Log.w(TAG, "Storyboard fetch failed", it) }.getOrNull()
        }

    /** Mirrors the plugin's createSeekThumbnailProvider level selection. */
    private fun buildProvider(videoId: String, spec: String): SeekThumbnailProvider? {
        val parts = spec.split("|")
        val urlTemplate = parts.firstOrNull() ?: return null
        if (!urlTemplate.contains("\$L") || !urlTemplate.contains("\$N")) return null

        for (level in minOf(2, parts.size - 2) downTo 0) {
            val fields = parts[level + 1].split("#")
            if (fields.size < 8) continue
            val width = fields[0].toIntOrNull() ?: continue
            val height = fields[1].toIntOrNull() ?: continue
            val frameCount = fields[2].toIntOrNull() ?: continue
            val columns = fields[3].toIntOrNull() ?: continue
            val rows = fields[4].toIntOrNull() ?: continue
            val intervalMs = fields[5].toLongOrNull() ?: continue
            val nameTemplate = fields[6]
            val signature = fields[7]
            if (width <= 0 || height <= 0 || frameCount <= 0 || columns <= 0 ||
                rows <= 0 || intervalMs <= 0 || nameTemplate.isEmpty() || signature.isEmpty()
            ) {
                continue
            }

            val framesPerSheet = columns * rows
            val sheetUrlBase = urlTemplate.replace("\$L", level.toString())

            return object : SeekThumbnailProvider {
                override val intervalMs: Long = intervalMs

                override suspend fun thumbnailFor(timeMs: Long): SeekThumbnailFrame? {
                    val time = timeMs.coerceAtLeast(0)
                    val frameIndex = minOf(frameCount - 1, (time / intervalMs).toInt())
                    val sheetIndex = frameIndex / framesPerSheet
                    val sheetName = nameTemplate.replace("\$M", sheetIndex.toString())
                    val url =
                        sheetUrlBase.replace("\$N", sheetName) +
                            (if (sheetUrlBase.contains("?")) "&" else "?") +
                            "sigh=" + java.net.URLEncoder.encode(signature, "UTF-8")
                    // Position within the sheet, clamped to the sheet's actual
                    // frame count on the last (partial) sheet.
                    val position = frameIndex - sheetIndex * framesPerSheet
                    return SeekThumbnailFrame(
                        sheetUrl = url,
                        x = (position % columns) * width,
                        y = (position / columns) * height,
                        width = width,
                        height = height,
                        timeMs = time,
                    )
                }
            }
        }
        return null
    }
}
