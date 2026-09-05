package com.tsutsen.platformplayer.activities

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.Window
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.designsystem.component.LinkifiedText
import com.tsutsen.platformplayer.core.designsystem.component.GroupCornerShapes
import com.tsutsen.platformplayer.core.designsystem.component.GroupPosition
import com.tsutsen.platformplayer.core.designsystem.component.VideoCardPills
import com.tsutsen.platformplayer.core.designsystem.component.VideoOptionsSheet
import com.tsutsen.platformplayer.core.designsystem.component.connectedGroupShapes
import com.tsutsen.platformplayer.core.designsystem.component.formatDuration
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.Author
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.ContentType
import com.tsutsen.platformplayer.core.model.DownloadButtonState
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.toContentItem
import com.tsutsen.platformplayer.core.ui.AsyncImage
import com.tsutsen.platformplayer.feature.library.impl.PlaylistCardView
import com.tsutsen.platformplayer.feature.player.impl.ChannelRow
import com.tsutsen.platformplayer.feature.player.impl.PlayerEvent
import com.tsutsen.platformplayer.feature.player.impl.PlayerEventBus
import com.tsutsen.platformplayer.feature.player.impl.SystemControls
import com.tsutsen.platformplayer.feature.player.impl.formatRelativeTime
import com.tsutsen.platformplayer.feature.player.impl.formatTime
import com.tsutsen.platformplayer.feature.player.impl.formatViewCount
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.tsutsen.platformplayer.core.model.VideoCard as CoreVideoCard
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun CompanionVideoOptionsSheet(
    card: CoreVideoCard,
    onDismiss: () -> Unit,
    onPlayItem: (ContentItem) -> Unit,
    libraryRepository: LibraryRepository,
    downloadsRepository: com.tsutsen.platformplayer.core.data.repository.DownloadsRepository,
    playbackQueueRepository: com.tsutsen.platformplayer.core.data.repository.PlaybackQueueRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    queue: List<ContentItem> = emptyList(),
    currentVideoUrl: String? = null,
) {
    val savedTypes by libraryRepository.observeSavedTypes(card.url).collectAsState(initial = emptySet())
    val playlists by libraryRepository.playlists.collectAsState(initial = emptyList())
    val containedPlaylists by libraryRepository
        .observePlaylistsContaining(card.url)
        .collectAsState(initial = emptySet())
    // Live download state straight from the shared repository (the same
    // derivation as the main screen's VideoOptionsViewModel), so the
    // second screen knows about downloads started anywhere.
    val downloads by downloadsRepository.downloads.collectAsState(initial = emptyList())
    val downloadEntry = downloads.firstOrNull { it.url == card.url }
    val downloadState =
        when {
            downloadEntry == null -> DownloadButtonState.Idle
            downloadEntry.done -> DownloadButtonState.Downloaded
            else -> DownloadButtonState.Downloading(downloadEntry.progress)
        }

    VideoOptionsSheet(
        url = card.url,
        onDismiss = onDismiss,
        onPlay = {
            onPlayItem(card.toContentItem())
            onDismiss()
        },
        // No navigation on the second screen.
        onGoToChannel = { onDismiss() },
        onToggleWatchLater = {
            scope.launch { toggleSaveType(libraryRepository, savedTypes, card, SavedVideoType.WATCH_LATER) }
        },
        onToggleLiked = {
            scope.launch { toggleSaveType(libraryRepository, savedTypes, card, SavedVideoType.LIKED) }
        },
        onToggleFavourite = {
            scope.launch { toggleSaveType(libraryRepository, savedTypes, card, SavedVideoType.FAVOURITE) }
        },
        onDownload = {
            scope.launch {
                when (downloadState) {
                    is DownloadButtonState.Downloaded ->
                        downloadsRepository.deleteDownload(card.url)

                    is DownloadButtonState.Downloading,
                    is DownloadButtonState.Starting ->
                        downloadsRepository.cancelDownload(card.url)

                    is DownloadButtonState.Idle ->
                        downloadsRepository.startDownload(card.url)
                }
            }
        },
        onDownloadWithQuality = { quality ->
            scope.launch { downloadsRepository.startDownload(card.url, quality) }
        },
        onAddToPlaylist = { playlistId ->
            // "New playlist" row only (checkboxes use onTogglePlaylist).
            if (playlistId == null) {
                scope.launch {
                    val id = libraryRepository.createPlaylist("New playlist")
                    libraryRepository.addVideoToPlaylist(id, card)
                }
            }
        },
        onTogglePlaylist = { playlistId, checked ->
            scope.launch {
                if (checked) {
                    libraryRepository.addVideoToPlaylist(playlistId, card)
                } else {
                    libraryRepository.removeVideoFromPlaylist(playlistId, card.url)
                }
            }
        },
        onAddToQueue = {
            // No onDismiss: the sheet stays open so the user can keep
            // choosing actions.
            playbackQueueRepository.add(card.toContentItem())
        },
        isInQueue = queue.any { it.url == card.url },
        isCurrentlyPlaying = currentVideoUrl != null && currentVideoUrl == card.url,
        onRemoveFromQueue = {
            scope.launch { playbackQueueRepository.remove(card.url) }
        },
        containedPlaylistIds = containedPlaylists,
        downloadState = downloadState,
        isWatchLaterSaved = savedTypes.contains(SavedVideoType.WATCH_LATER),
        isLikedSaved = savedTypes.contains(SavedVideoType.LIKED),
        isFavouriteSaved = savedTypes.contains(SavedVideoType.FAVOURITE),
        playlists = playlists,
        authorUrl = card.authorUrl?.takeIf { it.isNotEmpty() },
        title = card.title,
        durationMs = card.durationMs,
        viewCount = card.viewCount,
        publishedAt = card.publishedAt,
        // The host wraps this in a material3 BottomSheetScaffold sheet.
        embedded = true,
    )
}

internal fun CoreVideoCard.toContentItem() =
    com.tsutsen.platformplayer.core.model.ContentItem(
        id = id,
        url = url,
        title = title,
        author =
            author?.let { name ->
                com.tsutsen.platformplayer.core.model.Author(
                    id = id,
                    name = name,
                    url = authorUrl,
                    thumbnailUrl = null,
                )
            },
        thumbnailUrl = thumbnailUrl,
        contentType = com.tsutsen.platformplayer.core.model.ContentType.VIDEO,
        durationMs = durationMs,
        viewCount = viewCount,
        publishedAt = publishedAt,
    )

internal suspend fun toggleSaveType(
    libraryRepository: LibraryRepository,
    savedTypes: Set<SavedVideoType>,
    card: CoreVideoCard,
    type: SavedVideoType,
) {
    if (savedTypes.contains(type)) {
        libraryRepository.removeSavedVideo(type, card.url)
    } else {
        // Like and dislike are mutually exclusive (the main screen's player
        // row behaves the same way).
        when (type) {
            SavedVideoType.LIKED ->
                libraryRepository.removeSavedVideo(SavedVideoType.DISLIKED, card.url)

            SavedVideoType.DISLIKED ->
                libraryRepository.removeSavedVideo(SavedVideoType.LIKED, card.url)

            else -> Unit
        }
        libraryRepository.saveVideo(type, card)
    }
}

/** One card centred inside a pager page (or grid cell). */
@Composable
internal fun PagerCard(
    card: com.tsutsen.platformplayer.core.model.Card,
    onClick: (String) -> Unit,
    onLongClick: (CoreVideoCard) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Thumbnails always keep their fixed 16:9 ratio. In short slots the
        // card width is constrained so the natural card height (16:9 thumb +
        // 2-line title) fits; the card is centered in the slot.
        // ponytail: 58.dp title reserve is a fixed estimate (2 lines
        // bodyMedium + padding) — measure the title if fonts ever change.
        val cardWidth =
            ((maxHeight - 58.dp) * (16f / 9f)).coerceAtLeast(120.dp).coerceAtMost(maxWidth)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when (card) {
                is CoreVideoCard -> {
                    // Pill variant: all meta on the thumbnail, no meta row,
                    // so the card stays short and nothing clips in tight slots.
                    VideoCardPills(
                        card = card,
                        onClick = { onClick(card.url) },
                        onLongClick = { onLongClick(card) },
                        modifier = Modifier.width(cardWidth),
                    )
                }

                is PlaylistCard -> {
                    // No navigation on the second screen.
                    PlaylistCardView(card = card, onClick = {})
                }

                else -> {
                    // Channels and other card types: no navigation on the second
                    // screen.
                    Box(
                        modifier =
                            Modifier
                                .width(cardWidth)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(BluejayTokens().radius.sm))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

/**
 * Registers one icon-only playback control in the companion control group.
 * [containerColor]/[contentColor] come in as plain values so the caller
 * can animate them (the play/pause button turns accent-colored while
 * paused). Non-composable on purpose: the ButtonGroup scope itself is not
 * a composable context, only the item's content lambda is.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal fun ButtonGroupScope.controlItem(
    icon: ImageVector,
    description: String,
    containerColor: Color,
    contentColor: Color,
    shapes: GroupCornerShapes,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    customItem(
        buttonGroupContent = {
            Button(
                onClick = onClick,
                shapes = ButtonShapes(shapes.shape, shapes.pressedShape),
                modifier =
                    Modifier
                        .height(Tokens.ControlLg)
                        .weight(1f)
                        .animateWidth(interactionSource),
                contentPadding = PaddingValues(all = Tokens.SpaceMd),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                    ),
                interactionSource = interactionSource,
            ) {
                if (content != null) {
                    content()
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = description,
                        modifier = Modifier.size(Tokens.IconMd),
                    )
                }
            }
        },
        menuContent = {},
    )
}

/**
 * Playback controls as a native M3 [ButtonGroup] in the app's
 * connected-group language (the same corner recipe as the like/dislike
 * pill): on press the button expands, its neighbours compress, and its
 * shape and color morph.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
internal fun CompanionControlRow(
    isPlaying: Boolean,
    isLoading: Boolean = false,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val radius = BluejayTokens().radius
    val scheme = MaterialTheme.colorScheme
    val previousSource = remember { MutableInteractionSource() }
    val rewindSource = remember { MutableInteractionSource() }
    val playPauseSource = remember { MutableInteractionSource() }
    val forwardSource = remember { MutableInteractionSource() }
    val nextSource = remember { MutableInteractionSource() }
    // While stopped, the play button takes the accent color so the one
    // action that matters on the second screen stands out.
    val playContainer by
        animateColorAsState(
            targetValue = if (isPlaying) scheme.surfaceContainer else scheme.primaryContainer,
            animationSpec = tween(200, easing = FastOutSlowInEasing),
            label = "play-pause-container",
        )
    val playContent by
        animateColorAsState(
            targetValue = if (isPlaying) scheme.onSurfaceVariant else scheme.onPrimaryContainer,
            animationSpec = tween(200, easing = FastOutSlowInEasing),
            label = "play-pause-content",
        )
    ButtonGroup(
        overflowIndicator = { _ -> },
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
    ) {
        val restContainer = scheme.surfaceContainer
        val restContent = scheme.onSurfaceVariant
        controlItem(
            icon = Icons.Filled.SkipPrevious,
            description = "Previous",
            containerColor = restContainer,
            contentColor = restContent,
            shapes = connectedGroupShapes(GroupPosition.First, radius),
            interactionSource = previousSource,
            onClick = onPrevious,
        )
        // Seek icons without the baked-in "10" digit — plain rewind/ffwd.
        controlItem(
            icon = Icons.Filled.FastRewind,
            description = "Back 10 seconds",
            containerColor = restContainer,
            contentColor = restContent,
            shapes = connectedGroupShapes(GroupPosition.Middle, radius),
            interactionSource = rewindSource,
            onClick = { onSeekBy(-10_000L) },
        )
        controlItem(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            description = if (isPlaying) "Pause" else "Play",
            containerColor = playContainer,
            contentColor = playContent,
            shapes = connectedGroupShapes(GroupPosition.Middle, radius),
            interactionSource = playPauseSource,
            onClick = onPlayPause,
            content = if (isLoading) {
                {
                    LoadingIndicator(
                        modifier = Modifier.size(Tokens.IconMd),
                        color = playContent,
                    )
                }
            } else null,
        )
        controlItem(
            icon = Icons.Filled.FastForward,
            description = "Forward 10 seconds",
            containerColor = restContainer,
            contentColor = restContent,
            shapes = connectedGroupShapes(GroupPosition.Middle, radius),
            interactionSource = forwardSource,
            onClick = { onSeekBy(10_000L) },
        )
        controlItem(
            icon = Icons.Filled.SkipNext,
            description = "Next",
            containerColor = restContainer,
            contentColor = restContent,
            shapes = connectedGroupShapes(GroupPosition.Last, radius),
            interactionSource = nextSource,
            onClick = onNext,
        )
    }
}

/** Title block: small thumbnail + title + author/time. */
@Composable
internal fun CompanionVideoHeader(
    video: ContentItem,
    positionMs: Long,
    durationMs: Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            url = video.thumbnailUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .width(96.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(BluejayTokens().radius.sm)),
            contentScale = ContentScale.Crop,
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text =
                    buildString {
                        video.author?.let { append(it.name) }
                        if (durationMs > 0) {
                            if (isNotEmpty()) append("  •  ")
                            append("${formatDuration(positionMs)} / ${formatDuration(durationMs)}")
                        }
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Info tab: the main player's channel row (icon-only subscribe button) +
 * a scrollable description card.
 */
@Composable
internal fun CompanionInfoTab(
    video: ContentItem,
    savedTypes: Set<SavedVideoType>,
    isSubscribed: Boolean,
    onSubscribe: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onMore: () -> Unit,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onChannelClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val stats =
        buildList {
            video.viewCount?.let { add("${formatViewCount(it)} views") }
            video.publishedAt?.let { if (it > 0) add(formatRelativeTime(it)) }
        }.joinToString(" • ")

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChannelRow(
            author = video.author,
            viewCount = video.viewCount,
            publishedAt = video.publishedAt,
            likeCount = video.likeCount,
            isLiked = SavedVideoType.LIKED in savedTypes,
            dislikeCount = video.dislikeCount,
            isDisliked = SavedVideoType.DISLIKED in savedTypes,
            isSubscribed = isSubscribed,
            onSubscribe = onSubscribe,
            onLike = onLike,
            onDislike = onDislike,
            onMore = onMore,
            onChannelClick = onChannelClick,
            subscribeIconOnly = true,
            startPadding = 0.dp,
        )
        // Same card format as the main player's description: stats first
        // line, linkified text (timestamps + links), always expanded
        // (the card scrolls, no Show more/less on the second screen).
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            shape = RoundedCornerShape(BluejayTokens().radius.md),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            val description = video.description.orEmpty()
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                if (stats.isNotEmpty()) {
                    Text(
                        text = stats,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinkifiedText(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        onTimestampClick = { ms -> onSeekTo(ms.coerceIn(0L, durationMs.coerceAtLeast(0L))) },
                        onLinkClick = { url ->
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                )
                            }
                        },
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No description available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Controls tab: playback, volume and brightness sliders. The playback
 * slider expands to fill the page; the two device sliders sit below it.
 *
 * Volume and brightness drive the SYSTEM (same knobs as the player's
 * gestures, via SystemControls): music stream volume, and device-wide
 * brightness when WRITE_SETTINGS is granted. Without the grant the
 * brightness falls back to window-local, applied to this Presentation
 * window AND the host activity window so both screens follow.
 */
@Composable
internal fun CompanionControlsTab(
    positionMs: Long,
    durationMs: Long,
    isLive: Boolean,
    onSeekTo: (Long) -> Unit,
    companionWindow: Window?,
) {
    val context = LocalContext.current
    // Dragged seek position — the slider follows the finger, the player
    // only seeks on release.
    var seekDrag by remember { mutableStateOf<Float?>(null) }
    var volume by remember { mutableStateOf(1f) }
    var brightness by remember { mutableStateOf(1f) }
    // Resample each time the tab is composed (it is keyed per tab), so
    // changes made elsewhere (hardware keys, main-screen gestures) show.
    LaunchedEffect(Unit) {
        volume = SystemControls.getVolume(context)
        brightness = SystemControls.readBrightness(context)
        // Follow brightness changes made from the other screen (gestures
        // there apply to this display through the shared flow).
        SystemControls.brightness.collect { v ->
            v?.let {
                brightness = it
                companionWindow?.let { w -> SystemControls.setWindowBrightness(w, it) }
            }
        }
    }
    val onVolume: (Float) -> Unit = {
        volume = it
        SystemControls.setVolume(context, it)
        // Bus event so the main screen shows the volume badge like its own actions.
        PlayerEventBus.emit(PlayerEvent.VolumeChanged(it))
    }
    val onBrightness: (Float) -> Unit = { value ->
        brightness = value
        // All screens: device-wide when granted, plus this window; the
        // main window follows through SystemControls.brightness.
        SystemControls.applyBrightness(context, value, companionWindow)
        PlayerEventBus.emit(PlayerEvent.BrightnessChanged(value))
    }
    // Fixed slot widths keep every slider the same length: time strings
    // on the playback row, icon/percent on the others.
    // 8dp below the tab strip — same first-item gap as the other tabs.
    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            shape = RoundedCornerShape(BluejayTokens().radius.md),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                // Distribute the three rows across the card height —
                // spacedBy left a dead zone below them.
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                CompanionSliderRow(
                    leading = {
                        Text(
                            text =
                                if (isLive || durationMs <= 0) {
                                    "Live"
                                } else {
                                    formatTime(seekDrag?.let { (it * durationMs).toLong() } ?: positionMs)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailing = {
                        if (!isLive && durationMs > 0) {
                            Text(
                                text = formatTime(durationMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    value =
                        seekDrag
                            ?: (
                                if (durationMs > 0) {
                                    (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                            ),
                    onValueChange = { seekDrag = it },
                    onValueChangeFinished = {
                        seekDrag?.let { onSeekTo((it * durationMs).toLong()) }
                        seekDrag = null
                    },
                    enabled = !isLive && durationMs > 0,
                )
                CompanionSliderRow(
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = "Volume",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailing = {
                        Text(
                            text = "${(volume * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    value = volume,
                    onValueChange = onVolume,
                )
                CompanionSliderRow(
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.Brightness7,
                            contentDescription = "Brightness",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailing = {
                        Text(
                            text = "${(brightness * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    value = brightness,
                    onValueChange = onBrightness,
                )
            }
        }
    }
}

@Composable
internal fun CompanionSliderRow(
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    enabled: Boolean = true,
) {
    // 56dp slots: wide enough for "1:23:45", fixed so every slider
    // spans exactly the same width.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.width(56.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            leading()
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier.width(56.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            trailing()
        }
    }
}

/** Unwrap ContextWrappers to the host activity (null if none). */
internal fun hostActivity(context: Context): Activity? {
    var c: Context = context
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
/** The current video as an options-sheet card (three-dot menu / long-press). */
internal fun ContentItem.toCoreVideoCard(): CoreVideoCard =
    CoreVideoCard(
        id = id,
        title = title,
        thumbnailUrl = thumbnailUrl,
        author = author?.name,
        authorUrl = author?.url,
        durationMs = durationMs,
        viewCount = viewCount,
        publishedAt = publishedAt,
        url = url,
    )

