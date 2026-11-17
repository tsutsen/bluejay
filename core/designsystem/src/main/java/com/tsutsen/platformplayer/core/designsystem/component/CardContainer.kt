package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.SurfaceKeys
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.designsystem.theme.surfaceColor
import com.tsutsen.platformplayer.core.designsystem.theme.surfaceTheme
import com.tsutsen.platformplayer.core.designsystem.theme.surfaceContentColor
import com.tsutsen.platformplayer.core.designsystem.theme.surfaceTexture
import com.tsutsen.platformplayer.core.designsystem.theme.surfaceOutline
import com.tsutsen.platformplayer.core.model.Card

/**
 * The standard gap between a [ContentCard] edge and the cards laid on it:
 * the difference of their corner radii. Both radii rescale with the user's
 * corner rounding setting, so the inset tracks them (sharp rounding = zero
 * gap, extra rounding = a wider gap).
 */
@Composable
fun ContentCardInnerGap(): Dp =
    (BluejayTokens().radius.card - BluejayTokens().radius.sm).coerceAtLeast(0.dp)

/**
 * Content-area card: the rounded [surfaceContainer] panel that a scrolling
 * video container lays its cards on. The inner video cards stay `surface`,
 * so the panel's tone separates the card group from the screen background
 * (and makes an empty area read as a card, not as dead space).
 *
 * The panel insets itself from the screen edges ([Tokens.SpaceLg] on the
 * sides and bottom, like the library's section columns) — callers just
 * pass [fillMaxSize].
 *
 * The panel itself never scrolls — the lazy list scrolls inside it, so
 * wrap it around the lazy container, not the other way round.
 */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // One packed resolve instead of four helper calls.
    val theme = surfaceTheme(
        SurfaceKeys.VIDEO_CARD,
        fallbackColor = MaterialTheme.colorScheme.surfaceContainer,
        fallbackContent = MaterialTheme.colorScheme.onSurface,
    )
    Box(
        modifier =
            modifier
                .padding(
                    start = Tokens.SpaceLg,
                    top = Tokens.SpaceSm,
                    end = Tokens.SpaceLg,
                    bottom = Tokens.SpaceLg,
                )
                .clip(RoundedCornerShape(BluejayTokens().radius.card))
                .background(theme.color)
                // Texture stays on the existing modifier (it reads the skin's
                // decoded bitmap); the packed theme carries color/text/border.
                .surfaceTexture(SurfaceKeys.VIDEO_CARD)
                .surfaceOutline(SurfaceKeys.VIDEO_CARD, MaterialTheme.shapes.large),
    ) {
        CompositionLocalProvider(LocalContentColor provides theme.contentColor) {
            content()
        }
    }
}

/**
 * Layout mode for the video container.
 */
sealed interface ContainerLayout {
    /** Single-column vertical scroll (portrait). */
    object List : ContainerLayout

    /** Horizontal scrollable row (for sections). */
    object HorizontalStrip : ContainerLayout

    /** Multi-column grid (landscape). */
    data class Grid(
        val columns: Int,
    ) : ContainerLayout

    /**
     * Fixed-height vertical paginated grid.
     * Shows columns × rowsPerPage items in a grid.
     * Container has fixed height, not fillMaxSize.
     */
    data class PaginatedVertical(
        val columns: Int,
        val rowsPerPage: Int,
    ) : ContainerLayout

    /**
     * Fixed-width horizontal paginated grid.
     * Shows columns × rowsPerPage items in a grid.
     * Container has fixed width, not fillMaxSize.
     */
    data class PaginatedHorizontal(
        val columns: Int,
        val rowsPerPage: Int,
    ) : ContainerLayout
}

/**
 * Type-agnostic video container composable.
 * Supports List, HorizontalStrip, and Grid layout modes.
 * Handles infinite scroll pagination internally.
 *
 * @param items List of cards to display
 * @param layout Layout mode (List, HorizontalStrip, or Grid with column count)
 * @param isLoading Whether data is currently being loaded (prevents duplicate requests)
 * @param hasMorePages Whether there are more pages to load
 * @param onCardClick Called when a card is tapped
 * @param onLoadMore Called when the user scrolls to the end (if !isLoading && hasMorePages)
 * @param modifier Modifier for the container
 * @param contentPadding Padding around the content
 * @param cardContent Composable that renders a single card
 * @param trailingContent Optional extra item rendered after all cards (e.g. an "All (n)" card)
 */
@Composable
fun VideoContainer(
    items: List<Card>,
    layout: ContainerLayout = ContainerLayout.List,
    isLoading: Boolean,
    hasMorePages: Boolean,
    onCardClick: (Card) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    // The gap between the ContentCard edge and the inner video cards is the
    // difference of their radii. Both radii rescale with the user's corner
    // rounding setting, so the inset tracks them (null = derive it).
    contentPadding: PaddingValues? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    topContent: (@Composable () -> Unit)? = null,
    cardContent: @Composable (Card) -> Unit,
) {
    val effectivePadding = contentPadding ?: PaddingValues(ContentCardInnerGap())
    when (layout) {
        is ContainerLayout.List -> {
            val state = rememberLazyListState()
            AutoLoadFill(
                listState = state,
                itemCount = items.size,
                hasMorePages = hasMorePages,
                onLoadMore = onLoadMore,
            )
            BottomLoadMoreBox(
                isLoading = isLoading,
                hasMorePages = hasMorePages,
                onLoadMore = onLoadMore,
                atEnd = { !state.canScrollForward },
                modifier = modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    contentPadding = effectivePadding,
                    verticalArrangement = Arrangement.spacedBy(Tokens.SpaceLg),
                ) {
                    if (topContent != null) {
                        item(key = "__top__") { topContent() }
                    }
                    renderCards(items, cardContent, trailingContent)
                }
            }
        }

        is ContainerLayout.HorizontalStrip -> {
            val state = rememberLazyListState()
            ScrollEndReached(
                listState = state,
                gridState = null,
                itemCount = items.size,
                isLoading = isLoading,
                hasMorePages = hasMorePages,
                threshold = 6,
                onEndReached = onLoadMore,
            )
            LazyRow(
                modifier = modifier.fillMaxWidth(),
                state = state,
                contentPadding = effectivePadding,
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
            ) {
                renderCards(items, cardContent, trailingContent)
            }
        }

        is ContainerLayout.Grid -> {
            val state = rememberLazyGridState()
            AutoLoadFill(
                gridState = state,
                itemCount = items.size,
                hasMorePages = hasMorePages,
                onLoadMore = onLoadMore,
            )
            BottomLoadMoreBox(
                isLoading = isLoading,
                hasMorePages = hasMorePages,
                onLoadMore = onLoadMore,
                atEnd = { !state.canScrollForward },
                modifier = modifier.fillMaxSize(),
            ) {
                LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    columns = GridCells.Fixed(layout.columns),
                    contentPadding = effectivePadding,
                    horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
                    verticalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
                ) {
                    if (topContent != null) {
                        item(key = "__top__") { topContent() }
                    }
                    renderCards(items, cardContent, trailingContent)
                }
            }
        }

        is ContainerLayout.PaginatedVertical -> {
            val state = rememberLazyGridState()
            // Threshold = page size (columns × rowsPerPage) so load triggers one page before end
            val pageSize = layout.columns * layout.rowsPerPage
            ScrollEndReached(
                listState = null,
                gridState = state,
                itemCount = items.size,
                isLoading = isLoading,
                hasMorePages = hasMorePages,
                threshold = pageSize,
                onEndReached = onLoadMore,
            )
            LazyVerticalGrid(
                modifier = modifier.fillMaxWidth(),
                state = state,
                columns = GridCells.Fixed(layout.columns),
                contentPadding = effectivePadding,
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
            ) {
                items(items, key = { it.id }) { card ->
                    cardContent(card)
                }
            }
        }

        is ContainerLayout.PaginatedHorizontal -> {
            val state = rememberLazyGridState()
            // Threshold = page size (columns × rowsPerPage) so load triggers one page before end
            val pageSize = layout.columns * layout.rowsPerPage
            ScrollEndReached(
                listState = null,
                gridState = state,
                itemCount = items.size,
                isLoading = isLoading,
                hasMorePages = hasMorePages,
                threshold = pageSize,
                onEndReached = onLoadMore,
            )
            LazyHorizontalGrid(
                modifier = modifier.fillMaxHeight(),
                state = state,
                rows = GridCells.Fixed(layout.rowsPerPage),
                contentPadding = effectivePadding,
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
            ) {
                items(items, key = { it.id }) { card ->
                    cardContent(card)
                }
            }
        }
    }
}

/** Minimum time the committed ring stays pinned, so fast loads still read as an action. */
private const val BOTTOM_LOAD_MIN_PIN_MS = 600L

/** Hard cap on waiting out an observable loading state before settling the ring away. */
private const val BOTTOM_LOAD_MAX_PIN_MS = 30_000L

/** How far above the bottom edge the ring rests once fully pulled in. */
private val BOTTOM_LOAD_REST_DP = 20.dp

/**
 * The bottom twin of pull-to-refresh. When the content is scrolled to its
 * end, dragging further up pulls a circular indicator up from the bottom
 * edge — it follows the finger and the content shifts up damped, so the
 * overdrag visibly "grabs" something. Releasing past the threshold commits
 * the load: the indicator pins at its resting spot with the M3 loading
 * spinner until [isLoading] clears, then settles back out of view.
 * Without a commit (or with [hasMorePages] false) everything is inert.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomLoadMoreBox(
    isLoading: Boolean,
    hasMorePages: Boolean,
    onLoadMore: () -> Unit,
    atEnd: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!hasMorePages) {
        Box(modifier) { content() }
        return
    }
    val density = LocalDensity.current
    val thresholdPx = with(density) { 80.dp.toPx() }
    val maxPullPx = with(density) { 160.dp.toPx() }
    val ringSizePx = with(density) { 48.dp.toPx() }
    val gapPx = with(density) { 12.dp.toPx() }
    // Distance the ring travels from fully hidden below the edge to fully
    // visible...
    val ringTravelPx = ringSizePx + gapPx
    // ...plus the resting inset: at the commit threshold it sits seated
    // [BOTTOM_LOAD_REST_DP] above the bottom edge, not flush with it.
    val restPx = with(density) { BOTTOM_LOAD_REST_DP.toPx() }
    val holdPx = thresholdPx

    val pull = remember { mutableFloatStateOf(0f) }
    var pinned by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isLoadingNow = rememberUpdatedState(isLoading)

    // The running settle/hold animation. The finger must always win over
    // it: while it runs, [pull] is written every frame, so a drag that
    // doesn't cancel it gets yanked back — felt as sticky, inertia-less
    // scrolling.
    var pullAnimJob: kotlinx.coroutines.Job? = null

    // Animates the pull to [target] on a spring, mirroring each frame into
    // [pull] (the drag writes it directly; this takes over on release).
    fun animatePullTo(target: Float) {
        pullAnimJob?.cancel()
        pullAnimJob = scope.launch {
            val anim = Animatable(pull.floatValue)
            val job = launch { anim.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) }
            while (job.isActive) {
                pull.floatValue = anim.value
                withFrameNanos { }
            }
            pull.floatValue = anim.value
        }
    }

    val settle: () -> Unit = { animatePullTo(0f) }

    // Commit owns the pin's whole lifecycle — it must NOT depend on
    // observing isLoading become true: a fast load flips the repository's
    // isLoading true->false within one frame, so a keyed effect would never
    // see the transition and the pin would stick forever. Pin, snap to the
    // hold spot, load; then keep the ring at least [BOTTOM_LOAD_MIN_PIN_MS]
    // (so quick loads still read as a deliberate action), and wait out any
    // observable loading before settling away.
    val commit: () -> Unit = {
        if (!pinned) {
            pinned = true
            scope.launch {
                animatePullTo(holdPx)
                onLoadMore()
                val start = android.os.SystemClock.elapsedRealtime()
                delay(BOTTOM_LOAD_MIN_PIN_MS)
                while (isLoadingNow.value && android.os.SystemClock.elapsedRealtime() - start < BOTTOM_LOAD_MAX_PIN_MS) {
                    withFrameNanos { }
                }
                pinned = false
                animatePullTo(0f)
            }
        }
    }

    val connection = remember {
        object : NestedScrollConnection {
            var atEndCheck: () -> Boolean = { false }
            var onCommit: () -> Unit = {}
            var grabPull: () -> Unit = {}
            var onRelease: () -> Unit = {}
            var threshold = 0f
            var maxPull = 0f
            var isPinned: () -> Boolean = { false }

            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || isPinned()) return Offset.Zero
                // Upward drag at the end = pull; downward drag retracts it.
                if (atEndCheck() && (available.y < 0f || pull.floatValue > 0f)) {
                    grabPull() // the finger takes over from any settle animation
                    val newPull = (pull.floatValue - available.y).coerceIn(0f, maxPull)
                    val consumed = pull.floatValue - newPull
                    pull.floatValue = newPull
                    return Offset(available.x, consumed)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Return value = the portion WE consume. Pass-through must be
                // Velocity.Zero, not `available` — returning the full velocity
                // ate every fling and the list lost all inertia.
                if (isPinned()) return Velocity.Zero
                if (pull.floatValue >= threshold) {
                    onCommit()
                    return available // the ring takes over the motion
                }
                if (pull.floatValue > 0f) {
                    // Retract, but do NOT eat the fling: eating it here made
                    // a fling right after a small overdrag attempt stop dead.
                    // The list is at its end anyway, so the fling has nothing
                    // to scroll; the settle animation runs alongside.
                    onRelease()
                }
                return Velocity.Zero
            }
        }
    }
    SideEffect {
        connection.atEndCheck = atEnd
        connection.onCommit = commit
        connection.grabPull = {
            // Finger grabs the pull: kill the settle animation so its
            // per-frame writes stop fighting the drag.
            pullAnimJob?.cancel()
            pullAnimJob = null
        }
        connection.onRelease = settle
        connection.threshold = thresholdPx
        connection.maxPull = maxPullPx
        connection.isPinned = { pinned }
    }

    Box(
        modifier
            .nestedScroll(connection)
            .clipToBounds(),
    ) {
        // Damped content shift up, mirroring the top pull-to-refresh's
        // downward shift so the overdrag "grabs" something.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = -pull.floatValue * 0.5f },
        ) { content() }
        if (pull.floatValue > 0.5f || pinned) {
            ContainedLoadingIndicator(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .size(48.dp)
                        .graphicsLayer {
                            translationY = (ringTravelPx - pull.floatValue).coerceAtLeast(0f) - restPx
                            alpha = (pull.floatValue / ringTravelPx).coerceIn(0f, 1f)
                        },
            )
        }
    }
}

/**
 * Auto-fills the first screen: when a batch of items lands and the list
 * can't scroll (content shorter than the viewport) while more pages exist,
 * load the next page automatically. Mirrors grayjay's
 * FeedView.ensureEnoughContentVisible — without it a 9-card merged home
 * feed sits half-empty until the user scrolls.
 */
@Composable
private fun AutoLoadFill(
    listState: LazyListState? = null,
    gridState: LazyGridState? = null,
    itemCount: Int,
    hasMorePages: Boolean,
    onLoadMore: () -> Unit,
) {
    // Deliberately NOT gated on isLoading: while late sources are still
    // pending the first wave is a handful of cards and the list is
    // underfilled — the app should fill the screen straight away instead of
    // waiting for the user to scroll. Concurrent requests are dropped by the
    // repository's single-flight guard; the loop self-terminates once the
    // content overflows the viewport (or pages run out).
    LaunchedEffect(itemCount, hasMorePages) {
        if (!hasMorePages || itemCount == 0) return@LaunchedEffect
        // Wait one frame so the just-added items are laid out and
        // canScrollForward reflects reality.
        withFrameNanos { }
        val canScroll = listState?.canScrollForward ?: gridState?.canScrollForward ?: true
        if (!canScroll) onLoadMore()
    }
}

/**
 * Detects when the user scrolls near the end of a lazy list/grid and triggers onLoadMore.
 * Fires when the last visible item is within `threshold` items of the end.
 *
 * @param itemCount Number of items (used as LaunchedEffect key to re-evaluate when items are added)
 * @param threshold Number of items before the end to trigger loading (default: 1)
 */
@Composable
fun ScrollEndReached(
    listState: LazyListState?,
    gridState: LazyGridState?,
    itemCount: Int,
    isLoading: Boolean,
    hasMorePages: Boolean,
    threshold: Int = 1,
    onEndReached: () -> Unit,
) {
    // Handle lazy list (List, HorizontalStrip)
    if (listState != null) {
        LaunchedEffect(listState.isScrollInProgress, itemCount) {
            if (listState.isScrollInProgress && !isLoading && hasMorePages) {
                val lastVisibleIndex =
                    listState.layoutInfo.visibleItemsInfo
                        .lastOrNull()
                        ?.index ?: -1
                val totalItems = listState.layoutInfo.totalItemsCount
                if (lastVisibleIndex >= totalItems - threshold && totalItems > 0) {
                    onEndReached()
                }
            }
        }
    }

    // Handle lazy grid (Grid)
    if (gridState != null) {
        LaunchedEffect(gridState.isScrollInProgress, itemCount) {
            if (gridState.isScrollInProgress && !isLoading && hasMorePages) {
                val lastVisibleIndex =
                    gridState.layoutInfo.visibleItemsInfo
                        .lastOrNull()
                        ?.index ?: -1
                val totalItems = gridState.layoutInfo.totalItemsCount
                if (lastVisibleIndex >= totalItems - threshold && totalItems > 0) {
                    onEndReached()
                }
            }
        }
    }
}

/**
 * Renders a list of cards in a lazy list scope.
 */
private fun LazyListScope.renderCards(
    items: List<Card>,
    cardContent: @Composable (Card) -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    items(items, key = { it.id }) { card -> cardContent(card) }
    if (trailingContent != null) {
        item(key = "__trailing__") { trailingContent() }
    }
}

/**
 * Renders a list of cards in a lazy grid scope.
 */
private fun LazyGridScope.renderCards(
    items: List<Card>,
    cardContent: @Composable (Card) -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    items(items, key = { it.id }) { card -> cardContent(card) }
    if (trailingContent != null) {
        item(key = "__trailing__") { trailingContent() }
    }
}
