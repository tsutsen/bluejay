package com.futo.platformplayer.feature.player.impl

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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.futo.platformplayer.core.designsystem.component.VideoCardSkeleton
import com.futo.platformplayer.core.model.Author
import com.futo.platformplayer.core.model.ContentItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    // ==================== ExoPlayer (managed lifecycle) ====================
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is PlayerUiState.Loaded) {
            val state = uiState as PlayerUiState.Loaded
            brightnessValue = state.brightness
            volumeValue = state.volume
        }
    }

    // Auto-hide overlays when playing
    LaunchedEffect(showTopOverlay, showBottomOverlay, uiState) {
        if (showTopOverlay && showBottomOverlay) {
            if (uiState is PlayerUiState.Loaded && (uiState as PlayerUiState.Loaded).isPlaying) {
                delay(3000)
                if (uiState is PlayerUiState.Loaded && (uiState as PlayerUiState.Loaded).isPlaying) {
                    showTopOverlay = false
                    showBottomOverlay = false
                }
            }
        }
    }

    // Sync animation state with actual state
    LaunchedEffect(uiState) {
        if (uiState is PlayerUiState.Loaded) {
            val state = uiState as PlayerUiState.Loaded
            isMinimizedAnim.value = state.isMinimized
            isFullscreenAnim.value = state.isFullscreen
            // Show overlays when exiting mini player
            if (!state.isMinimized) {
                showTopOverlay = true
                showBottomOverlay = true
            }
            Log.d(TAG, "Animation state synced: isMinimized=${state.isMinimized}, isFullscreen=${state.isFullscreen}")
        }
    }

    when (val state = uiState) {
        is PlayerUiState.Initial -> {
            // No player active — don't show anything
        }
        is PlayerUiState.Loaded -> {
            val isTablet = configuration.smallestScreenWidthDp >= 600
            val miniPlayerScale = if (isTablet) 0.35f else 0.45f

            // Load video into ExoPlayer
            LaunchedEffect(state.currentVideo?.url) {
                if (state.currentVideo?.url != null) {
                    player.setMediaItem(MediaItem.fromUri(state.currentVideo!!.url))
                    player.prepare()
                    player.playWhenReady = true
                    Log.d(TAG, "MediaItem loaded: ${state.currentVideo!!.url}")
                }
            }

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
                                this.player = player
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (!isMinimizedAnim.value) {
                    // Detail page: player at 60% height, details scrollable below
                    val playerHeight = containerSize.height * 0.6f
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Player takes 60% of screen height
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(with(LocalDensity.current) { playerHeight.toDp() })
                                .background(Color.Black)
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
                                        this.player = player
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            // Minimize button overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.minimize() },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Minimize",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Scrollable details below player
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(y = with(LocalDensity.current) { playerHeight.toDp() })
                        ) {
                            // Title and Meta
                            item {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = state.currentVideo?.title ?: "Unknown",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = formatViewCount(state.currentVideo?.viewCount ?: 0),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatRelativeTime(state.currentVideo?.publishedAt),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Channel Row
                            item {
                                ChannelRow(
                                    author = state.currentVideo?.author,
                                    onSubscribe = { /* TODO */ },
                                    onWatchLater = { /* TODO */ },
                                    onShare = { /* TODO */ },
                                    onMore = { /* TODO */ }
                                )
                            }

                            // Description
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
                                    item {
                                        CommentsSection()
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

                    // ==================== Options Modal ====================
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

                    // ==================== Chapters Panel ====================
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

                // ==================== Top Overlay (always composed, animates visibility) ====================
                AnimatedVisibility(
                    visible = showTopOverlay && !isMinimizedAnim.value,
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

                // ==================== Bottom Overlay (always composed, animates visibility) ====================
                AnimatedVisibility(
                    visible = showBottomOverlay && !isMinimizedAnim.value,
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
                        onFullscreen = { viewModel.toggleFullscreen() }
                    )
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

// ==================== Overlay Composables ====================

@Composable
private fun TopOverlay(
    title: String,
    channelName: String,
    onMinimize: () -> Unit,
    onReplayToggle: () -> Unit,
    onWatchLater: () -> Unit,
    onOptions: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMinimize) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Minimize",
                tint = Color.White
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = channelName,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onReplayToggle) {
            Icon(
                imageVector = Icons.Default.Replay,
                contentDescription = "Replay",
                tint = Color.White
            )
        }
        IconButton(onClick = onWatchLater) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Watch Later",
                tint = Color.White
            )
        }
        IconButton(onClick = onOptions) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Options",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun BottomOverlay(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChapters: () -> Unit,
    onFullscreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Timeline
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = formatTime(currentPositionMs),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatTime(durationMs),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onChapters) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = "Chapters",
                    tint = Color.White
                )
            }
            IconButton(onClick = onFullscreen) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun BrightnessIndicator(
    brightness: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.BrightnessHigh,
            contentDescription = "Brightness",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(100.dp)
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(brightness)
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${(brightness * 100).toInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun VolumeIndicator(
    volume: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = "Volume",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(100.dp)
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(volume)
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${(volume * 100).toInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SeekIndicators(
    showSeekBack: Boolean,
    showSeekForward: Boolean
) {
    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        if (showSeekBack) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Seek back 10s",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
        if (showSeekForward) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Seek forward 10s",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsModal(
    playbackSpeed: Float,
    quality: String,
    onSpeedChange: (Float) -> Unit,
    onQualityChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Text(
            text = "Speed",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                FilterChip(
                    selected = playbackSpeed == speed,
                    onClick = { onSpeedChange(speed) },
                    label = { Text("${speed}x") }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Quality",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            listOf("Auto", "1080p", "720p", "480p", "360p").forEach { q ->
                FilterChip(
                    selected = quality == q,
                    onClick = { onQualityChange(q) },
                    label = { Text(q) }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChaptersPanel(
    chapters: List<Chapter>,
    currentPositionMs: Long,
    onChapterClick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        LazyColumn {
            itemsIndexed(chapters) { _, chapter ->
                val isSelected = currentPositionMs in chapter.startTimeMs..chapter.endTimeMs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                            MaterialTheme.shapes.medium
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(chapter.startTimeMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

data class Chapter(
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

private fun formatTime(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

private fun formatViewCount(viewCount: Long): String {
    return when {
        viewCount >= 1_000_000 -> "${String.format(Locale.getDefault(), "%.1f", viewCount / 1_000_000.0)}M"
        viewCount >= 1_000 -> "${String.format(Locale.getDefault(), "%.1f", viewCount / 1_000.0)}K"
        else -> viewCount.toString()
    }
}

private fun formatRelativeTime(publishedAt: Long?): String {
    if (publishedAt == null) return ""
    val now = System.currentTimeMillis()
    val diffMs = now - publishedAt
    val diffSeconds = diffMs / 1000
    val diffMinutes = diffSeconds / 60
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24
    val diffWeeks = diffDays / 7
    val diffMonths = diffDays / 30
    val diffYears = diffDays / 365

    return when {
        diffYears > 0L -> "$diffYears${if (diffYears == 1L) " year" else " years"} ago"
        diffMonths > 0L -> "$diffMonths${if (diffMonths == 1L) " month" else " months"} ago"
        diffWeeks > 0L -> "$diffWeeks${if (diffWeeks == 1L) " week" else " weeks"} ago"
        diffDays > 0L -> "$diffDays${if (diffDays == 1L) " day" else " days"} ago"
        diffHours > 0L -> "$diffHours${if (diffHours == 1L) " hour" else " hours"} ago"
        diffMinutes > 0L -> "$diffMinutes${if (diffMinutes == 1L) " minute" else " minutes"} ago"
        else -> "Just now"
    }
}

@Composable
private fun ChannelRow(
    author: Author?,
    onSubscribe: () -> Unit,
    onWatchLater: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        if (author?.thumbnailUrl != null) {
            AsyncImage(
                model = author.thumbnailUrl,
                contentDescription = author.name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (author?.name?.firstOrNull()?.toString() ?: "?").uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Channel Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = author?.name ?: "Unknown Channel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "125K subscribers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Subscribe Button
        Button(
            onClick = onSubscribe,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text(
                text = "Subscribe",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }

    // Action buttons
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Like/Dislike
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.ThumbUp,
                contentDescription = "Like",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "1.2K",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Dislike
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.ThumbDown,
                contentDescription = "Dislike",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "45",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Watch Later
        IconButton(onClick = onWatchLater) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Watch Later",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Share
        IconButton(onClick = onShare) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // More
        IconButton(onClick = onMore) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DescriptionSection(
    description: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (description.isNotEmpty()) {
            Text(
                text = if (isExpanded) description else description.take(200) + if (description.length > 200) "..." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onToggle) {
                Text(
                    text = if (isExpanded) "Show less" else "Show more",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun TabsSection(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        TabItem(
            text = "Comments",
            isSelected = selectedTab == 0,
            onClick = { onTabSelected(0) }
        )
        TabItem(
            text = "Recommended",
            isSelected = selectedTab == 1,
            onClick = { onTabSelected(1) }
        )
    }
}

@Composable
private fun TabItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun CommentsSection() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Comments",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Placeholder comment
        CommentCard(
            username = "User123",
            timeAgo = "2 hours ago",
            text = "This is a great video! Thanks for sharing.",
            likeCount = 42
        )
    }
}

@Composable
private fun RecommendedSection() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Recommended",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Placeholder recommended videos
        for (i in 1..5) {
            RecommendedVideoCard(
                title = "Recommended Video $i",
                channelName = "Channel $i",
                viewCount = "${100 * i}K views",
                timeAgo = "$i days ago"
            )
        }
    }
}

@Composable
private fun CommentCard(
    username: String,
    timeAgo: String,
    text: String,
    likeCount: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = username.first().toString().uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = username,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = timeAgo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.ThumbUp,
                contentDescription = "Like",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = likeCount.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            TextButton(onClick = { /* TODO: Reply */ }) {
                Text("Reply", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun RecommendedVideoCard(
    title: String,
    channelName: String,
    viewCount: String,
    timeAgo: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(160.dp, 90.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Thumbnail",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = channelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$viewCount • $timeAgo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
