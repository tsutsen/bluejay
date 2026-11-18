package com.tsutsen.platformplayer.core.data.repository.impl

import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import com.tsutsen.platformplayer.core.data.playback.RecoverableSourceError
import com.tsutsen.platformplayer.core.data.repository.ResolutionResult
import com.tsutsen.platformplayer.core.model.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transparent playback recovery for source errors that a fresh resolve
 * can fix. SABR playback dies mid-stream with a po-token rejection
 * (YouTube returns 403 / ATTESTATION_REQUIRED on requests after the
 * first — typical trigger: seek or rebuffer). A re-resolve of the same
 * video mints a fresh attestation, so instead of surfacing a dead
 * player we silently rebuild the source and continue at the position
 * the error interrupted.
 *
 * Budget per play() so a persistently rejected stream can't loop
 * recoveries forever, plus a minimum spacing between attempts. Extracted
 * from [PlayerRepositoryImpl]; called from onPlayerError (main thread).
 */
internal class SourceRecoveryManager(
    private val logTag: String,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<PlayerState>,
    private val playerProvider: () -> ExoPlayer?,
    private val currentPlayUrl: () -> String?,
    private val resolve: suspend (url: String, positionMs: Long) -> ResolutionResult,
    private val invalidateSourceCache: suspend (url: String) -> Unit,
    private val onSourceRebuilt: (MediaSource) -> Unit,
) {
    private var sourceRecoveryBudget = 0
    private var lastSourceRecoveryAtMs = 0L

    /** A fresh play arms recovery: reset the budget for the new video. */
    fun arm() {
        sourceRecoveryBudget = SOURCE_RECOVERY_MAX_ATTEMPTS
    }

    /**
     * Returns true when recovery was started — the caller then skips
     * publishing the error to the UI.
     */
    fun attempt(error: PlaybackException): Boolean {
        // Recover errors our source layer explicitly marked (SABR po-token
        // rejections) AND IO-class failures: progressive stream URLs are
        // time-limited (YouTube: ~6h) and resets/403s surface here once
        // ExoPlayer's internal retries exhaust — the classic trigger is
        // resuming playback after the device was locked long enough for
        // the URL to expire. A fresh resolve mints a new URL.
        var cause: Throwable? = error
        var recoverable: RecoverableSourceError? = null
        while (cause != null) {
            if (cause is RecoverableSourceError) {
                recoverable = cause
                break
            }
            cause = cause.cause
        }
        if (recoverable == null && !isRecoverableIoError(error)) return false

        val url = currentPlayUrl()
        if (url.isNullOrEmpty()) return false
        if (sourceRecoveryBudget <= 0) {
            Log.w(logTag, "Source recovery budget exhausted; surfacing error")
            return false
        }
        val now = System.currentTimeMillis()
        if (now - lastSourceRecoveryAtMs < SOURCE_RECOVERY_MIN_INTERVAL_MS) {
            Log.w(logTag, "Source recovery attempted too recently; surfacing error")
            return false
        }
        sourceRecoveryBudget--
        lastSourceRecoveryAtMs = now

        val positionMs = playerProvider()?.currentPosition ?: 0L
        Log.w(logTag, "Recoverable source error (" +
            (recoverable?.javaClass?.simpleName ?: error.errorCodeName) + "); " +
            "re-resolving $url at ${positionMs}ms (budget left: $sourceRecoveryBudget)")

        // Keep the player usable while re-resolving: clear the failed state
        // and show loading, so the UI doesn't flash the error slab.
        state.update { it.copy(isLoading = true, error = null) }

        scope.launch {
            try {
                // Drop cached attestation tokens first — a plain re-resolve
                // can return the same rejected token from the plugin's cache.
                withContext(Dispatchers.IO) { invalidateSourceCache(url) }
                val resolution = withContext(Dispatchers.IO) { resolve(url, positionMs) }
                val mediaSource = resolution.mediaSource
                if (mediaSource == null) {
                    Log.e(logTag, "Source recovery resolve produced no MediaSource")
                    state.update { it.copy(isLoading = false, error = "Failed to resolve video source") }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    val player = playerProvider()
                    if (player == null) {
                        Log.w(logTag, "Source recovery aborted: player gone")
                        return@withContext
                    }
                    onSourceRebuilt(mediaSource)
                    player.setMediaSource(mediaSource)
                    player.seekTo(positionMs)
                    player.prepare()
                    player.playWhenReady = true
                    Log.i(logTag, "Source recovery applied; playback rebuilt at ${positionMs}ms")
                }
            } catch (e: Exception) {
                Log.e(logTag, "Source recovery failed", e)
                state.update { it.copy(isLoading = false, error = e.message ?: "Playback failed") }
            }
        }
        return true
    }

    /**
     * IO-class playback errors worth a fresh resolve. ExoPlayer retries
     * these internally first ([onPlayerError] fires only after its retry
     * policy gives up), so reaching here means the URL is dead — expired
     * (HTTP 403 after the ~6h `expire` window), repeatedly reset by the
     * server, or unreachable after a long device sleep. DRM/decoding/
     * content-not-found errors are deliberately excluded: re-resolving
     * can't fix them.
     */
    private fun isRecoverableIoError(error: PlaybackException): Boolean =
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
            -> true
            else -> false
        }

    private companion object {
        // Transparent source-recovery budget per play() call, and the
        // minimum spacing between recovery attempts (guards against a
        // persistently rejected source looping recoveries).
        const val SOURCE_RECOVERY_MAX_ATTEMPTS = 3
        const val SOURCE_RECOVERY_MIN_INTERVAL_MS = 5_000L
    }
}
