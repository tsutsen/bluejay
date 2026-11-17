package com.tsutsen.platformplayer.feature.player.impl.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.tsutsen.platformplayer.core.ui.R
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayerCompactOverlay(
    isPlaying: Boolean,
    isLoading: Boolean = false,
    subtitlesOn: Boolean,
    onMinimize: () -> Unit,
    onPlayPause: () -> Unit,
    onChapters: () -> Unit,
    onSubtitleToggle: () -> Unit,
    onWatchLater: () -> Unit,
    isWatchLater: Boolean = false,
    onOptions: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                // Top must be dark from the very first pixel (like the
                // video player's scrim) — the old 0.0 top stop left a
                // non-darkened sliver above the video.
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.7f),
                        Color.Black.copy(alpha = 0.8f),
                        Color.Black.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onMinimize) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.player_minimize_cd),
                    tint = Color.White
                )
            }
            IconButton(onClick = onPlayPause) {
                if (isLoading) {
                    LoadingIndicator(
                        modifier = Modifier.size(Tokens.IconMd),
                        color = Color.White,
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.player_pause_cd) else stringResource(R.string.video_play),
                        tint = Color.White
                    )
                }
            }
            IconButton(onClick = onChapters) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = stringResource(R.string.player_chapters),
                    tint = Color.White
                )
            }
            IconButton(onClick = onSubtitleToggle) {
                Icon(
                    imageVector = if (subtitlesOn) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaption,
                    contentDescription = stringResource(R.string.player_subtitles),
                    tint = Color.White
                )
            }
            IconButton(onClick = onWatchLater) {
                Icon(
                    imageVector =
                        if (isWatchLater) Icons.Filled.Schedule else Icons.Outlined.Schedule,
                    contentDescription = stringResource(R.string.video_watch_later),
                    tint = if (isWatchLater) Color.White else Color.White.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onOptions) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.player_options_cd),
                    tint = Color.White
                )
            }
            IconButton(onClick = onFullscreen) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = stringResource(R.string.player_fullscreen_cd),
                    tint = Color.White
                )
            }
        }
    }
}
