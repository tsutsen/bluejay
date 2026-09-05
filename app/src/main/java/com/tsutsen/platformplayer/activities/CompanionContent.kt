package com.tsutsen.platformplayer.activities

import android.app.Presentation
import android.content.Context
import android.view.Window
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.data.repository.ChannelRepository
import com.tsutsen.platformplayer.core.data.repository.HomeRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.ContentType
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.toContentItem
import com.tsutsen.platformplayer.feature.player.impl.HistoryTracker
import com.tsutsen.platformplayer.feature.player.impl.PlayerEvent
import com.tsutsen.platformplayer.feature.player.impl.PlayerEventBus
import com.tsutsen.platformplayer.feature.player.impl.SystemControls
import com.tsutsen.platformplayer.stats.WatchStats
import com.tsutsen.platformplayer.stats.WatchStatsBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tsutsen.platformplayer.core.model.VideoCard as CoreVideoCard
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


/**
 * The whole second screen: a vertical pager with three fixed pages.
 * Vertical flick changes pages; horizontal swipes scroll the strips inside
 * each page. No vertical scrolling anywhere.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun CompanionContent(
    playerRepository: PlayerRepository,
    libraryRepository: LibraryRepository,
    homeRepository: HomeRepository,
    downloadsRepository: com.tsutsen.platformplayer.core.data.repository.DownloadsRepository,
    playbackQueueRepository: com.tsutsen.platformplayer.core.data.repository.PlaybackQueueRepository,
    settingsRepository: SettingsRepository,
    liveChatRepository: com.tsutsen.platformplayer.core.data.repository.LiveChatRepository,
    channelRepository: ChannelRepository,
    historyTracker: HistoryTracker,
    subscriptionDao: com.tsutsen.platformplayer.core.database.dao.SubscriptionDao,
    companionWindow: Window?,
    onChannelClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onWatchStats: () -> Unit,
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
    val prefsState by settingsRepository.preferences.collectAsState(initial = null)
    val prefs = prefsState ?: return
    // Saved order wins (Settings > Dual screen > Pages); unknown keys are
    // dropped, missing pages are simply absent.
    val pageKeys = prefs.dualScreenPages.filter { it in setOf("video", "library", "home", "dash") }
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
    // Dash page: widget order + the "Top creators" scope (Settings > Dual
    // screen > Dash page).
    val dashPageOrder =
        (prefs.dualScreenDashPageOrder + listOf("stats", "top_creators", "continue")).distinct()
    val topCreatorsScope = prefs.dualScreenTopCreatorsScope
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

    val onLongClick: (CoreVideoCard) -> Unit = { card -> optionsCard = card }

    // Dash page: live history (the Room flow the player's HistoryTracker
    // writes to) and the stats derived from it — the same pipeline as the
    // Dash tab card on the main screen. Subscriptions supply the channel
    // avatars (history rows only store video thumbnails).
    val history by historyTracker.observeHistory().collectAsState(initial = emptyList())
    val subscriptions by subscriptionDao.observeAll().collectAsState(initial = emptyList())
    var watchStats by remember { mutableStateOf(WatchStats()) }
    LaunchedEffect(history, subscriptions) {
        watchStats =
            withContext(Dispatchers.IO) {
                WatchStatsBuilder.build(
                    history,
                    channelAvatars = subscriptions.associate { it.channelId to it.thumbnailUrl },
                )
            }
    }
    // A URL-only play (library slots, etc.) enriches from history when the
    // entry is known, so the player UI shows real details immediately
    // instead of the "Loading..." placeholder.
    val onPlay: (String) -> Unit = { url ->
        scope.launch {
            val entry = history.firstOrNull { it.contentUrl == url }
            playerRepository.play(url, entry?.toContentItem())
        }
    }
    // A play with full details (continue widget, recommendations, options
    // sheet) — the same path the main screen's card taps take.
    val onPlayItem: (ContentItem) -> Unit = { item ->
        scope.launch { playerRepository.play(item.url, item) }
    }
    // Dash page: the continue widget's discarded entries (local dismiss —
    // history itself is untouched), remembered across relaunches.
    val context = LocalContext.current
    val companionPrefs =
        remember { context.getSharedPreferences("bluejay_companion", Context.MODE_PRIVATE) }
    var discardedContinue by remember {
        mutableStateOf(
            companionPrefs.getStringSet("discarded_continue_urls", emptySet())?.toSet()
                ?: emptySet()
        )
    }

    fun discardFromContinue(url: String) {
        val updated = discardedContinue + url
        discardedContinue = updated
        // ponytail: unbounded URL set — grows by one string per dismiss;
        // prune or cap if it ever gets large.
        companionPrefs.edit().putStringSet("discarded_continue_urls", updated).apply()
    }

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

    // The dash page is the landing page; when a video opens in the player
    // the pager flips to the video page.
    val videoPageIndex = pageKeys.indexOf("video")
    LaunchedEffect(video?.url) {
        if (video == null) return@LaunchedEffect
        if (videoPageIndex in pageKeys.indices && videoPageIndex != pagerState.currentPage) {
            pagerState.animateScrollToPage(videoPageIndex)
        }
    }
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
                        onPlayItem = onPlayItem,
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
                                    onPlayItem = onPlayItem,
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
                                    onPlaylistClick = onPlaylistClick,
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

                            "dash" -> {
                                CompanionDashPage(
                                    stats = watchStats,
                                    history = history,
                                    currentVideoUrl = video?.url,
                                    isPlaying = playerState.isPlaying,
                                    pageOrder = dashPageOrder,
                                    topCreatorsScope = topCreatorsScope,
                                    onPlay = onPlay,
                                    onPlayItem = onPlayItem,
                                    discarded = discardedContinue,
                                    onDiscard = { url ->
                                        discardFromContinue(url)
                                    },
                                    onStatsClick = onWatchStats,
                                    onChannelClick = onChannelClick,
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

