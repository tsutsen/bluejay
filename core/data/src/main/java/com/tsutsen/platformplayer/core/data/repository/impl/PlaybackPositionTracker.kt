package com.tsutsen.platformplayer.core.data.repository.impl

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Position ticker: single source of truth for currentPositionMs. It stays
 * on Dispatchers.Main (ExoPlayer must be accessed on the main thread) and
 * its Job is parented to the injected app-lifetime scope so the ticker
 * shares that lifetime without dispatching off Main.
 */
internal class PlaybackPositionTracker(
    appScope: CoroutineScope,
    private val playerProvider: () -> ExoPlayer?,
    private val onPosition: (Long) -> Unit,
) {
    private val scope =
        CoroutineScope(SupervisorJob(appScope.coroutineContext[Job]) + Dispatchers.Main)
    private var tickerJob: Job? = null

    fun start() {
        tickerJob?.cancel()
        tickerJob =
            scope.launch {
                while (true) {
                    val player = playerProvider()
                    if (player != null) {
                        val position = player.currentPosition
                        onPosition(position)
                    }
                    delay(250) // 4Hz; the ViewModel quantizes emissions to 500ms, so UI is unaffected
                }
            }
    }

    fun stop() {
        tickerJob?.cancel()
        tickerJob = null
    }
}
