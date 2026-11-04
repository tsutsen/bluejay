package com.tsutsen.platformplayer.feature.player.impl.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material3.*
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.VideoChapter
import com.tsutsen.platformplayer.feature.player.impl.formatTime
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayerNormalBottomOverlay(
    positionMs: StateFlow<Long>,
    durationMs: Long,
    isPlaying: Boolean,
    isLoading: Boolean = false,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChapters: () -> Unit,
    onFullscreen: () -> Unit,
    subtitlesOn: Boolean,
    onSubtitleToggle: () -> Unit,
    onSeek: (Long) -> Unit = {},
    onScrubFinished: () -> Unit = {},
    isScrubbing: Boolean = false,
    scrubPositionMs: Long = 0L,
    // Hold-seek scrub target while the player is paused mid-scrub; null = no
    // preview. While set, it drives the playhead (the live position is
    // frozen until the scrub commits).
    seekPreviewMs: Long? = null,
    // Storyboard scrub previews, when the source plugin provides them.
    seekThumbnails: kotlinx.coroutines.flow.StateFlow<com.tsutsen.platformplayer.core.data.repository.SeekThumbnailProvider?>? = null,
    chapters: List<VideoChapter> = emptyList(),
) {
    // Collected here — at the leaf — so the 10 Hz position ticks recompose
    // only this overlay, not the whole player screen.
    val currentPositionMs by positionMs.collectAsState(initial = positionMs.value)

    // After the scrub releases, keep showing the scrub target: the live
    // position is still the pre-seek value until ExoPlayer applies the seek
    // (the view model's seekTo is double-async — viewModelScope.launch ->
    // withContext(Main)), and switching over immediately flashes the
    // playhead back to the old spot for a frame. The pending target clears
    // once a tick lands on it — tolerance is one 10 Hz tick plus playback
    // advance (~150ms); anything wider shows as a visible jump. A timeout
    // guards against a failed seek pinning the playhead.
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    // Guards the capture below: the overlay re-composes on every show, and a
    // bare "isScrubbing == false" test would then re-capture the STALE
    // scrubPositionMs from the previous video session's scrub — the playhead
    // would lag at the old spot until the timeout. Only a real true->false
    // transition (a scrub that just released while composed) may capture.
    var wasScrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(isScrubbing) {
        if (isScrubbing) {
            pendingSeekMs = null
            wasScrubbing = true
        } else if (wasScrubbing) {
            wasScrubbing = false
            if (scrubPositionMs > 0L) pendingSeekMs = scrubPositionMs
        }
    }
    LaunchedEffect(pendingSeekMs) {
        val target = pendingSeekMs ?: return@LaunchedEffect
        withTimeoutOrNull(1_000) {
            snapshotFlow { currentPositionMs }
                .first { kotlin.math.abs(it - target) <= 150 }
        }
        pendingSeekMs = null
    }

    val effectivePositionMs =
        pendingSeekMs ?: seekPreviewMs ?: (if (isScrubbing) scrubPositionMs else currentPositionMs)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        // Controls row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(56.dp),
            ) {
                // While loading, the button itself spins instead of showing
                // play/pause (same pattern as the companion screen controls).
                if (isLoading) {
                    LoadingIndicator(
                        modifier = Modifier.size(Tokens.IconMd),
                        color = Color.White,
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                )
            }
            // Current chapter title fills the space left of the time text.
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                val currentChapter = chapters.lastOrNull { it.startTimeMs <= effectivePositionMs }
                if (currentChapter != null) {
                    Text(
                        text = currentChapter.title,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
            Box(
                modifier = Modifier.height(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${formatTime(effectivePositionMs)} / ${formatTime(durationMs)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onSubtitleToggle) {
                Icon(
                    imageVector = if (subtitlesOn) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaption,
                    contentDescription = "Subtitles",
                    tint = Color.White,
                )
            }
            IconButton(onClick = onChapters) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = "Chapters",
                    tint = Color.White,
                )
            }
            IconButton(onClick = onFullscreen) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = Color.White,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Timeline
        var isDragging by remember { mutableStateOf(false) }
        var seekPosition by remember { mutableFloatStateOf(0f) }

        // Playhead position for the track: a local slider drag wins, then
        // the effective (preview-aware) position drives the rest.
        val playheadFraction =
            if (isDragging) seekPosition
            else if (durationMs > 0) effectivePositionMs.toFloat() / durationMs
            else 0f

        // Storyboard frame preview above the thumb while dragging. The frame
        // is a cell of a sprite sheet: the sheet is fetched once per URL
        // (Coil memory cache), the cell is cropped at draw time.
        val emptyThumbnails =
            remember { kotlinx.coroutines.flow.MutableStateFlow<com.tsutsen.platformplayer.core.data.repository.SeekThumbnailProvider?>(null) }
        val thumbnailProvider by (seekThumbnails ?: emptyThumbnails).collectAsState()
        val scrubTimeMs = (playheadFraction * durationMs).toLong()
        var previewFrame by remember {
            mutableStateOf<com.tsutsen.platformplayer.core.data.repository.SeekThumbnailFrame?>(null)
        }
        val previewQuantum = thumbnailProvider?.intervalMs?.coerceAtLeast(1L) ?: 1L
        LaunchedEffect(isDragging, thumbnailProvider, durationMs, scrubTimeMs / previewQuantum) {
            val provider = thumbnailProvider
            if (!isDragging || provider == null || durationMs <= 0) {
                previewFrame = null
                return@LaunchedEffect
            }
            previewFrame =
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { provider.thumbnailFor(scrubTimeMs) }.getOrNull()
                }
        }
        val sheetBitmap = rememberSheetBitmap(previewFrame?.sheetUrl)

        BoxWithConstraints {
            Slider(
                value = playheadFraction,
                onValueChange = {
                    isDragging = true
                    seekPosition = it
                    val seekToMs = (it * durationMs).toLong()
                    onSeek(seekToMs)
                },
                onValueChangeFinished = {
                    val seekToMs = (seekPosition * durationMs).toLong()
                    onSeek(seekToMs)
                    onScrubFinished()
                    isDragging = false
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                enabled = durationMs > 0,
                colors = SliderDefaults.colors(),
                interactionSource = remember { MutableInteractionSource() },
            )
            // Storyboard preview bubble, floating above the thumb while the
            // timeline is being dragged.
            val frame = previewFrame
            if (isDragging && sheetBitmap != null && frame != null) {
                val previewWidth = 160.dp
                val previewHeight =
                    (previewWidth * frame.height / frame.width.coerceAtLeast(1))
                val bubbleOffsetX =
                    (maxWidth * playheadFraction - previewWidth / 2)
                        .coerceIn(0.dp, maxWidth - previewWidth)
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .offset(x = bubbleOffsetX, y = (-previewHeight - 14.dp))
                            .background(
                                Color.Black.copy(alpha = 0.85f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(3.dp),
                ) {
                    Canvas(modifier = Modifier.size(previewWidth, previewHeight)) {
                        drawImage(
                            image = sheetBitmap,
                            srcOffset = IntOffset(frame.x, frame.y),
                            srcSize = IntSize(frame.width, frame.height),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        )
                    }
                }
            }
            // Chapter dots over the timeline (drawn after the slider so they
            // sit on the track), skipping the leading edge. Played part ->
            // secondary, unplayed part -> primary.
            val progress = playheadFraction
            val playedDotColor = MaterialTheme.colorScheme.secondaryContainer
            val unplayedDotColor = MaterialTheme.colorScheme.primary
            if (chapters.isNotEmpty() && durationMs > 0) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .drawBehind {
                                val cy = size.height / 2f
                                val radius = 2.dp.toPx()
                                chapters.forEach { chapter ->
                                    val fraction =
                                        (chapter.startTimeMs.toFloat() / durationMs)
                                            .coerceIn(0f, 1f)
                                    if (fraction <= 0.001f) return@forEach
                                    drawCircle(
                                        color = if (fraction <= progress) playedDotColor else unplayedDotColor,
                                        radius = radius,
                                        center = Offset(fraction * size.width, cy),
                                    )
                                }
                            },
                )
            }
        }
    }
}

/**
 * Decodes a storyboard sprite sheet once per URL through the app's shared
 * Coil loader (memory + disk cached); the caller crops the wanted cell at
 * draw time with a src-rect [androidx.compose.ui.graphics.drawscope.DrawScope.drawImage].
 */
@Composable
private fun rememberSheetBitmap(url: String?): ImageBitmap? {
    if (url == null) return null
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, url) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(url)
                            .allowHardware(false)
                            .build()
                    val drawable = context.imageLoader.execute(request).drawable
                    (drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
                }.getOrNull()
            }
    }.value
}
