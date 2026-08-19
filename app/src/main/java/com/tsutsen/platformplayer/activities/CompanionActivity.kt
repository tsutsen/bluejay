package com.tsutsen.platformplayer.activities

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.data.repository.HomeRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.designsystem.component.CommentCardView
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import com.tsutsen.platformplayer.core.designsystem.component.OptionTile
import com.tsutsen.platformplayer.core.designsystem.component.OptionTileView
import com.tsutsen.platformplayer.core.designsystem.component.VideoCardFull
import com.tsutsen.platformplayer.core.designsystem.component.VideoCardPills
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.core.designsystem.component.formatDuration
import com.tsutsen.platformplayer.core.designsystem.theme.GrayjayTheme
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.CommentItem
import com.tsutsen.platformplayer.core.model.LibrarySection
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.model.VideoCard as CoreVideoCard
import com.tsutsen.platformplayer.core.ui.AsyncImage
import com.tsutsen.platformplayer.feature.library.impl.PlaylistCardView
import dagger.hilt.android.AndroidEntryPoint
import java.lang.ref.WeakReference
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Renders the second-display companion (dual screen).
 *
 * Launched on the rear display via [start] when the dual-screen setting is
 * on. Three fixed pages you flick between vertically; everything inside a
 * page scrolls horizontally so the gestures never conflict:
 *  0. current video — controls, title block, comments/recommended strips
 *  1. library — up to four horizontal slots (Watch Later, Liked, ...)
 *  2. home — two horizontal rows of feed cards
 *
 * All data comes from the shared repositories, so the screen stays in sync
 * with the main app without a second ViewModel. Comments and recommendations
 * are read from the shared [PlayerState] — the main player's ViewModel fetches
 * them once and pushes them there, so nothing is fetched twice.
 */
@AndroidEntryPoint
@OptIn(ExperimentalFoundationApi::class)
class CompanionActivity : ComponentActivity() {

    @Inject lateinit var playerRepository: PlayerRepository
    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject lateinit var homeRepository: HomeRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    companion object {
        private const val TAG = "CompanionActivity"
        private const val EXTRA_DISPLAY_ID = "displayId"
        private var instance: WeakReference<CompanionActivity>? = null
        // Set while a launch is in flight (before onCreate registers
        // [instance]) so start() called twice in quick succession — e.g.
        // MainActivity.onStart plus the settings LaunchedEffect — doesn't
        // launch a second window. Short on purpose: it only guards against
        // same-moment double fires, and must expire quickly so a silently
        // dropped launch (rear display busy with the front display's return
        // transition) doesn't block the retries MainActivity schedules.
        private const val LAUNCH_PENDING_TTL_MS = 2_000L
        private var launchPendingUntilMs = 0L

        fun start(context: Context, enabled: Boolean) {
            val current = instance?.get()
            if (!enabled) {
                // Close the companion window if it's open
                if (current != null && !current.isDestroyed) current.finish()
                return
            }
            // Already running — don't launch a duplicate. A finishing/destroyed
            // instance (e.g. killed while the app was backgrounded) counts as
            // dead and gets relaunched below.
            if (current != null && !current.isFinishing && !current.isDestroyed) {
                Logger.v(TAG, "start(): companion already running, skip")
                return
            }
            if (SystemClock.uptimeMillis() < launchPendingUntilMs) {
                Logger.v(TAG, "start(): launch already in flight, skip")
                return
            }

            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            if (display == null) {
                Logger.w(TAG, "start(): no second display found")
                return
            }
            val intent = Intent(context, CompanionActivity::class.java)
                .putExtra(EXTRA_DISPLAY_ID, display.displayId)
            // Activity.setDisplay was removed in SDK 36 — launch on the
            // target display via ActivityOptions instead.
            val options = ActivityOptions.makeBasic().apply { launchDisplayId = display.displayId }
            launchPendingUntilMs = SystemClock.uptimeMillis() + LAUNCH_PENDING_TTL_MS
            try {
                Logger.i(TAG, "start(): launching companion on display ${display.displayId}")
                context.startActivity(intent, options.toBundle())
            } catch (t: Throwable) {
                launchPendingUntilMs = 0L
                throw t
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val displayId = intent?.getIntExtra(EXTRA_DISPLAY_ID, -1) ?: -1
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(displayId)
        launchPendingUntilMs = 0L
        if (displayId == Display.DEFAULT_DISPLAY || display == null) {
            // No second screen (or stale display id after a hot-unplug).
            finish()
            return
        }
        instance = WeakReference(this)

        val playerRepository = playerRepository
        val libraryRepository = libraryRepository
        val homeRepository = homeRepository
        val settingsRepository = settingsRepository
        setContent {
            // Follow the user's theme settings — same computation as
            // MainActivity, so the second screen matches the main app.
            val prefs by settingsRepository.preferences.collectAsState(initial = AppPreferences())
            val darkTheme =
                when (prefs.appearance.themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.AUTO -> isSystemInDarkTheme()
                }
            GrayjayTheme(darkTheme = darkTheme, dynamicColor = prefs.appearance.dynamicColor) {
                CompanionContent(
                    playerRepository = playerRepository,
                    libraryRepository = libraryRepository,
                    homeRepository = homeRepository,
                )
            }
        }
    }
}

/**
 * The whole second screen: a vertical pager with three fixed pages.
 * Vertical flick changes pages; horizontal swipes scroll the strips inside
 * each page. No vertical scrolling anywhere.
 */
@ExperimentalFoundationApi
@Composable
private fun CompanionContent(
    playerRepository: PlayerRepository,
    libraryRepository: LibraryRepository,
    homeRepository: HomeRepository,
) {
    val playerState by playerRepository.playerState.collectAsState()
    val scope = rememberCoroutineScope()
    val video = playerState.currentVideo

    // Same data the main screen shows: the PlayerViewModel fetches comments
    // and recommendations once per video and pushes them into the shared
    // player state — the companion just reads them. No second fetch, no
    // polling, no thread juggling.
    val comments = playerState.comments
    // distinctBy: duplicate urls from the engine crash the strip's keying.
    val recommendations = playerState.recommendations
        .filterIsInstance<CoreVideoCard>()
        .distinctBy { it.url }

    // Live library + home data (shared repositories — updates propagate).
    val sections by libraryRepository.sections.collectAsState()
    val home by homeRepository.feed.collectAsState()
    LaunchedEffect(Unit) { homeRepository.loadInitial() }

    var selectedTab by remember { mutableIntStateOf(0) }

    val onPlay: (String) -> Unit = { url -> scope.launch { playerRepository.play(url) } }

    val pagerState = rememberPagerState(pageCount = { 3 })
    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        when (page) {
            0 -> CompanionVideoPage(
                playerState = playerState,
                video = video,
                comments = comments,
                recommendations = recommendations,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onPlayPause = {
                    scope.launch {
                        if (playerState.isPlaying) playerRepository.pause()
                        else playerRepository.resume()
                    }
                },
                onSeekBy = { deltaMs ->
                    val target = (playerState.currentPositionMs + deltaMs)
                        .coerceIn(0L, if (playerState.durationMs > 0) playerState.durationMs else Long.MAX_VALUE)
                    scope.launch { playerRepository.seekTo(target) }
                },
                onPrevious = {
                    val index = playerState.selectedIndex
                    if (playerState.queue.isNotEmpty() && index > 0) {
                        scope.launch { playerRepository.play(playerState.queue[index - 1].id) }
                    }
                },
                onNext = {
                    val index = playerState.selectedIndex
                    if (playerState.queue.isNotEmpty() && index + 1 < playerState.queue.size) {
                        scope.launch { playerRepository.play(playerState.queue[index + 1].id) }
                    }
                },
                onSeekTo = { ms -> scope.launch { playerRepository.seekTo(ms) } },
                onPlay = onPlay,
            )

            1 -> CompanionLibraryPage(sections = sections, onPlay = onPlay)

            2 ->
                CompanionHomePage(
                    items = home.items,
                    onLoadNextPage = { scope.launch { homeRepository.loadNextPage() } },
                    onPlay = onPlay,
                )
        }
    }
}

/**
 * Page 0: the currently playing video. Controls aligned to the top, then
 * the title block, then the comments/recommended tabs + horizontal strip.
 */
@Composable
private fun CompanionVideoPage(
    playerState: com.tsutsen.platformplayer.core.model.PlayerState,
    video: ContentItem?,
    comments: List<CommentItem>,
    recommendations: List<CoreVideoCard>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlay: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CompanionControlRow(
            isPlaying = playerState.isPlaying,
            onPlayPause = onPlayPause,
            onSeekBy = onSeekBy,
            onPrevious = onPrevious,
            onNext = onNext,
        )
        if (video != null) {
            CompanionVideoHeader(
                video = video,
                positionMs = playerState.currentPositionMs,
                durationMs = playerState.durationMs,
            )
            val chapters = playerState.chapters
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                CompanionTab("Comments", selected = selectedTab == 0) { onTabSelected(0) }
                CompanionTab("Chapters", selected = selectedTab == 1) { onTabSelected(1) }
                CompanionTab("Recommended", selected = selectedTab == 2) { onTabSelected(2) }
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 8.dp),
            ) {
                if (selectedTab == 0) {
                    if (comments.isEmpty()) {
                        item(key = "no-comments") {
                            Text(
                                "No comments",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    items(comments, key = { it.id }) { comment ->
                        CommentCardView(comment = comment)
                    }
                } else if (selectedTab == 1) {
                    if (chapters.isEmpty()) {
                        item(key = "no-chapters") {
                            Text(
                                "No chapters",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    itemsIndexed(chapters, key = { _, c -> c.startTimeMs }) { index, chapter ->
                        // Same card chrome as the comment cards.
                        Card(
                            modifier =
                                Modifier
                                    .width(240.dp)
                                    .fillMaxHeight()
                                    .combinedClickable(onClick = { onSeekTo(chapter.startTimeMs) }),
                            shape = RoundedCornerShape(Tokens.RadiusMd),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                ),
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = formatDuration(chapter.startTimeMs),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = chapter.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                } else {
                    if (recommendations.isEmpty()) {
                        item(key = "no-recs") {
                            Text(
                                "No recommendations",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    items(recommendations, key = { it.url }) { card ->
                        VideoCardFull(
                            card = card,
                            onClick = { onPlay(card.url) },
                            modifier = Modifier
                                .width(240.dp)
                                .padding(horizontal = 2.dp),
                        )
                    }
                }
            }
        } else {
            Text(
                "Nothing playing",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 40.dp),
            )
        }
    }
}

/**
 * Page 1: the library as four corner slots (2x2). Each slot is a section
 * header + a horizontal pager of that section's cards — swipe left/right
 * inside a slot to page through its list.
 */
@Composable
private fun CompanionLibraryPage(
    sections: List<LibrarySection>,
    onPlay: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        val slots = sections.take(4)
        if (slots.isEmpty()) {
            Text(
                text = "Nothing here yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LibrarySlotPager(
                section = slots.getOrNull(0),
                onPlay = onPlay,
                modifier = Modifier.weight(1f),
            )
            LibrarySlotPager(
                section = slots.getOrNull(1),
                onPlay = onPlay,
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LibrarySlotPager(
                section = slots.getOrNull(2),
                onPlay = onPlay,
                modifier = Modifier.weight(1f),
            )
            LibrarySlotPager(
                section = slots.getOrNull(3),
                onPlay = onPlay,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One corner slot: section header + a horizontal pager showing one card
 * per page. Empty slots show a placeholder.
 */
@Composable
private fun LibrarySlotPager(
    section: LibrarySection?,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = section?.title ?: "Empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val total = section?.totalCount ?: 0
            if (total > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val cards = section?.items.orEmpty()
        if (cards.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(Tokens.RadiusMd))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nothing here yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // key() recreates the pager (and its state) when the list
            // identity changes (e.g. empty -> loaded).
            key(cards) {
                val state = rememberPagerState(pageCount = { cards.size })
                HorizontalPager(
                    state = state,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    pageSpacing = 8.dp,
                ) { index ->
                    PagerCard(card = cards[index], onClick = onPlay)
                }
            }
        }
    }
}

/**
 * Page 2: the home feed as a horizontal pager of grids — each page is a
 * 2x2 grid of feed cards, swipe left/right to move between pages. Reaching
 * the last page fetches the next feed page.
 */
@Composable
private fun CompanionHomePage(
    items: List<com.tsutsen.platformplayer.core.model.Card>,
    onLoadNextPage: () -> Unit,
    onPlay: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Home",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (items.isEmpty()) {
            Text(
                text = "Nothing here yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // The pager state is recreated only when the page count changes
            // (feed grew), keeping the user's current page.
            val feedItems = remember { mutableStateOf(items) }
            LaunchedEffect(items) { feedItems.value = items }
            val pageCount = (feedItems.value.size + 3) / 4
            val lastPage = remember { mutableIntStateOf(0) }
            key(pageCount) {
                val state = rememberPagerState(
                    initialPage = lastPage.value.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
                    pageCount = { pageCount },
                )
                // Reach the last page -> fetch more. The repository no-ops
                // once the feed is exhausted, so extra triggers are harmless.
                LaunchedEffect(state) {
                    snapshotFlow { state.currentPage }
                        .distinctUntilChanged()
                        .collect { page ->
                            lastPage.value = page
                            if (page == pageCount - 1) {
                                onLoadNextPage()
                            }
                        }
                }
                HorizontalPager(
                    state = state,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    pageSpacing = 8.dp,
                ) { pageIndex ->
                    HomeGridPage(
                        pageItems = feedItems.value.chunked(4).getOrNull(pageIndex).orEmpty(),
                        onPlay = onPlay,
                    )
                }
            }
        }
    }
}

/** One pager page: a 2x2 grid of feed cards. */
@Composable
private fun HomeGridPage(
    pageItems: List<com.tsutsen.platformplayer.core.model.Card>,
    onPlay: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            HomeGridCell(pageItems.getOrNull(0), onPlay = onPlay, modifier = Modifier.weight(1f))
            HomeGridCell(pageItems.getOrNull(1), onPlay = onPlay, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            HomeGridCell(pageItems.getOrNull(2), onPlay = onPlay, modifier = Modifier.weight(1f))
            HomeGridCell(pageItems.getOrNull(3), onPlay = onPlay, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeGridCell(
    card: com.tsutsen.platformplayer.core.model.Card?,
    onPlay: (String) -> Unit,
    modifier: Modifier,
) {
    Box(modifier = modifier.padding(4.dp), contentAlignment = Alignment.Center) {
        if (card != null) {
            PagerCard(card = card, onClick = onPlay)
        }
    }
}

/** One card centred inside a pager page (or grid cell). */
@Composable
private fun PagerCard(
    card: com.tsutsen.platformplayer.core.model.Card,
    onClick: (String) -> Unit,
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
                is CoreVideoCard ->
                    // Pill variant: all meta on the thumbnail, no meta row,
                    // so the card stays short and nothing clips in tight slots.
                    VideoCardPills(
                        card = card,
                        onClick = { onClick(card.url) },
                        modifier = Modifier.width(cardWidth),
                    )

                is PlaylistCard ->
                // No navigation on the second screen.
                PlaylistCardView(card = card, onClick = {})

                else ->
                    // Channels and other card types: no navigation on the second
                    // screen.
                    Box(
                        modifier =
                            Modifier
                                .width(cardWidth)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(Tokens.RadiusSm))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
            }
        }
    }
}

/**
 * Playback controls as large tiles — same look as the main player's video
 * options sheet buttons (icon + label in a rounded box).
 */
@Composable
private fun CompanionControlRow(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        OptionTileView(
            OptionTile(label = "Previous", icon = Icons.Filled.SkipPrevious, onClick = onPrevious),
            modifier = Modifier.weight(1f),
            showLabel = false,
        )
        OptionTileView(
            OptionTile(label = "Back 10s", icon = Icons.Filled.Replay10, onClick = { onSeekBy(-10_000L) }),
            modifier = Modifier.weight(1f),
            showLabel = false,
        )
        OptionTileView(
            OptionTile(
                label = if (isPlaying) "Pause" else "Play",
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                onClick = onPlayPause,
            ),
            modifier = Modifier.weight(1f),
            showLabel = false,
        )
        OptionTileView(
            OptionTile(label = "Fwd 10s", icon = Icons.Filled.Forward10, onClick = { onSeekBy(10_000L) }),
            modifier = Modifier.weight(1f),
            showLabel = false,
        )
        OptionTileView(
            OptionTile(label = "Next", icon = Icons.Filled.SkipNext, onClick = onNext),
            modifier = Modifier.weight(1f),
            showLabel = false,
        )
    }
}

/** Title block: small thumbnail + title + author/time. */
@Composable
private fun CompanionVideoHeader(
    video: ContentItem,
    positionMs: Long,
    durationMs: Long,
) {
    Row(verticalAlignment = Alignment.Top) {
        AsyncImage(
            url = video.thumbnailUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .width(96.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(Tokens.RadiusSm)),
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
                text = buildString {
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

@Composable
private fun CompanionTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
