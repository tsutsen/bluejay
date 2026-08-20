package com.tsutsen.platformplayer.activities

import android.content.Context
import android.app.Presentation
import androidx.compose.ui.platform.ComposeView
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import com.tsutsen.platformplayer.logging.Logger
import kotlinx.coroutines.flow.distinctUntilChanged
import java.lang.reflect.Proxy
import android.os.Bundle
import android.view.Display
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
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
import com.tsutsen.platformplayer.core.designsystem.component.PillTabs
import com.tsutsen.platformplayer.core.designsystem.component.VideoCardFull
import com.tsutsen.platformplayer.core.designsystem.component.VideoCardPills
import com.tsutsen.platformplayer.core.designsystem.component.VideoOptionsSheet
import com.tsutsen.platformplayer.core.designsystem.component.formatDuration
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTheme
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.CommentItem
import com.tsutsen.platformplayer.core.model.LibrarySection
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.model.DownloadButtonState
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.VideoCard as CoreVideoCard
import com.tsutsen.platformplayer.core.ui.AsyncImage
import com.tsutsen.platformplayer.feature.library.impl.PlaylistCardView
import kotlinx.coroutines.launch

/**
 * The second-screen UI, hosted in a Presentation window on the rear display
 * — the same mechanism Cemu uses for its external display. A Presentation is
 * a window, not a task, so the AYN shell's rear-display task management (which
 * hid/evicted a companion *activity* during front-display transitions, killing
 * the second screen) never touches it.
 *
 * Three fixed pages you flick between vertically; everything inside a page
 * scrolls horizontally so the gestures never conflict:
 *  0. current video — controls, title block, comments/recommended strips
 *  1. library — up to four horizontal slots (Watch Later, Liked, ...)
 *  2. home — two horizontal rows of feed cards
 *
 * All data comes from the shared repositories, so the screen stays in sync
 * with the main app without a second ViewModel. Comments and recommendations
 * are read from the shared [PlayerState] — the main player's ViewModel fetches
 * them once and pushes them there, so nothing is fetched twice.
 */
class CompanionPresentation(
    context: Context,
    display: Display,
    private val playerRepository: PlayerRepository,
    private val libraryRepository: LibraryRepository,
    private val homeRepository: HomeRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadsRepository: com.tsutsen.platformplayer.core.data.repository.DownloadsRepository,
) : Presentation(context, display) {

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ComposeView (not the internal AndroidComposeView) — the only
        // public Compose host view, and it's a plain View so it can live in
        // a Presentation window.
        @Suppress("DEPRECATION")
        val composeView = ComposeView(context)
        // A Presentation window carries no ViewTreeLifecycleOwner (unlike an
        // activity window), but Compose requires one in the hierarchy — bind
        // the owning activity's lifecycle (always present: MainActivity
        // creates us), falling back to a permanently-resumed owner.
        val lifecycleOwner =
            (context as? LifecycleOwner) ?: object : LifecycleOwner {
                private val registry = LifecycleRegistry(this)
                init { registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
                override val lifecycle: Lifecycle
                    get() = registry
            }
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        attachSavedStateOwner(composeView)

        composeView.layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        setContentView(composeView)
        composeView.setContent {
            // Follow the user's theme settings — same computation as
            // MainActivity, so the second screen matches the main app.
            val prefs by settingsRepository.preferences.collectAsState(initial = AppPreferences())
            val darkTheme =
                when (prefs.appearance.themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.AUTO -> isSystemInDarkTheme()
                }
            BluejayTheme(darkTheme = darkTheme, dynamicColor = prefs.appearance.dynamicColor) {
                CompanionContent(
                    playerRepository = playerRepository,
                    libraryRepository = libraryRepository,
                    homeRepository = homeRepository,
                    downloadsRepository = downloadsRepository,
                )
            }
        }
    }

    /**
     * Compose requires a ViewTreeSavedStateRegistryOwner in the hierarchy.
     * The androidx.savedstate classes ship in the APK but are not exposed to
     * this module's Kotlin compile classpath (KMP variant quirk), so the
     * owner is wired up reflectively — it is plain, stable API.
     */
    private fun attachSavedStateOwner(view: android.view.View) {
        // Dedicated registry (not the activity's): performRestore() must run
        // while the owner is still in its initialization stage (like
        // ComponentActivity does in onCreate), so it must start INITIALIZED
        // and never be advanced.
        val savedStateLifecycle = object : LifecycleOwner {
            private val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle
                get() = registry
        }
        try {
            val ownerItf = Class.forName("androidx.savedstate.SavedStateRegistryOwner")
            val controllerCls = Class.forName("androidx.savedstate.SavedStateRegistryController")
            val companion = controllerCls.getField("Companion").get(null)
            val create = controllerCls.getMethod("create", ownerItf)
            var controller: Any? = null
            val owner = Proxy.newProxyInstance(
                ownerItf.classLoader,
                arrayOf(ownerItf),
            ) { proxy, method, args ->
                when (method.name) {
                    "getLifecycle" -> savedStateLifecycle.lifecycle
                    "getSavedStateRegistry" -> {
                        if (controller == null) controller = create.invoke(companion, proxy)
                        controller!!
                            .javaClass
                            .getMethod("getSavedStateRegistry")
                            .invoke(controller)
                    }
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    "toString" -> "CompanionSavedStateOwner"
                    else -> throw UnsupportedOperationException(method.name)
                }
            }
            // create() only calls getLifecycle() (not getSavedStateRegistry),
            // so this cannot recurse.
            controller = create.invoke(companion, owner)
            controller!!
                .javaClass
                .getMethod("performRestore", android.os.Bundle::class.java)
                .invoke(controller, null)
            val set = Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
                .getMethod("set", android.view.View::class.java, ownerItf)
            set.invoke(null, view, owner)
        } catch (t: Throwable) {
            var root: Throwable? = t
            while (root?.cause != null) root = root.cause
            Logger.w(TAG, t) { "Could not attach saved-state owner (root: $root)" }
        }
    }

    private companion object {
        const val TAG = "CompanionPresentation"
    }
}

/**
 * The whole second screen: a vertical pager with three fixed pages.
 * Vertical flick changes pages; horizontal swipes scroll the strips inside
 * each page. No vertical scrolling anywhere.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CompanionContent(
    playerRepository: PlayerRepository,
    libraryRepository: LibraryRepository,
    homeRepository: HomeRepository,
    downloadsRepository: com.tsutsen.platformplayer.core.data.repository.DownloadsRepository,
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
    // NOTE: deliberately no loadInitial() here — the main app triggers the
    // home load. A second concurrent call would re-run JS-client init and
    // deadlocks the V8 busy lock (see PackageHttp.autoParallelPool).

    var selectedTab by remember { mutableIntStateOf(0) }
    var optionsCard by remember { mutableStateOf<CoreVideoCard?>(null) }

    val onPlay: (String) -> Unit = { url -> scope.launch { playerRepository.play(url) } }
    val onLongClick: (CoreVideoCard) -> Unit = { card -> optionsCard = card }

    val pagerState = rememberPagerState(pageCount = { 3 })

    // Native M3 bottom sheet for the long-press options — material3's
    // BottomSheetScaffold gives the standard Android sheet physics (drag,
    // collapse, expand, handle). It is pure in-composition layout (no Popup
    // window, unlike ModalBottomSheet), so it works inside a Presentation
    // window. Skip the partial state: the sheet toggles hidden <-> expanded.
    // (rememberModalBottomSheetState is the public SheetState factory in
    // material3 1.4.0 — the state class is shared between the modal and
    // scaffold variants.)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    // BottomSheetScaffold is non-modal (no scrim, no outside-tap). Add the
    // standard modal affordance: dim the content while the sheet is
    // expanded; tapping the scrim hides the sheet.
    val scrimAlpha by animateFloatAsState(
        targetValue = if (sheetState.targetValue == SheetValue.Expanded) 0.4f else 0f,
        animationSpec = tween(200),
        label = "sheetScrim",
    )
    LaunchedEffect(optionsCard) {
        if (optionsCard != null) sheetState.expand() else sheetState.hide()
    }
    // Dragging the sheet down dismisses it — clear the selection so the
    // sheet content goes away.
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.targetValue }
            .distinctUntilChanged()
            .collect { value ->
                if (value == SheetValue.Hidden && optionsCard != null) {
                    optionsCard = null
                }
            }
    }

    // No peek: the sheet only ever opens programmatically (long-press), so
    // a 56dp peek would just reserve a blank strip below the content.
    // 0dp keeps the content's top and bottom insets equal.
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        sheetContent = {
            optionsCard?.let { card ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp),
                ) {
                    CompanionVideoOptionsSheet(
                        card = card,
                        onDismiss = { optionsCard = null },
                        onPlay = onPlay,
                        libraryRepository = libraryRepository,
                        downloadsRepository = downloadsRepository,
                        scope = scope,
                    )
                }
            }
        },
        content = { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
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
                    onLongClick = onLongClick,
                )

                1 -> CompanionLibraryPage(sections = sections, onPlay = onPlay, onLongClick = onLongClick)

                2 ->
                    CompanionHomePage(
                        items = home.items,
                        onLoadNextPage = { scope.launch { homeRepository.loadNextPage() } },
                        onPlay = onPlay,
                        onLongClick = onLongClick,
                    )
            }
        }
        if (scrimAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable { scope.launch { sheetState.hide() } },
            )
        }
        }
        },
    )
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
    onLongClick: (CoreVideoCard) -> Unit,
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
            // One scroll state per tab — switching tabs never shares or
            // resets a strip's position.
            val tabStates = remember { List(3) { LazyListState() } }
            val currentChapterIndex =
                chapters.indexOfLast { it.startTimeMs <= playerState.currentPositionMs }
            // Follow the playhead: while on the chapters tab, a chapter
            // change scrolls the strip to the newly active chapter.
            LaunchedEffect(currentChapterIndex) {
                if (selectedTab == 1 && currentChapterIndex >= 0) {
                    tabStates[1].animateScrollToItem(currentChapterIndex)
                }
            }
            PillTabs(
                labels = listOf("Comments", "Chapters", "Recommended"),
                selected = selectedTab,
                onSelect = onTabSelected,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                key(selectedTab) {
                    LazyRow(
                        state = tabStates[selectedTab],
                        modifier = Modifier.fillMaxSize(),
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
                        // Same card chrome as the comment cards; the chapter
                        // containing the current playhead is highlighted.
                        val scheme = MaterialTheme.colorScheme
                        val isCurrent = index == currentChapterIndex
                        // Animated highlight: the active chapter eases in.
                        val containerColor by animateColorAsState(
                            targetValue =
                                if (isCurrent) scheme.primaryContainer
                                else scheme.surfaceContainer,
                            animationSpec = tween(300),
                            label = "chapterBg",
                        )
                        val cardFg by animateColorAsState(
                            targetValue =
                                if (isCurrent) scheme.onPrimaryContainer
                                else scheme.onSurface,
                            animationSpec = tween(300),
                            label = "chapterFg",
                        )
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
                                    containerColor = containerColor,
                                ),
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = cardFg,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = formatDuration(chapter.startTimeMs),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = cardFg,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = chapter.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cardFg,
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
                            onLongClick = { onLongClick(card) },
                            modifier = Modifier
                                .width(240.dp)
                                .padding(horizontal = 2.dp),
                        )
                    }
                }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Nothing playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    onLongClick: (CoreVideoCard) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
                onLongClick = onLongClick,
                modifier = Modifier.weight(1f),
            )
            LibrarySlotPager(
                section = slots.getOrNull(1),
                onPlay = onPlay,
                onLongClick = onLongClick,
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LibrarySlotPager(
                section = slots.getOrNull(2),
                onPlay = onPlay,
                onLongClick = onLongClick,
                modifier = Modifier.weight(1f),
            )
            LibrarySlotPager(
                section = slots.getOrNull(3),
                onPlay = onPlay,
                onLongClick = onLongClick,
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
    onLongClick: (CoreVideoCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(4.dp),
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
                    PagerCard(card = cards[index], onClick = onPlay, onLongClick = onLongClick)
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
    onLongClick: (CoreVideoCard) -> Unit,
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
                        onLongClick = onLongClick,
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
    onLongClick: (CoreVideoCard) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            HomeGridCell(pageItems.getOrNull(0), onPlay = onPlay, onLongClick = onLongClick, modifier = Modifier.weight(1f))
            HomeGridCell(pageItems.getOrNull(1), onPlay = onPlay, onLongClick = onLongClick, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            HomeGridCell(pageItems.getOrNull(2), onPlay = onPlay, onLongClick = onLongClick, modifier = Modifier.weight(1f))
            HomeGridCell(pageItems.getOrNull(3), onPlay = onPlay, onLongClick = onLongClick, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeGridCell(
    card: com.tsutsen.platformplayer.core.model.Card?,
    onPlay: (String) -> Unit,
    onLongClick: (CoreVideoCard) -> Unit,
    modifier: Modifier,
) {
    Box(modifier = modifier.padding(4.dp), contentAlignment = Alignment.Center) {
        if (card != null) {
            PagerCard(card = card, onClick = onPlay, onLongClick = onLongClick)
        }
    }
}

/**
 * Long-press video options for the second screen. Mirrors the main app's
 * VideoOptionsSheet but is wired directly against the shared repositories —
 * no Hilt ViewModel, since a Presentation has no ViewModelStoreOwner.
 */
@Composable
private fun CompanionVideoOptionsSheet(
    card: CoreVideoCard,
    onDismiss: () -> Unit,
    onPlay: (String) -> Unit,
    libraryRepository: LibraryRepository,
    downloadsRepository: com.tsutsen.platformplayer.core.data.repository.DownloadsRepository,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val savedTypes by libraryRepository.observeSavedTypes(card.url).collectAsState(initial = emptySet())
    val playlists by libraryRepository.playlists.collectAsState(initial = emptyList())
    val containedPlaylists by libraryRepository
        .observePlaylistsContaining(card.url)
        .collectAsState(initial = emptySet())
    var downloading by remember { mutableStateOf(false) }

    VideoOptionsSheet(
        url = card.url,
        onDismiss = onDismiss,
        onPlay = {
            onPlay(card.url)
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
                if (downloading) {
                    downloading = false
                    downloadsRepository.cancelDownload(card.url)
                } else {
                    downloading = true
                    downloadsRepository.startDownload(card.url)
                }
            }
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
        containedPlaylistIds = containedPlaylists,
        downloadState = if (downloading) DownloadButtonState.Downloading(0f) else DownloadButtonState.Idle,
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

private suspend fun toggleSaveType(
    libraryRepository: LibraryRepository,
    savedTypes: Set<SavedVideoType>,
    card: CoreVideoCard,
    type: SavedVideoType,
) {
    if (savedTypes.contains(type)) {
        libraryRepository.removeSavedVideo(type, card.url)
    } else {
        libraryRepository.saveVideo(type, card)
    }
}

/** One card centred inside a pager page (or grid cell). */
@Composable
private fun PagerCard(
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
                is CoreVideoCard ->
                    // Pill variant: all meta on the thumbnail, no meta row,
                    // so the card stays short and nothing clips in tight slots.
                    VideoCardPills(
                        card = card,
                        onClick = { onClick(card.url) },
                        onLongClick = { onLongClick(card) },
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
        val tileModifier: Modifier = Modifier.weight(1f).height(80.dp)
        OptionTileView(
            OptionTile(label = "Previous", icon = Icons.Filled.SkipPrevious, onClick = onPrevious),
            modifier = tileModifier,
            showLabel = false,
        )
        OptionTileView(
            OptionTile(label = "Back 10s", icon = Icons.Filled.Replay10, onClick = { onSeekBy(-10_000L) }),
            modifier = tileModifier,
            showLabel = false,
        )
        OptionTileView(
            OptionTile(
                label = if (isPlaying) "Pause" else "Play",
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                onClick = onPlayPause,
            ),
            modifier = tileModifier,
            showLabel = false,
        )
        OptionTileView(
            OptionTile(label = "Fwd 10s", icon = Icons.Filled.Forward10, onClick = { onSeekBy(10_000L) }),
            modifier = tileModifier,
            showLabel = false,
        )
        OptionTileView(
            OptionTile(label = "Next", icon = Icons.Filled.SkipNext, onClick = onNext),
            modifier = tileModifier,
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

