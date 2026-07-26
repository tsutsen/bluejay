package com.tsutsen.platformplayer.feature.player.impl

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.tsutsen.platformplayer.core.designsystem.component.VideoCardSkeleton
import com.tsutsen.platformplayer.core.model.Author
import com.tsutsen.platformplayer.core.model.CommentItem
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.feature.player.impl.CommentCard
import com.tsutsen.platformplayer.feature.player.impl.formatRelativeTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "PlayerScreen"

/**
 * Video player overlay composable.
 * Rendered at the activity level on top of whatever screen is behind it.
 * No navigation needed — player just appears/disappears based on PlayerState.
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    Log.d(TAG, "PlayerScreen COMPOSE created (overlay mode)")
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    var showTopOverlay by remember { mutableStateOf(true) }
    var showBottomOverlay by remember { mutableStateOf(true) }
    var showOptionsModal by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var brightnessValue by remember { mutableStateOf(1.0f) }
    var volumeValue by remember { mutableStateOf(1.0f) }
    var selectedSpeed by remember { mutableStateOf(1.0f) }
    var selectedQuality by remember { mutableStateOf("Auto") }
    var showMiniPlayerOptions by remember { mutableStateOf(false) }
    var touchX by remember { mutableStateOf(0f) }
    
    // Detail page state
    var expandedDescription by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Comments, 1 = Recommended
    var isLooping by remember { mutableStateOf(false) }
    
    // Mini player drag state
    var miniPlayerOffsetX by remember { mutableStateOf(0f) }
    var miniPlayerOffsetY by remember { mutableStateOf(0f) }
    var isDraggingMiniPlayer by remember { mutableStateOf(false) }
    
    // Animated offset for snap animation - faster, snappier
    val animatedMiniOffsetX by animateFloatAsState(
        targetValue = miniPlayerOffsetX,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "animatedMiniOffsetX"
    )
    val animatedMiniOffsetY by animateFloatAsState(
        targetValue = miniPlayerOffsetY,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "animatedMiniOffsetY"
    )

    // Smoother spring animations - more damping for less bounce, lower stiffness for smoother feel
    val transitionSpringSpec = spring<Float>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy
    )
    val transitionDpSpec = spring<Dp>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy
    )

    // ==================== Animation State (persists outside when block) ====================
    val isMinimizedAnim = remember { mutableStateOf(false) }
    val isFullscreenAnim = remember { mutableStateOf(false) }

    var containerSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    // ==================== ExoPlayer (from repository) ====================
    // Get ExoPlayer from repository - PlayerRepository manages the player lifecycle
    val player = remember(uiState) {
        (viewModel as? PlayerViewModel)?.getPlayer()?.exoPlayer
    }

    LaunchedEffect(uiState) {
        if (uiState is PlayerUiState.Loaded) {
            val state = uiState as PlayerUiState.Loaded
            brightnessValue = state.brightness
            volumeValue = state.volume
        }
    }

    // Auto-hide overlays when playing
    LaunchedEffect(showTopOverlay, showBottomOverlay) {
        if (showTopOverlay && showBottomOverlay) {
            // Wait until the player is actually playing
            var currentState: PlayerUiState.Loaded? = uiState as? PlayerUiState.Loaded
            while (currentState == null || !currentState.isPlaying) {
                delay(100)
                currentState = uiState as? PlayerUiState.Loaded
            }
            delay(3000)
            currentState = uiState as? PlayerUiState.Loaded
            if (currentState != null && currentState.isPlaying) {
                showTopOverlay = false
                showBottomOverlay = false
            }
        }
    }

    // Sync animation state with actual state
    val isMinimizedState = (uiState as? PlayerUiState.Loaded)?.isMinimized
    val isFullscreenState = (uiState as? PlayerUiState.Loaded)?.isFullscreen

    LaunchedEffect(isMinimizedState, isFullscreenState) {
        val minimized = isMinimizedState ?: return@LaunchedEffect
        val fullscreen = isFullscreenState ?: return@LaunchedEffect
        isMinimizedAnim.value = minimized
        isFullscreenAnim.value = fullscreen
        // Show overlays when exiting mini player
        if (!minimized) {
            showTopOverlay = true
            showBottomOverlay = true
        }
        Log.d(TAG, "Animation state synced: isMinimized=$minimized, isFullscreen=$fullscreen")
    }

    when (val state = uiState) {
        is PlayerUiState.Initial -> {
            // No player active — don't show anything
        }
        is PlayerUiState.Loading -> {
            // Show loading spinner while video is being resolved and loaded
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading video...",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        is PlayerUiState.Loaded -> {
            val isTablet = configuration.smallestScreenWidthDp >= 600
            val miniPlayerScale = if (isTablet) 0.35f else 0.45f

            // Log when player enters Loaded state
            Log.d(TAG, "PlayerScreen entered Loaded state")
            Log.d(TAG, "Current video: ${state.currentVideo?.url}")
            Log.d(TAG, "Is playing: ${state.isPlaying}")
            Log.d(TAG, "Is loading: ${state.isLoading}")
            Log.d(TAG, "Is minimized: ${state.isMinimized}")
            Log.d(TAG, "Is fullscreen: ${state.isFullscreen}")

            // Note: Video loading is handled by PlayerRepositoryImpl
            // PlayerScreen only displays the video using the ExoPlayer from the repository

            // Calculate mini-player size - smaller, 16:9 aspect ratio
            val miniWidth = 280.dp
            val miniHeight = miniWidth * 9f / 16f

            val isMinimized = state.isMinimized
            val isFullscreen = state.isFullscreen

            // ==================== Animated Values ====================
            val miniScaleTarget = if (isTablet) 0.35f else 0.45f
            val scale by animateFloatAsState(
                targetValue = if (isMinimizedAnim.value) miniScaleTarget else 1.0f,
                animationSpec = transitionSpringSpec,
                label = "miniScale"
            )
            val cornerRadius by animateDpAsState(
                targetValue = if (isMinimizedAnim.value) 12.dp else 0.dp,
                animationSpec = transitionDpSpec,
                label = "cornerRadius"
            )
            val shadowElevationDp by animateDpAsState(
                targetValue = if (isMinimizedAnim.value) 8.dp else 0.dp,
                animationSpec = transitionDpSpec,
                label = "shadowElevation"
            )
            // No scrim when minimized - let user see content behind
            val scrimAlpha by animateFloatAsState(
                targetValue = 0f,
                animationSpec = transitionSpringSpec,
                label = "scrimAlpha"
            )
            val translationX by animateFloatAsState(
                targetValue = if (isMinimizedAnim.value) containerSize.width * 0.85f else 0f,
                animationSpec = transitionSpringSpec,
                label = "translationX"
            )
            val translationY by animateFloatAsState(
                targetValue = if (isMinimizedAnim.value) containerSize.height * 0.8f else 0f,
                animationSpec = transitionSpringSpec,
                label = "translationY"
            )

            // Fullscreen transition: animate corner radius and shadow only
            val fullscreenCornerRadius by animateDpAsState(
                targetValue = if (isFullscreenAnim.value) 0.dp else if (isMinimizedAnim.value) 12.dp else 0.dp,
                animationSpec = transitionDpSpec,
                label = "fullscreenCornerRadius"
            )
            val fullscreenShadowElevation by animateDpAsState(
                targetValue = if (isFullscreenAnim.value) 0.dp else if (isMinimizedAnim.value) 8.dp else 0.dp,
                animationSpec = transitionDpSpec,
                label = "fullscreenShadowElevation"
            )

            // Fullscreen scrim alpha - subtle for better visibility
            val fullscreenScrimAlpha by animateFloatAsState(
                targetValue = if (isFullscreenAnim.value) 0.3f else 0f,
                animationSpec = transitionSpringSpec,
                label = "fullscreenScrimAlpha"
            )

            // ==================== Orientation & System UI ====================
            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val isSmallWindow = with(context.resources.displayMetrics) {
                kotlin.math.min(widthPixels, heightPixels) < 600
            }

            // Auto-fullscreen on landscape for phones
            LaunchedEffect(isLandscape, isSmallWindow, isFullscreen) {
                if (isLandscape && isSmallWindow && !isFullscreen && !isMinimized) {
                    Log.d(TAG, "Auto-entering fullscreen: landscape + phone")
                    viewModel.toggleFullscreen()
                } else if (!isLandscape && isFullscreen) {
                    Log.d(TAG, "Exiting fullscreen: portrait")
                    viewModel.exitFullscreen()
                }
            }

            // Enable edge-to-edge so window size never changes when bars hide/show
            LaunchedEffect(Unit) {
                val activity = context as? Activity
                if (activity != null) {
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    Log.d(TAG, "Edge-to-edge enabled for PlayerScreen")
                }
            }

            // System UI handling in fullscreen
            LaunchedEffect(isFullscreen, isSmallWindow) {
                if (isFullscreen) {
                    val activity = context as? Activity
                    if (activity != null) {
                        activity.window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        )
                        if (isSmallWindow) {
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                        Log.d(TAG, "System UI hidden for fullscreen")
                    }
                } else {
                    val activity = context as? Activity
                    if (activity != null) {
                        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                        if (isSmallWindow) {
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                        Log.d(TAG, "System UI restored")
                    }
                }
            }

            // ==================== Auto-hide controls in fullscreen ====================
            var controlsVisible by remember { mutableStateOf(true) }
            var hideControlsJob by remember { mutableStateOf<Job?>(null) }

            LaunchedEffect(isFullscreen, controlsVisible) {
                if (isFullscreen && controlsVisible) {
                    hideControlsJob = launch {
                        delay(3000)
                        controlsVisible = false
                    }
                } else {
                    hideControlsJob?.cancel()
                    if (!isFullscreen) controlsVisible = true
                }
            }

            // ==================== Collapsing player height (shared by video content AND controls overlay) ====================
            val scrollState = rememberLazyListState()
            val maxPlayerHeightPx = containerSize.height * 0.6f
            val minPlayerHeightPx = containerSize.height * 0.2f

            // Height is driven directly by raw scroll deltas via NestedScrollConnection rather
            // than derived from scrollState.firstVisibleItemScrollOffset - that value resets to 0
            // every time the list crosses an item boundary, which caused the player to snap back
            // to full height and then shrink again on every boundary crossing. This single height
            // value is shared by the video Box below AND the controls overlay container further
            // down (top/bottom bars, gesture layer) so they always stay in sync with each other.
            var playerHeightPx by remember { mutableStateOf(maxPlayerHeightPx) }

            // Keep the current height valid if container size changes (e.g. rotation)
            LaunchedEffect(maxPlayerHeightPx, minPlayerHeightPx) {
                playerHeightPx = playerHeightPx.coerceIn(minPlayerHeightPx, maxPlayerHeightPx)
            }

            val nestedScrollConnection = remember(minPlayerHeightPx, maxPlayerHeightPx) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        val delta = available.y
                        val previousHeight = playerHeightPx
                        val consumed = when {
                            // Scrolling content up (finger moving up) - shrink the player first,
                            // let any leftover delta fall through to the list.
                            delta < 0f -> {
                                val newHeight = (previousHeight + delta)
                                    .coerceIn(minPlayerHeightPx, maxPlayerHeightPx)
                                newHeight - previousHeight
                            }
                            // Pulling down while already at the very top of the list - expand
                            // the player instead of doing nothing.
                            delta > 0f &&
                                scrollState.firstVisibleItemIndex == 0 &&
                                scrollState.firstVisibleItemScrollOffset == 0 -> {
                                val newHeight = (previousHeight + delta)
                                    .coerceIn(minPlayerHeightPx, maxPlayerHeightPx)
                                newHeight - previousHeight
                            }
                            else -> 0f
                        }
                        if (consumed != 0f) {
                            playerHeightPx += consumed
                        }
                        return Offset(0f, consumed)
                    }
                }
            }

            // ==================== Main Layout ====================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        containerSize = androidx.compose.ui.geometry.Size(
                            coordinates.size.width.toFloat(),
                            coordinates.size.height.toFloat()
                        )
                    }
            ) {
                // Scrim background when fullscreen (subtle)
                if (isFullscreenAnim.value) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = fullscreenScrimAlpha))
                    )
                }

                // ==================== Video Player ====================
                if (isFullscreenAnim.value) {
                    // Fullscreen: player takes entire screen
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                setControllerAutoShow(false)
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { view -> view.player = player },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (!isMinimizedAnim.value) {
                    // Detail page: player height shrinks as user scrolls, expands when scrolling to top
                    val playerHeight = playerHeightPx

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Player takes dynamic height based on scroll
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(with(LocalDensity.current) { playerHeight.toDp() })
                                .background(Color.Black)
                                .clipToBounds()
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        useController = false
                                        setControllerAutoShow(false)
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                },
                                update = { view -> view.player = player },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Scrollable details below player
                        LazyColumn(
                            state = scrollState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .nestedScroll(nestedScrollConnection)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            // Row 1: Title
                            item {
                                Text(
                                    text = state.currentVideo?.title ?: "Unknown",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }

                            // Row 2: Channel info + subscribe (left) / watch later, share, more (right)
                            item {
                                ChannelRow(
                                    author = state.currentVideo?.author,
                                    onSubscribe = { /* TODO */ },
                                    onWatchLater = { /* TODO */ },
                                    onShare = { /* TODO */ },
                                    onMore = { /* TODO */ }
                                )
                            }

                            // Row 3: Likes/dislikes, views, date posted
                            item {
                                VideoStatsRow(
                                    viewCount = state.currentVideo?.viewCount ?: 0,
                                    publishedAt = state.currentVideo?.publishedAt,
                                    likeCount = state.currentVideo?.likeCount,
                                    dislikeCount = state.currentVideo?.dislikeCount
                                )
                            }

                            // Row 4: Description (collapsed to 3 lines, expands on tap)
                            item {
                                DescriptionSection(
                                    description = state.currentVideo?.description ?: "",
                                    isExpanded = expandedDescription,
                                    onToggle = { expandedDescription = !expandedDescription }
                                )
                            }

                            // Tabs
                            item {
                                TabsSection(
                                    selectedTab = selectedTab,
                                    onTabSelected = { selectedTab = it }
                                )
                            }

                            // Tab Content
                            when (selectedTab) {
                                0 -> {
                                    // Render comments directly as items in the parent LazyColumn
                                    items(state.comments.size) { index ->
                                        val comment = state.comments[index]
                                        CommentCard(
                                            username = comment.author,
                                            timeAgo = formatRelativeTime(comment.publishedAtMs),
                                            text = comment.text,
                                            likeCount = comment.likeCount.toInt()
                                        )
                                        if (index < state.comments.lastIndex) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                    }
                                    
                                    // Load more button
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            TextButton(onClick = { viewModel.loadMoreComments(state.currentVideo?.url ?: "") }) {
                                                Text("Load more comments")
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    item {
                                        RecommendedSection()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Mini player is handled separately
                }

                // ==================== Mini-player controls overlay ====================
                if (isMinimizedAnim.value) {
                    Box(
                        modifier = Modifier
                            .size(miniWidth, miniHeight)
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .offset {
                                IntOffset(
                                    x = animatedMiniOffsetX.toInt(),
                                    y = animatedMiniOffsetY.toInt()
                                )
                            }
                            .graphicsLayer {
                                shape = RoundedCornerShape(cornerRadius)
                                clip = true
                            }
                            .pointerInput(isDraggingMiniPlayer) {
                                var isDragging = false
                                var dragStartX = 0f
                                var dragStartY = 0f
                                
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        isDragging = true
                                        dragStartX = offset.x
                                        dragStartY = offset.y
                                    },
                                    onDrag = { change, dragAmount: Offset ->
                                        change.consume()
                                        miniPlayerOffsetX += dragAmount.x
                                        miniPlayerOffsetY += dragAmount.y
                                    },
                                    onDragEnd = {
                                        isDraggingMiniPlayer = false
                                        isDragging = false
                                        // Snap to nearest edge or keep position
                                        val screenWidth = containerSize.width
                                        val screenHeight = containerSize.height
                                        val miniWidthPx = miniWidth.toPx()
                                        val miniHeightPx = miniHeight.toPx()
                                        val paddingPx = 16.dp.toPx()
                                        val edgeThreshold = 100f
                                        
                                        // Calculate actual position (initial position + offset)
                                        val initialX = screenWidth - miniWidthPx - paddingPx
                                        val initialY = screenHeight - miniHeightPx - paddingPx
                                        val actualX = initialX + miniPlayerOffsetX
                                        val actualY = initialY + miniPlayerOffsetY
                                        
                                        // Snap X to left or right edge based on actual position
                                        if (actualX < edgeThreshold) {
                                            // Near left edge: snap to left (Box at x=0, content at x=padding due to padding modifier)
                                            miniPlayerOffsetX = -initialX
                                        } else if (actualX > screenWidth - miniWidthPx - edgeThreshold) {
                                            // Near right edge: snap to right (offset = 0)
                                            miniPlayerOffsetX = 0f
                                        }
                                        
                                        // Snap Y to top or bottom edge based on actual position
                                        if (actualY < edgeThreshold) {
                                            // Near top edge: snap to top (Box at y=0, content at y=padding due to padding modifier)
                                            miniPlayerOffsetY = -initialY
                                        } else if (actualY > screenHeight - miniHeightPx - edgeThreshold) {
                                            // Near bottom edge: snap to bottom (offset = 0)
                                            miniPlayerOffsetY = 0f
                                        }
                                    },
                                    onDragCancel = {
                                        isDraggingMiniPlayer = false
                                        isDragging = false
                                    }
                                )
                            }
                    ) {
                        // Background Box with tap gesture to expand mini player
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .pointerInput(isDraggingMiniPlayer) {
                                    if (!isDraggingMiniPlayer) {
                                        detectTapGestures(
                                            onTap = {
                                                Log.d(TAG, "Tap background: expand mini player")
                                                viewModel.exitMiniPlayer()
                                            }
                                        )
                                    }
                                }
                        )
                        // Column with buttons (buttons are not affected by background tap)
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top row: Play/Pause (left) and Close (right)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (state.isPlaying) viewModel.pause() else viewModel.resume()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        Log.d(TAG, "Close mini player: close")
                                        viewModel.close()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            // Bottom row: Title and actions
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.currentVideo?.title ?: "Unknown",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (state.currentVideo?.author?.name?.isNotEmpty() == true) {
                                        Text(
                                            text = state.currentVideo!!.author!!.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showMiniPlayerOptions = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        Log.d(TAG, "Fullscreen from mini player: exit mini then enter fullscreen")
                                        viewModel.exitMiniPlayer()
                                        viewModel.toggleFullscreen()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            // Progress bar
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                LinearProgressIndicator(
                                    progress = if (state.durationMs > 0) state.currentPositionMs.toFloat() / state.durationMs else 0f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.Transparent
                                )
                            }
                        }
                    }
                } else {
                    // ==================== DEFAULT / FULLSCREEN state ====================
                    // In fullscreen the player (and every control drawn over it) fills the
                    // entire screen. In normal/detail mode the player only occupies the top
                    // 60% of the screen. EVERYTHING below - the gesture box, brightness/volume
                    // indicators, and the top/bottom control bars - must be confined to that
                    // same region as one unit. It now tracks the exact same playerHeightPx used
                    // by the video content above, so it shrinks/expands in lockstep with the
                    // player as the user scrolls the details list - previously this used a fixed
                    // 60% independent of the collapsing player, so the bottom bar never moved.
                    val playerAreaModifier = if (isFullscreenAnim.value) {
                        Modifier
                            .align(Alignment.TopStart)
                            .fillMaxSize()
                    } else {
                        Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .height(with(LocalDensity.current) { playerHeightPx.toDp() })
                    }

                    // Below 30% of screen height (only reachable in normal, non-fullscreen mode),
                    // there isn't room for the full title/timeline controls - switch to a single
                    // compact row instead.
                    val isCollapsedControls = !isFullscreenAnim.value &&
                        containerSize.height > 0f &&
                        (playerHeightPx / containerSize.height) <= 0.3f

                    Box(modifier = playerAreaModifier.clipToBounds()) {
                        // ==================== Gesture layer (brightness/volume swipe, tap-to-toggle) ====================
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { _, dragAmount ->
                                            val delta = -dragAmount / 500f
                                            if (touchX < size.width / 2) {
                                                brightnessValue = (brightnessValue + delta).coerceIn(0f, 1f)
                                                viewModel.setBrightness(brightnessValue)
                                                showBrightnessIndicator = true
                                                coroutineScope.launch {
                                                    delay(1500)
                                                    showBrightnessIndicator = false
                                                }
                                            } else {
                                                volumeValue = (volumeValue + delta).coerceIn(0f, 1f)
                                                viewModel.setVolume(volumeValue)
                                                showVolumeIndicator = true
                                                coroutineScope.launch {
                                                    delay(1500)
                                                    showVolumeIndicator = false
                                                }
                                            }
                                        },
                                        onDragStart = { touchX = it.x }
                                    )
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            controlsVisible = true
                                            hideControlsJob?.cancel()
                                            showTopOverlay = !showTopOverlay
                                            showBottomOverlay = !showBottomOverlay
                                        },
                                        onDoubleTap = {
                                            if (isFullscreen) {
                                                Log.d(TAG, "Double-tap: exit fullscreen")
                                                viewModel.exitFullscreen()
                                            } else {
                                                Log.d(TAG, "Double-tap: enter fullscreen")
                                                viewModel.toggleFullscreen()
                                            }
                                        },
                                        onLongPress = { /* TODO: PiP */ }
                                    )
                                }
                        )

                        // ==================== Brightness Indicator ====================
                        AnimatedVisibility(
                            visible = showBrightnessIndicator,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(120.dp)
                            ) {
                                BrightnessIndicator(
                                    brightness = brightnessValue,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }

                        // ==================== Volume Indicator ====================
                        AnimatedVisibility(
                            visible = showVolumeIndicator,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(120.dp)
                            ) {
                                VolumeIndicator(
                                    volume = volumeValue,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }

                        // ==================== Double-tap seek indicators ====================
                        SeekIndicators(
                            showSeekBack = false,
                            showSeekForward = false
                        )

                        // ==================== Top Overlay ====================
                        AnimatedVisibility(
                            visible = showTopOverlay && !isCollapsedControls,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
                        ) {
                            TopOverlay(
                                title = state.currentVideo?.title ?: "Unknown",
                                channelName = state.currentVideo?.author?.name ?: "Unknown",
                                onMinimize = {
                                    Log.d(TAG, "Minimize button clicked")
                                    viewModel.minimize()
                                },
                                onReplayToggle = { /* TODO */ },
                                onWatchLater = { /* TODO */ },
                                onOptions = { showOptionsModal = true }
                            )
                        }

                        // ==================== Bottom Overlay ====================
                        AnimatedVisibility(
                            visible = showBottomOverlay && !isCollapsedControls,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            BottomOverlay(
                                currentPositionMs = state.currentPositionMs,
                                durationMs = state.durationMs,
                                isPlaying = state.isPlaying,
                                onPlayPause = {
                                    if (state.isPlaying) viewModel.pause() else viewModel.resume()
                                },
                                onPrevious = { viewModel.skipPrevious() },
                                onNext = { viewModel.skipNext() },
                                onChapters = { showChapters = !showChapters },
                                onFullscreen = { viewModel.toggleFullscreen() },
                                onSeek = { positionMs -> viewModel.seekTo(positionMs) }
                            )
                        }

                        // ==================== Compact Controls Row (collapsed player state) ====================
                        AnimatedVisibility(
                            visible = isCollapsedControls,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            CompactControlsRow(
                                isPlaying = state.isPlaying,
                                isLooping = isLooping,
                                onMinimize = {
                                    Log.d(TAG, "Minimize button clicked")
                                    viewModel.minimize()
                                },
                                onPlayPause = {
                                    if (state.isPlaying) viewModel.pause() else viewModel.resume()
                                },
                                onChapters = { showChapters = !showChapters },
                                onLoopToggle = {
                                    isLooping = !isLooping
                                    player?.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                },
                                onWatchLater = { /* TODO */ },
                                onOptions = { showOptionsModal = true },
                                onFullscreen = { viewModel.toggleFullscreen() }
                            )
                        }
                    }

                    // ==================== Options Modal (full-screen dialog) ====================
                    if (showOptionsModal) {
                        OptionsModal(
                            playbackSpeed = selectedSpeed,
                            quality = selectedQuality,
                            onSpeedChange = { speed ->
                                selectedSpeed = speed
                                viewModel.setPlaybackSpeed(speed)
                            },
                            onQualityChange = { quality ->
                                selectedQuality = quality
                                viewModel.setVideoQuality(quality)
                            },
                            onDismiss = { showOptionsModal = false }
                        )
                    }

                    // ==================== Chapters Panel (full-screen dialog) ====================
                    if (showChapters) {
                        ChaptersPanel(
                            chapters = emptyList(),
                            currentPositionMs = state.currentPositionMs,
                            onChapterClick = { positionMs ->
                                viewModel.seekTo(positionMs)
                                showChapters = false
                            },
                            onDismiss = { showChapters = false }
                        )
                    }
                }
            }
        }
        is PlayerUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
