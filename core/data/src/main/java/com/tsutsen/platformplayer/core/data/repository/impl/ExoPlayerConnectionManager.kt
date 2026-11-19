package com.tsutsen.platformplayer.core.data.repository.impl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tsutsen.platformplayer.core.data.service.PlayerService

/**
 * Owns the ExoPlayer instance and its PlayerService connection: creates the
 * player (once), registers the listeners, connects the media3
 * MediaController that keeps the service's MediaSession alive, and releases
 * everything on close. Extracted from [PlayerRepositoryImpl], which stays
 * the orchestrator.
 *
 * The player must only be touched from the main thread — callers dispatch.
 */
internal class ExoPlayerConnectionManager(
    private val context: Context,
    private val logTag: String,
    private val playerListener: Player.Listener,
    private val analyticsListener: AnalyticsListener,
    private val onPlayerCreated: () -> Unit,
) {
    /** The live player; null until [ensurePlayer] and after [releasePlayer]. */
    var player: ExoPlayer? = null
        private set

    // Holds the connection to PlayerService open while playback is active.
    // The bind is what makes the service create its MediaSession — a bare
    // start never does (onGetSession is only called on connect).
    private var mediaController: MediaController? = null

    /**
     * Creates the player on first use, registering [playerListener],
     * [analyticsListener] and invoking [onPlayerCreated]; later calls
     * return the existing instance.
     */
    fun ensurePlayer(): ExoPlayer {
        val existing = player
        if (existing != null) {
            Log.i(logTag, "Using existing ExoPlayer instance")
            return existing
        }
        Log.i(logTag, "Creating new ExoPlayer instance")
        val newPlayer =
            ExoPlayer
                .Builder(context)
                .setHandleAudioBecomingNoisy(true)
                .build()
        newPlayer.addListener(playerListener)
        newPlayer.addAnalyticsListener(analyticsListener)
        // media3 1.x disables legacy text decoding by default, so
        // the raw VTT subtitle sources we attach can't be decoded
        // without opting back in.
        for (i in 0 until newPlayer.rendererCount) {
            if (newPlayer.getRendererType(i) == C.TRACK_TYPE_TEXT) {
                (newPlayer.getRenderer(i) as? TextRenderer)
                    ?.experimentalSetLegacyDecodingEnabled(true)
            }
        }
        player = newPlayer
        onPlayerCreated()
        return newPlayer
    }

    /**
     * Registers playback with the system: media notification, lock-screen
     * controls, media buttons (media3 session service). Idempotent — the
     * controller is connected once and kept for the session.
     */
    fun startServiceAndConnectController() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, PlayerService::class.java),
        )
        if (mediaController != null) return
        val future =
            MediaController
                .Builder(
                    context,
                    SessionToken(
                        context,
                        ComponentName(context, PlayerService::class.java),
                    ),
                ).buildAsync()
        future.addListener(
            {
                try {
                    mediaController = future.get()
                } catch (t: Throwable) {
                    Log.w(logTag, "MediaController connection failed", t)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /** Releases the MediaController bind (kept separate from [releasePlayer] for close() ordering). */
    fun disconnectMediaController() {
        mediaController?.release()
        mediaController = null
    }

    /** Releases the player. Order with [disconnectMediaController] is the caller's concern. */
    fun releasePlayer() {
        player?.release()
        player = null
    }
}
