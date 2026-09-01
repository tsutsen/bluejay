package com.tsutsen.platformplayer.activities

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import android.view.Window
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import com.tsutsen.platformplayer.core.data.repository.ChannelRepository
import com.tsutsen.platformplayer.core.data.repository.HomeRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import com.tsutsen.platformplayer.core.designsystem.component.CommentCardView
import com.tsutsen.platformplayer.core.designsystem.component.LinkifiedText
import com.tsutsen.platformplayer.core.designsystem.component.OptionTile
import com.tsutsen.platformplayer.core.designsystem.component.OptionTileView
import com.tsutsen.platformplayer.core.designsystem.component.PillTabs
import com.tsutsen.platformplayer.core.designsystem.component.QueueStripCard
import com.tsutsen.platformplayer.core.designsystem.component.VideoCardFull
import com.tsutsen.platformplayer.core.designsystem.component.VideoCardPills
import com.tsutsen.platformplayer.core.designsystem.component.VideoOptionsSheet
import com.tsutsen.platformplayer.core.designsystem.component.formatDuration
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTheme
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.ThemeEngine
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.CommentItem
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.DownloadButtonState
import com.tsutsen.platformplayer.core.model.LibrarySection
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.ui.AsyncImage
import com.tsutsen.platformplayer.feature.library.impl.PlaylistCardView
import com.tsutsen.platformplayer.feature.player.impl.ChannelRow
import com.tsutsen.platformplayer.feature.player.impl.PlayerEvent
import com.tsutsen.platformplayer.feature.player.impl.PlayerEventBus
import com.tsutsen.platformplayer.feature.player.impl.SystemControls
import com.tsutsen.platformplayer.feature.player.impl.formatRelativeTime
import com.tsutsen.platformplayer.feature.player.impl.formatTime
import com.tsutsen.platformplayer.feature.player.impl.formatViewCount
import com.tsutsen.platformplayer.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.Proxy
import kotlin.math.roundToInt
import com.tsutsen.platformplayer.core.model.VideoCard as CoreVideoCard

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
    private val playbackQueueRepository: com.tsutsen.platformplayer.core.data.repository.PlaybackQueueRepository,
    private val liveChatRepository: com.tsutsen.platformplayer.core.data.repository.LiveChatRepository,
    private val channelRepository: ChannelRepository,
    private val onChannelClick: (String) -> Unit,
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

                init {
                    registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                }

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
            val appearance = prefs.appearance
            val activeTheme =
                appearance.customThemes.firstOrNull { it.id == appearance.activeThemeId }
            val customSchemes =
                remember(activeTheme) {
                    activeTheme?.let {
                        ThemeEngine.generate(it.primary, it.secondary, it.tertiary, it.paletteStyle)
                    }
                }

            BluejayTheme(
                darkTheme = darkTheme,
                dynamicColor = appearance.dynamicColor,
                uiRounding = appearance.uiRounding,
                colorScheme = customSchemes?.let { if (darkTheme) it.dark else it.light },
            ) {
                CompanionContent(
                    playerRepository = playerRepository,
                    libraryRepository = libraryRepository,
                    homeRepository = homeRepository,
                    downloadsRepository = downloadsRepository,
                    playbackQueueRepository = playbackQueueRepository,
                    settingsRepository = settingsRepository,
                    liveChatRepository = liveChatRepository,
                    channelRepository = channelRepository,
                    companionWindow = window,
                    onChannelClick = onChannelClick,
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
        val savedStateLifecycle =
            object : LifecycleOwner {
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
            val owner =
                Proxy.newProxyInstance(
                    ownerItf.classLoader,
                    arrayOf(ownerItf),
                ) { proxy, method, args ->
                    when (method.name) {
                        "getLifecycle" -> {
                            savedStateLifecycle.lifecycle
                        }

                        "getSavedStateRegistry" -> {
                            if (controller == null) controller = create.invoke(companion, proxy)
                            controller!!
                                .javaClass
                                .getMethod("getSavedStateRegistry")
                                .invoke(controller)
                        }

                        "hashCode" -> {
                            System.identityHashCode(proxy)
                        }

                        "equals" -> {
                            proxy === args?.getOrNull(0)
                        }

                        "toString" -> {
                            "CompanionSavedStateOwner"
                        }

                        else -> {
                            throw UnsupportedOperationException(method.name)
                        }
                    }
                }
            // create() only calls getLifecycle() (not getSavedStateRegistry),
            // so this cannot recurse.
            controller = create.invoke(companion, owner)
            controller!!
                .javaClass
                .getMethod("performRestore", android.os.Bundle::class.java)
                .invoke(controller, null)
            val set =
                Class
                    .forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
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
    playbackQueueRepository: com.tsutsen.platformplayer.core.data.repository.PlaybackQueueRepository,
    settingsRepository: SettingsRepository,
    liveChatRepository: com.tsutsen.platformplayer.core.data.repository.LiveChatRepository,
    channelRepository: ChannelRepository,
    companionWindow: Window?,
    onChannelClick: (String) -> Unit,
) {
    val playerState by playerRepository.playerState.collectAsState()
    val liveChat by liveChatRepository.state.collectAsState()
    val queue by playbackQueueRepository.queue.collectAsState()
    val scope = rememberCoroutineScope()
    val video = playerState.currentVideo

    // Brightness changes made from the other screen (gestures there apply
    // to this display through the shared flow) follow for the whole
    // presentation lifetime, not just while the Controls tab is composed.
    LaunchedEffect(companionWindow) {
        SystemControls.brightness.collect { v ->
            if (v != null && companionWindow != null) SystemControls.setWindowBrightness(companionWindow, v)
        }
    }

    // Dual screen settings: which pages / video-page tabs / library
    // sections the second screen shows (Settings > Dual screen).
    val prefs by settingsRepository.preferences.collectAsState(initial = AppPreferences())
    val pageKeys = listOf("video", "library", "home").filter { it in prefs.dualScreenPages }
    // Configured order wins; canonical keys appended as fallback (older
    // saves predate info/controls), then filtered to the enabled set.
    val allVideoTabKeys =
        listOf("info", "controls", "comments", "chapters", "recommended", "queue", "dot")
    val videoTabKeys =
        (prefs.dualScreenVideoTabOrder + allVideoTabKeys)
            .distinct()
            .filter { it in prefs.dualScreenVideoTabs }
    val pageOrder =
        (prefs.dualScreenPageOrder + listOf("controls", "video", "tabs")).distinct()
    val librarySlotValues = prefs.dualScreenLibrarySlots

    // Same data the main screen shows: the PlayerViewModel fetches comments
    // and recommendations once per video and pushes them into the shared
    // player state — the companion just reads them. No second fetch, no
    // polling, no thread juggling.
    val comments = playerState.comments
    val isLive = video?.contentType == com.tsutsen.platformplayer.core.model.ContentType.LIVE
    // distinctBy: duplicate urls from the engine crash the strip's keying.
    val recommendations =
        playerState.recommendations
            .filterIsInstance<CoreVideoCard>()
            .distinctBy { it.url }

    // Live library + home data (shared repositories — updates propagate).
    val sections by libraryRepository.sections.collectAsState()
    val home by homeRepository.feed.collectAsState()
    // Second-screen home feed: which sources to show (empty = all).
    // Filtered here, not in the repository — the main screen's home page
    // shares the same feed and has its own (chip-based) filter.
    val dualFeedSources = prefs.dualScreenFeedSources
    val dualHomeItems =
        if (dualFeedSources.isEmpty()) {
            home.items
        } else {
            home.items.filter { it.sourceId == null || it.sourceId in dualFeedSources }
        }
    // NOTE: deliberately no loadInitial() here — the main app triggers the
    // home load. A second concurrent call would re-run JS-client init and
    // deadlocks the V8 busy lock (see PackageHttp.autoParallelPool).

    var selectedTab by remember { mutableIntStateOf(0) }
    // Settings can shrink the tab list while the page is up — reset the
    // selection to the first tab.
    LaunchedEffect(videoTabKeys) {
        if (videoTabKeys.isNotEmpty() && selectedTab >= videoTabKeys.size) {
            selectedTab = 0
        }
    }

    var optionsCard by remember { mutableStateOf<CoreVideoCard?>(null) }

    val onPlay: (String) -> Unit = { url -> scope.launch { playerRepository.play(url) } }
    val onLongClick: (CoreVideoCard) -> Unit = { card -> optionsCard = card }

    // Info tab: like/dislike state from the library, subscribe state from
    // the channel repository (same actions as the main player row).
    val videoUrl = video?.url
    val savedTypesFlow: kotlinx.coroutines.flow.Flow<Set<SavedVideoType>> =
        remember(videoUrl) {
            videoUrl?.let { libraryRepository.observeSavedTypes(it) }
                ?: flowOf(emptySet())
        }
    val savedTypes by savedTypesFlow.collectAsState(initial = emptySet())
    val channelUrl = video?.author?.url
    var isSubscribed by remember { mutableStateOf(false) }
    LaunchedEffect(channelUrl) {
        val url = channelUrl
        isSubscribed =
            if (url.isNullOrEmpty()) {
                false
            } else {
                withContext(Dispatchers.IO) { channelRepository.isSubscribed(url) }
            }
    }
    val currentVideoCard = video?.toCoreVideoCard()
    val onSubscribe: () -> Unit = {
        channelUrl?.let { url ->
            scope.launch { isSubscribed = channelRepository.toggleSubscription(url) }
        }
    }
    val onVideoLike: () -> Unit = {
        currentVideoCard?.let { card ->
            scope.launch { toggleSaveType(libraryRepository, savedTypes, card, SavedVideoType.LIKED) }
        }
    }
    val onVideoDislike: () -> Unit = {
        currentVideoCard?.let { card ->
            scope.launch {
                toggleSaveType(libraryRepository, savedTypes, card, SavedVideoType.DISLIKED)
            }
        }
    }

    val pagerState = rememberPagerState(pageCount = { pageKeys.size })
    // Settings can shrink the page list while the screen is up — snap back
    // to the first page instead of clamping mid-gesture.
    LaunchedEffect(pageKeys.size) {
        if (pageKeys.isNotEmpty() && pagerState.currentPage >= pageKeys.size) {
            pagerState.scrollToPage(0)
        }
    }

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
                        playbackQueueRepository = playbackQueueRepository,
                        scope = scope,
                        queue = queue,
                        currentVideoUrl = playerState.currentVideo?.url,
                    )
                }
            }
        },
        content = { padding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
            ) {
                if (pageKeys.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No pages enabled",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        when (pageKeys.getOrNull(page)) {
                            "video" -> {
                                CompanionVideoPage(
                                    playerState = playerState,
                                    video = video,
                                    comments = comments,
                                    isLive = isLive,
                                    liveChat = liveChat,
                                    recommendations = recommendations,
                                    selectedTab = selectedTab,
                                    onTabSelected = { selectedTab = it },
                                    onPlayPause = {
                                        // Publish to the player event bus so the main
                                        // screen shows the same feedback as its own actions.
                                        PlayerEventBus.emit(
                                            PlayerEvent.PlayPauseToggled(isPlaying = !playerState.isPlaying),
                                        )
                                        scope.launch {
                                            if (playerState.isPlaying) {
                                                playerRepository.pause()
                                            } else {
                                                playerRepository.resume()
                                            }
                                        }
                                    },
                                    onSeekBy = { deltaMs ->
                                        PlayerEventBus.emit(PlayerEvent.Seek(deltaMs))
                                        val target =
                                            (playerState.currentPositionMs + deltaMs)
                                                .coerceIn(0L, if (playerState.durationMs > 0) playerState.durationMs else Long.MAX_VALUE)
                                        scope.launch { playerRepository.seekTo(target) }
                                    },
                                    onPrevious = {
                                        val index = playerState.selectedIndex
                                        if (playerState.queue.isNotEmpty() && index > 0) {
                                            PlayerEventBus.emit(PlayerEvent.PreviousRequested)
                                            scope.launch { playerRepository.play(playerState.queue[index - 1].id) }
                                        }
                                    },
                                    onNext = {
                                        val index = playerState.selectedIndex
                                        if (playerState.queue.isNotEmpty() && index + 1 < playerState.queue.size) {
                                            PlayerEventBus.emit(PlayerEvent.NextRequested)
                                            scope.launch { playerRepository.play(playerState.queue[index + 1].id) }
                                        }
                                    },
                                    onSeekTo = { ms -> scope.launch { playerRepository.seekTo(ms) } },
                                    onPlay = onPlay,
                                    onLongClick = onLongClick,
                                    queue = queue,
                                    onQueuePlay = { index -> playbackQueueRepository.playAt(index) },
                                    onQueueRemove = { url -> playbackQueueRepository.remove(url) },
                                    onQueueMove = { from, to -> playbackQueueRepository.move(from, to) },
                                    videoTabKeys = videoTabKeys,
                                    pageOrder = pageOrder,
                                    savedTypes = savedTypes,
                                    isSubscribed = isSubscribed,
                                    onSubscribe = onSubscribe,
                                    onLike = onVideoLike,
                                    onDislike = onVideoDislike,
                                    onMore = { currentVideoCard?.let { optionsCard = it } },
                                    companionWindow = companionWindow,
                                    onChannelClick = onChannelClick,
                                )
                            }

                            "library" -> {
                                CompanionLibraryPage(
                                    sections = sections,
                                    slots = librarySlotValues,
                                    libraryRepository = libraryRepository,
                                    onPlay = onPlay,
                                    onLongClick = onLongClick,
                                )
                            }

                            "home" -> {
                                CompanionHomePage(
                                    items = dualHomeItems,
                                    onLoadNextPage = { scope.launch { homeRepository.loadNextPage() } },
                                    onPlay = onPlay,
                                    onLongClick = onLongClick,
                                )
                            }
                        }
                    }
                }
                if (scrimAlpha > 0.001f) {
                    Box(
                        modifier =
                            Modifier
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

/**
 * Second-screen Queue tab: the pending queue (tap = play, hold the dots to
 * reorder, X = remove). The same horizontal strip component as the Feed
 * queue card.
 */
@Composable
private fun CompanionQueueTabContent(
    queue: List<ContentItem>,
    current: ContentItem?,
    isPlaying: Boolean,
    onPlayItem: (Int) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onPlayPause: () -> Unit,
    onLongClick: (ContentItem) -> Unit,
) {
    // Same horizontal component as the Feed queue card on the main screen.
    Box(
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        QueueStripCard(
            queue = queue,
            current = current,
            isPlaying = isPlaying,
            onPlay = onPlayItem,
            onRemove = onRemove,
            onMove = onMove,
            onPlayPause = onPlayPause,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CompanionVideoPage(
    playerState: com.tsutsen.platformplayer.core.model.PlayerState,
    video: ContentItem?,
    comments: List<CommentItem>,
    isLive: Boolean = false,
    liveChat: com.tsutsen.platformplayer.core.model.LiveChatUiState? = null,
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
    queue: List<ContentItem>,
    onQueuePlay: (Int) -> Unit,
    onQueueRemove: (String) -> Unit,
    onQueueMove: (Int, Int) -> Unit,
    videoTabKeys: List<String> =
        listOf("info", "controls", "comments", "chapters", "recommended", "queue"),
    pageOrder: List<String> = listOf("controls", "video", "tabs"),
    savedTypes: Set<SavedVideoType> = emptySet(),
    isSubscribed: Boolean = false,
    onSubscribe: () -> Unit = {},
    onLike: () -> Unit = {},
    onDislike: () -> Unit = {},
    onMore: () -> Unit = {},
    companionWindow: Window?,
    onChannelClick: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (element in pageOrder) {
            when (element) {
                "controls" -> {
                    CompanionControlRow(
                        isPlaying = playerState.isPlaying,
                        onPlayPause = onPlayPause,
                        onSeekBy = onSeekBy,
                        onPrevious = onPrevious,
                        onNext = onNext,
                    )
                }

                "video" -> {
                    video?.let {
                        CompanionVideoHeader(
                            video = it,
                            positionMs = playerState.currentPositionMs,
                            durationMs = playerState.durationMs,
                        )
                    }
                }

                "tabs" -> {
                    if (video != null) {
                        VideoPageTabs(
                            video = video,
                            videoTabKeys = videoTabKeys,
                            selectedTab = selectedTab,
                            onTabSelected = onTabSelected,
                            comments = comments,
                            isLive = isLive,
                            liveChat = liveChat,
                            recommendations = recommendations,
                            queue = queue,
                            positionMs = playerState.currentPositionMs,
                            durationMs = playerState.durationMs,
                            chapters = playerState.chapters,
                            savedTypes = savedTypes,
                            isSubscribed = isSubscribed,
                            onSubscribe = onSubscribe,
                            onLike = onLike,
                            onDislike = onDislike,
                            onMore = onMore,
                            onSeekTo = onSeekTo,
                            onPlay = onPlay,
                            onLongClick = onLongClick,
                            onQueuePlay = onQueuePlay,
                            onQueueRemove = onQueueRemove,
                            onQueueMove = onQueueMove,
                            context = context,
                            currentVideo = video,
                            isPlaying = playerState.isPlaying,
                            onPlayPause = onPlayPause,
                            companionWindow = companionWindow,
                            // Tabs take the leftover height so any element
                            // order works — fillMaxSize here would swallow
                            // everything below when tabs isn't last.
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            onChannelClick = onChannelClick,
                        )
                    }
                }
            }
        }
        if (video == null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
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
 * The tab strip + its content area (the "tabs" element of the video page).
 * One scroll state per (enabled) tab — switching tabs never shares or
 * resets a strip's position.
 */
@Composable
private fun VideoPageTabs(
    video: ContentItem,
    videoTabKeys: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    comments: List<CommentItem>,
    isLive: Boolean,
    liveChat: com.tsutsen.platformplayer.core.model.LiveChatUiState?,
    recommendations: List<CoreVideoCard>,
    queue: List<ContentItem>,
    positionMs: Long,
    durationMs: Long,
    chapters: List<com.tsutsen.platformplayer.core.model.VideoChapter>,
    savedTypes: Set<SavedVideoType>,
    isSubscribed: Boolean,
    onSubscribe: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onMore: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlay: (String) -> Unit,
    onLongClick: (CoreVideoCard) -> Unit,
    onQueuePlay: (Int) -> Unit,
    onQueueRemove: (String) -> Unit,
    onQueueMove: (Int, Int) -> Unit,
    context: android.content.Context,
    currentVideo: ContentItem?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    companionWindow: Window?,
    modifier: Modifier = Modifier.fillMaxSize(),
    onChannelClick: (String) -> Unit,
) {
    val tabStates = remember(videoTabKeys) { videoTabKeys.map { LazyListState() } }
    val activeTab = videoTabKeys.getOrNull(selectedTab)
    val currentChapterIndex =
        chapters.indexOfLast { it.startTimeMs <= positionMs }
    // Follow the playhead: while on the chapters tab, a chapter
    // change scrolls the strip to the newly active chapter.
    LaunchedEffect(currentChapterIndex) {
        if (activeTab == "chapters" && currentChapterIndex >= 0) {
            tabStates.getOrNull(selectedTab)?.animateScrollToItem(currentChapterIndex)
        }
    }
    Column(
        modifier = modifier,
        // The old layout spaced these 12dp apart; keep the gap below the
        // tab strip so the content doesn't hug it.
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PillTabs(
            labels =
                videoTabKeys.map {
                    if (isLive && it == "comments") "Live chat" else it.companionTabLabel()
                },
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
                if (activeTab == null) {
                    // No tabs enabled — nothing to show in the strip.
                    Unit
                } else if (activeTab == "info") {
                    // Info tab: the main player's channel row (icon-only
                    // subscribe) + a scrollable description card.
                    CompanionInfoTab(
                        video = video,
                        savedTypes = savedTypes,
                        isSubscribed = isSubscribed,
                        onSubscribe = onSubscribe,
                        onLike = onLike,
                        onDislike = onDislike,
                        onMore = onMore,
                        durationMs = durationMs,
                        onSeekTo = onSeekTo,
                        onChannelClick = onChannelClick,
                    )
                } else if (activeTab == "controls") {
                    // Controls tab: playback, volume and brightness
                    // sliders.
                    CompanionControlsTab(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isLive = isLive,
                        onSeekTo = onSeekTo,
                        companionWindow = companionWindow,
                    )
                } else if (activeTab == "queue") {
                    // Queue tab: the playing video pinned on top, then
                    // the pending queue (tap = play, drag = reorder,
                    // X = remove). The same horizontal strip as the
                    // tabs' horizontal strips.
                    CompanionQueueTabContent(
                        queue = queue,
                        current = currentVideo,
                        isPlaying = isPlaying,
                        onPlayItem = onQueuePlay,
                        onRemove = onQueueRemove,
                        onMove = onQueueMove,
                        onPlayPause = onPlayPause,
                        onLongClick = { item ->
                            onLongClick(
                                CoreVideoCard(
                                    id = item.id,
                                    title = item.title,
                                    thumbnailUrl = item.thumbnailUrl,
                                    author = item.author?.name,
                                    authorUrl = item.author?.url,
                                    durationMs = item.durationMs,
                                    viewCount = item.viewCount,
                                    publishedAt = item.publishedAt,
                                    url = item.url,
                                ),
                            )
                        },
                    )
                } else if (activeTab == "dot") {
                    // The distraction-free tab: just the video.
                    Unit
                } else if (activeTab == "comments" && isLive) {
                    // Live stream: the comments slot becomes the live
                    // chat, same as the main player. Vertical, fills the
                    // tab area.
                    com.tsutsen.platformplayer.feature.player.impl.ui.components
                        .LiveChatPanel(
                            state = liveChat,
                            modifier = Modifier.fillMaxSize(),
                            listHeight = null,
                            // No horizontal inset on the companion
                            // (bottom screen) chat.
                            horizontalPadding = 0.dp,
                        )
                } else if (activeTab == "comments" && comments.isEmpty()) {
                    // Centre the empty state in the whole tab area — a
                    // LazyRow item can't fill the (unbounded) row width.
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No comments",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp),
                        )
                    }
                } else if (activeTab == "chapters" && chapters.isEmpty()) {
                    // Centre the empty state in the whole tab area — a
                    // LazyRow item can't fill the (unbounded) row width.
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No chapters",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyRow(
                        state = tabStates[selectedTab],
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 8.dp),
                    ) {
                        if (activeTab == "comments") {
                            items(comments, key = { it.id }) { comment ->
                                CommentCardView(
                                    comment = comment,
                                    onTimestampClick = { ms ->
                                        val dur = durationMs
                                        val target =
                                            if (dur > 0) ms.coerceIn(0, dur - 500) else ms.coerceAtLeast(0)
                                        onSeekTo(target)
                                    },
                                    onLinkClick = { url ->
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                            )
                                        }
                                    },
                                )
                            }
                        } else if (activeTab == "chapters") {
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
                                        if (isCurrent) {
                                            scheme.primaryContainer
                                        } else {
                                            scheme.surfaceContainer
                                        },
                                    animationSpec = tween(300),
                                    label = "chapterBg",
                                )
                                val cardFg by animateColorAsState(
                                    targetValue =
                                        if (isCurrent) {
                                            scheme.onPrimaryContainer
                                        } else {
                                            scheme.onSurface
                                        },
                                    animationSpec = tween(300),
                                    label = "chapterFg",
                                )
                                Card(
                                    modifier =
                                        Modifier
                                            .width(240.dp)
                                            .fillMaxHeight()
                                            .combinedClickable(onClick = { onSeekTo(chapter.startTimeMs) }),
                                    shape = RoundedCornerShape(BluejayTokens().radius.md),
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
                                    modifier =
                                        Modifier
                                            .width(240.dp)
                                            .padding(horizontal = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Page 1: the library as four corner slots (2x2).
 * Each slot is a section
 * header + a horizontal pager of that section's cards — swipe left/right
 * inside a slot to page through its list.
 */
@Composable
private fun CompanionLibraryPage(
    sections: List<LibrarySection>,
    slots: List<String> =
        listOf("watch_later", "liked", "favourite", "history"),
    libraryRepository: LibraryRepository,
    onPlay: (String) -> Unit,
    onLongClick: (CoreVideoCard) -> Unit,
) {
    Column(
        modifier =
            Modifier
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
        // Four 2x2 slots (order matters). Each is a section id or a
        // "playlist:<id>" reference (Settings > Dual screen > Library slots).
        val slotValues = (0 until 4).map { slots.getOrNull(it) }
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LibrarySlotCell(
                value = slotValues.getOrNull(0),
                sections = sections,
                libraryRepository = libraryRepository,
                onPlay = onPlay,
                onLongClick = onLongClick,
                modifier = Modifier.weight(1f),
            )
            LibrarySlotCell(
                value = slotValues.getOrNull(1),
                sections = sections,
                libraryRepository = libraryRepository,
                onPlay = onPlay,
                onLongClick = onLongClick,
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LibrarySlotCell(
                value = slotValues.getOrNull(2),
                sections = sections,
                libraryRepository = libraryRepository,
                onPlay = onPlay,
                onLongClick = onLongClick,
                modifier = Modifier.weight(1f),
            )
            LibrarySlotCell(
                value = slotValues.getOrNull(3),
                sections = sections,
                libraryRepository = libraryRepository,
                onPlay = onPlay,
                onLongClick = onLongClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One 2x2 corner: a section or playlist slot, blank when unset. */
@Composable
private fun LibrarySlotCell(
    value: String?,
    sections: List<LibrarySection>,
    libraryRepository: LibraryRepository,
    onPlay: (String) -> Unit,
    onLongClick: (CoreVideoCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (value == null) {
        Box(modifier = modifier)
        return
    }
    if (value.startsWith("playlist:")) {
        PlaylistSlotPager(
            playlistId = value.substringAfter(":"),
            libraryRepository = libraryRepository,
            onPlay = onPlay,
            onLongClick = onLongClick,
            modifier = modifier,
        )
        return
    }
    val section = sections.firstOrNull { it.id == value }
    LibrarySlotPager(
        title = section?.title ?: "Empty",
        cards = section?.items.orEmpty(),
        totalCount = section?.totalCount ?: 0,
        onPlay = onPlay,
        onLongClick = onLongClick,
        modifier = modifier,
    )
}

/** A playlist-backed 2x2 slot. Collects the live playlist cards. */
@Composable
private fun PlaylistSlotPager(
    playlistId: String,
    libraryRepository: LibraryRepository,
    onPlay: (String) -> Unit,
    onLongClick: (CoreVideoCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val id = playlistId.toLongOrNull()
    if (id == null) {
        Box(modifier = modifier)
        return
    }
    val pair by libraryRepository
        .observeLocalPlaylist(id)
        .collectAsState(initial = null)
    LibrarySlotPager(
        title = pair?.first?.name ?: "Playlist",
        cards = pair?.second.orEmpty(),
        totalCount = pair?.second?.size ?: 0,
        onPlay = onPlay,
        onLongClick = onLongClick,
        modifier = modifier,
    )
}

private fun String.companionTabLabel(): String =
    when (this) {
        "info" -> "Info"
        "controls" -> "Controls"
        "comments" -> "Comments"
        "chapters" -> "Chapters"
        "recommended" -> "Recommended"
        "dot" -> "Blank"
        else -> "Queue"
    }

/**
 * One corner slot: section header (with current/total page position) + a
 * horizontal pager. Pages are slightly narrower than the slot so the next
 * item peeks in, and edge fades let cards dissolve at the slot's edges.
 */
@Composable
private fun LibrarySlotPager(
    title: String,
    cards: List<com.tsutsen.platformplayer.core.model.Card>,
    totalCount: Int,
    onPlay: (String) -> Unit,
    onLongClick: (CoreVideoCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    // 1-based index of the page currently in the centre.
    val currentPage = remember(title) { mutableIntStateOf(1) }
    Card(
        modifier = modifier.padding(4.dp),
        shape = RoundedCornerShape(BluejayTokens().radius.md),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val total = totalCount
                if (total > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${currentPage.value.coerceIn(1, total)}/$total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (cards.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(BluejayTokens().radius.md))
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
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    // key() recreates the pager (and its state) when the list
                    // identity changes (e.g. empty -> loaded).
                    key(cards) {
                        val state = rememberPagerState(pageCount = { cards.size })
                        LaunchedEffect(state) {
                            snapshotFlow { state.currentPage }
                                .distinctUntilChanged()
                                .collect { currentPage.value = it + 1 }
                        }
                        HorizontalPager(
                            state = state,
                            modifier = Modifier.fillMaxSize(),
                            pageSpacing = 8.dp,
                            // Fixed page width narrower than the viewport:
                            // pages centre themselves, so ~20dp of each
                            // neighbour peeks in at the edges.
                            pageSize = PageSize.Fixed(maxWidth - 40.dp),
                        ) { index ->
                            PagerCard(
                                card = cards[index],
                                onClick = onPlay,
                                onLongClick = onLongClick,
                            )
                        }
                    }
                    // Edge fades: the slot's card colour → transparent, so
                    // peeked neighbours dissolve into the tile's edges.
                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .width(36.dp)
                                .align(Alignment.CenterStart)
                                .background(
                                    Brush.horizontalGradient(
                                        colors =
                                            listOf(
                                                cardColor,
                                                cardColor.copy(alpha = 0f),
                                            ),
                                    ),
                                ),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .width(36.dp)
                                .align(Alignment.CenterEnd)
                                .background(
                                    Brush.horizontalGradient(
                                        colors =
                                            listOf(
                                                cardColor.copy(alpha = 0f),
                                                cardColor,
                                            ),
                                    ),
                                ),
                    )
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
        modifier =
            Modifier
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
                val state =
                    rememberPagerState(
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
                        pageItems =
                            feedItems.value
                                .chunked(4)
                                .getOrNull(pageIndex)
                                .orEmpty(),
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
    // The whole 2x2 page lives in one card so it reads as a single element,
    // not four separate cells.
    Card(
        modifier = Modifier.fillMaxSize().padding(4.dp),
        shape = RoundedCornerShape(BluejayTokens().radius.md),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
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
        onDownloadWithQuality = { quality ->
            scope.launch {
                downloading = true
                downloadsRepository.startDownload(card.url, quality)
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

private fun CoreVideoCard.toContentItem() =
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
    ) {
        val tileModifier: Modifier = Modifier.weight(1f).height(80.dp)
        OptionTileView(
            OptionTile(label = "Previous", icon = Icons.Filled.SkipPrevious, onClick = onPrevious),
            modifier = tileModifier,
            showLabel = false,
            outerHPadding = 0.dp,
        )
        // Seek icons without the baked-in "10" digit — plain rewind/ffwd.
        OptionTileView(
            OptionTile(label = "Back 10s", icon = Icons.Filled.FastRewind, onClick = { onSeekBy(-10_000L) }),
            modifier = tileModifier,
            showLabel = false,
            outerHPadding = 0.dp,
        )
        OptionTileView(
            OptionTile(
                label = if (isPlaying) "Pause" else "Play",
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                onClick = onPlayPause,
            ),
            modifier = tileModifier,
            showLabel = false,
            outerHPadding = 0.dp,
        )
        OptionTileView(
            OptionTile(label = "Fwd 10s", icon = Icons.Filled.FastForward, onClick = { onSeekBy(10_000L) }),
            modifier = tileModifier,
            showLabel = false,
            outerHPadding = 0.dp,
        )
        OptionTileView(
            OptionTile(label = "Next", icon = Icons.Filled.SkipNext, onClick = onNext),
            modifier = tileModifier,
            showLabel = false,
            outerHPadding = 0.dp,
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
private fun CompanionInfoTab(
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
private fun CompanionControlsTab(
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
private fun CompanionSliderRow(
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
private fun hostActivity(context: Context): Activity? {
    var c: Context = context
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
/** The current video as an options-sheet card (three-dot menu / long-press). */
private fun ContentItem.toCoreVideoCard(): CoreVideoCard =
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
