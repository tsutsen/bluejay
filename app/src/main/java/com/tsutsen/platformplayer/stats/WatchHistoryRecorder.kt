package com.tsutsen.platformplayer.stats

import com.tsutsen.platformplayer.api.media.PlatformID
import com.tsutsen.platformplayer.api.media.models.PlatformAuthorLink
import com.tsutsen.platformplayer.api.media.models.Thumbnail
import com.tsutsen.platformplayer.api.media.models.Thumbnails
import com.tsutsen.platformplayer.api.media.models.contents.ContentType as ApiContentType
import com.tsutsen.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.states.StateHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Records local watch history: the "when was this video watched" data the
 * stats and history features read.
 *
 * Nothing else writes to [StateHistory] on a fresh device — only backup
 * restore and remote sync do — so without this recorder the history store
 * (and the Dash stats) would stay empty for normal local viewing.
 *
 * Watches the shared player repository's state and writes the current
 * position + timestamp into the history store when a video starts, when
 * playback pauses/stops, and periodically while playing (at most once
 * every [MIN_WRITE_INTERVAL_MS]). The stored [HistoryVideo date]
 * therefore lags actual viewing by at most that interval.
 *
 * Private mode needs no handling here: [StateHistory.getHistoryByVideo]
 * refuses to create entries while it is on.
 */
object WatchHistoryRecorder {
    private const val MIN_WRITE_INTERVAL_MS = 30_000L

    private var started = false
    private var lastWriteMs = 0L

    fun start(playerRepository: PlayerRepository) {
        if (started) return
        started = true
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var lastUrl: String? = null
            var wasPlaying = false
            playerRepository.playerState.collect { state ->
                val item = state.currentVideo ?: return@collect
                val playing = state.isPlaying && !state.isLoading
                val videoStarted = item.url != lastUrl
                val justPaused = wasPlaying && !playing
                lastUrl = item.url
                wasPlaying = playing

                val due = playing &&
                    System.currentTimeMillis() - lastWriteMs >= MIN_WRITE_INTERVAL_MS
                if (videoStarted || justPaused || due) {
                    lastWriteMs = System.currentTimeMillis()
                    record(item, state.currentPositionMs)
                }
            }
        }
    }

    private fun record(item: ContentItem, positionMs: Long) {
        val video = item.toHistoryVideo()
        val index = StateHistory.instance.getHistoryByVideo(video, create = true) ?: return
        StateHistory.instance.updateHistoryPosition(video, index, true, positionMs)
    }

    private fun ContentItem.toHistoryVideo(): SerializedPlatformVideo =
        SerializedPlatformVideo(
            contentType =
                when (contentType) {
                    com.tsutsen.platformplayer.core.model.ContentType.VIDEO,
                    com.tsutsen.platformplayer.core.model.ContentType.SHORTS,
                    com.tsutsen.platformplayer.core.model.ContentType.LIVE,
                    -> ApiContentType.MEDIA

                    com.tsutsen.platformplayer.core.model.ContentType.PLAYLIST ->
                        ApiContentType.PLAYLIST

                    else -> ApiContentType.UNKNOWN
                },
            id = PlatformID.asUrlID(url),
            name = title,
            thumbnails = thumbnailUrl?.let { Thumbnails(arrayOf(Thumbnail(it))) } ?: Thumbnails(),
            author =
                PlatformAuthorLink(
                    id = PlatformID.NONE,
                    name = author?.name ?: "Unknown",
                    url = author?.url ?: "",
                ),
            url = url,
            duration = durationMs ?: 0L,
            viewCount = viewCount ?: 0L,
        )
}
