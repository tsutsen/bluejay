package com.tsutsen.platformplayer.activities

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.designsystem.component.formatDuration
import com.tsutsen.platformplayer.core.designsystem.theme.GrayjayTheme
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.ui.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Companion activity rendered on the device's second (rear) display.
 *
 * Shows controls for the currently playing video (same process, same
 * [PlayerRepository] as MainActivity, so the state is live).
 *
 * Single-screen devices: [start] refuses to launch and [onCreate] finishes
 * the activity if the requested display does not exist — a missing second
 * screen never crashes the app.
 */
@AndroidEntryPoint
class CompanionActivity : ComponentActivity() {

    @Inject
    lateinit var playerRepository: PlayerRepository

    companion object {
        private const val EXTRA_DISPLAY_ID = "displayId"
        private var instance: WeakReference<CompanionActivity>? = null

        /** The first non-default display, or null on single-screen devices. */
        fun secondaryDisplay(context: Context): Display? {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            return dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        }

        /**
         * Start the companion window on the second display when [enabled],
         * finish it when disabled. No-op when the device has no second screen.
         */
        fun start(context: Context, enabled: Boolean) {
            if (!enabled) {
                instance?.get()?.finish()
                return
            }
            val display = secondaryDisplay(context) ?: return
            val options = ActivityOptions.makeBasic().apply { launchDisplayId = display.displayId }
            context.startActivity(
                Intent(context, CompanionActivity::class.java)
                    .putExtra(EXTRA_DISPLAY_ID, display.displayId),
                options.toBundle(),
            )
        }

        fun finish() {
            instance?.get()?.finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val displayId = intent?.getIntExtra(EXTRA_DISPLAY_ID, -1) ?: -1
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(displayId)
        if (displayId == Display.DEFAULT_DISPLAY || display == null) {
            // No second screen (or stale display id after a hot-unplug).
            finish()
            return
        }
        instance = WeakReference(this)

        setContent {
            GrayjayTheme {
                CompanionContent(playerRepository)
            }
        }
    }

    override fun onDestroy() {
        if (instance?.get() == this) instance = null
        super.onDestroy()
    }
}

@Composable
private fun CompanionContent(playerRepository: PlayerRepository) {
    val state by playerRepository.playerState.collectAsState()
    val video = state.currentVideo
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (video == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nothing playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Surface
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Tokens.SpaceLg),
        ) {
            AsyncImage(
                url = video.thumbnailUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(Tokens.RadiusMd)),
            )

            Spacer(modifier = Modifier.size(Tokens.SpaceMd))

            Text(
                text = video.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            video.author?.let {
                Text(
                    text = it.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.size(Tokens.SpaceMd))

            Text(
                text =
                    "${formatDuration(state.currentPositionMs)} / " +
                        "${formatDuration(state.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.weight(1f))

            PlayerControlRow(
                isPlaying = state.isPlaying,
                onPlayPause = {
                    scope.launch {
                        if (state.isPlaying) playerRepository.pause() else playerRepository.resume()
                    }
                },
                onSeekBy = { deltaMs ->
                    val target = (state.currentPositionMs + deltaMs)
                        .coerceIn(0L, if (state.durationMs > 0) state.durationMs else Long.MAX_VALUE)
                    scope.launch { playerRepository.seekTo(target) }
                },
                onPrevious = {
                    val index = state.selectedIndex
                    if (state.queue.isNotEmpty() && index > 0) {
                        scope.launch { playerRepository.play(state.queue[index - 1].id) }
                    }
                },
                onNext = {
                    val index = state.selectedIndex
                    if (state.queue.isNotEmpty() && index + 1 < state.queue.size) {
                        scope.launch { playerRepository.play(state.queue[index + 1].id) }
                    }
                },
            )

            Spacer(modifier = Modifier.size(Tokens.SpaceMd))
        }
    }
}

@Composable
private fun PlayerControlRow(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
        }
        IconButton(onClick = { onSeekBy(-10_000L) }) {
            Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds")
        }
        IconButton(onClick = onPlayPause) {
            Icon(
                imageVector =
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = { onSeekBy(10_000L) }) {
            Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds")
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next")
        }
    }
}
