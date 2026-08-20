/*
 * Vendored subset of ComposeReorderable (https://github.com/aclassen/ComposeReorderable),
 * Apache License 2.0, copyright André Claßen and the Android Open Source Project.
 *
 * Kept: lazy-list state (LazyRow + LazyColumn), ReorderableItem, reorderable(),
 * detectReorder(), the drag-slop helper and the spring cancel animation.
 * Dropped: grid support and the long-press drag variant (its isOutOfBounds()
 * helper no longer exists in Compose 1.11). The ReorderableItem default
 * dragging modifier is a plain Modifier here because Modifier.animateItem()
 * does not exist in this Compose version either — non-dragged items snap to
 * their new slot, the dragged item still follows the finger.
 */
package com.tsutsen.platformplayer.core.designsystem.reorder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.sign

data class ItemPosition(val index: Int, val key: Any?)

// ---------------------------------------------------------------------------
// Cancelled-drag animation
// ---------------------------------------------------------------------------

interface DragCancelledAnimation {
    suspend fun dragCancelled(position: ItemPosition, offset: Offset)
    val position: ItemPosition?
    val offset: Offset
}

class NoDragCancelledAnimation : DragCancelledAnimation {
    override suspend fun dragCancelled(position: ItemPosition, offset: Offset) {}
    override val position: ItemPosition? = null
    override val offset: Offset = Offset.Zero
}

class SpringDragCancelledAnimation(
    private val stiffness: Float = Spring.StiffnessMediumLow,
) : DragCancelledAnimation {
    private val animatable = Animatable(Offset.Zero, Offset.VectorConverter)
    override val offset: Offset
        get() = animatable.value

    override var position by mutableStateOf<ItemPosition?>(null)
        private set

    override suspend fun dragCancelled(position: ItemPosition, offset: Offset) {
        this.position = position
        animatable.snapTo(offset)
        animatable.animateTo(
            Offset.Zero,
            spring(stiffness = stiffness, visibilityThreshold = Offset.VisibilityThreshold),
        )
        this.position = null
    }
}

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

abstract class ReorderableState<T>(
    private val scope: CoroutineScope,
    private val maxScrollPerFrame: Float,
    private val onMove: (fromIndex: ItemPosition, toIndex: ItemPosition) -> Unit,
    private val canDragOver: ((draggedOver: ItemPosition, dragging: ItemPosition) -> Boolean)?,
    private val onDragEnd: ((startIndex: Int, endIndex: Int) -> Unit)?,
    val dragCancelledAnimation: DragCancelledAnimation,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set
    val draggingItemKey: Any?
        get() = selected?.itemKey

    protected abstract val T.left: Int
    protected abstract val T.top: Int
    protected abstract val T.right: Int
    protected abstract val T.bottom: Int
    protected abstract val T.width: Int
    protected abstract val T.height: Int
    protected abstract val T.itemIndex: Int
    protected abstract val T.itemKey: Any
    protected abstract val visibleItemsInfo: List<T>
    protected abstract val firstVisibleItemIndex: Int
    protected abstract val firstVisibleItemScrollOffset: Int
    protected abstract val viewportStartOffset: Int
    protected abstract val viewportEndOffset: Int

    internal val interactions = Channel<StartDrag>()
    internal val scrollChannel = Channel<Float>()

    val draggingItemLeft: Float
        get() = draggingLayoutInfo?.let { item ->
            (selected?.left ?: 0) + draggingDelta.x - item.left
        } ?: 0f
    val draggingItemTop: Float
        get() = draggingLayoutInfo?.let { item ->
            (selected?.top ?: 0) + draggingDelta.y - item.top
        } ?: 0f

    abstract val isVerticalScroll: Boolean

    private val draggingLayoutInfo: T?
        get() = visibleItemsInfo.firstOrNull { it.itemIndex == draggingItemIndex }

    private var draggingDelta by mutableStateOf(Offset.Zero)
    private var selected by mutableStateOf<T?>(null)
    private var autoscroller: Job? = null
    private val targets = mutableListOf<T>()
    private val distances = mutableListOf<Int>()

    protected abstract suspend fun scrollToItem(index: Int, offset: Int)

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun visibleItemsChanged() =
        snapshotFlow { draggingItemIndex != null }
            .flatMapLatest { if (it) snapshotFlow { visibleItemsInfo } else flowOf(null) }
            .filterNotNull()
            .distinctUntilChanged { old, new ->
                old.firstOrNull()?.itemIndex == new.firstOrNull()?.itemIndex &&
                    old.count() == new.count()
            }

    internal open fun onDragStart(offsetX: Int, offsetY: Int): Boolean {
        val x: Int
        val y: Int
        if (isVerticalScroll) {
            x = offsetX
            y = offsetY + viewportStartOffset
        } else {
            x = offsetX + viewportStartOffset
            y = offsetY
        }
        return visibleItemsInfo
            .firstOrNull { x in it.left..it.right && y in it.top..it.bottom }
            ?.also {
                selected = it
                draggingItemIndex = it.itemIndex
            } != null
    }

    internal fun onDragCanceled() {
        val dragIdx = draggingItemIndex
        if (dragIdx != null) {
            val position = ItemPosition(dragIdx, selected?.itemKey)
            val offset = Offset(draggingItemLeft, draggingItemTop)
            scope.launch { dragCancelledAnimation.dragCancelled(position, offset) }
        }
        val startIndex = selected?.itemIndex
        val endIndex = draggingItemIndex
        selected = null
        draggingDelta = Offset.Zero
        draggingItemIndex = null
        cancelAutoScroll()
        onDragEnd?.let { end ->
            if (startIndex != null && endIndex != null) end(startIndex, endIndex)
        }
    }

    internal fun onDrag(offsetX: Int, offsetY: Int) {
        val selected = selected ?: return
        draggingDelta = Offset(draggingDelta.x + offsetX, draggingDelta.y + offsetY)
        val draggingItem = draggingLayoutInfo ?: return
        val startOffset = draggingItem.top + draggingItemTop
        val startOffsetX = draggingItem.left + draggingItemLeft
        chooseDropItem(
            draggingItem,
            findTargets(draggingDelta.x.toInt(), draggingDelta.y.toInt(), selected),
            startOffsetX.toInt(),
            startOffset.toInt(),
        )?.also { targetItem ->
            if (
                targetItem.itemIndex == firstVisibleItemIndex ||
                    draggingItem.itemIndex == firstVisibleItemIndex
            ) {
                scope.launch {
                    onMove(
                        ItemPosition(draggingItem.itemIndex, draggingItem.itemKey),
                        ItemPosition(targetItem.itemIndex, targetItem.itemKey),
                    )
                    scrollToItem(firstVisibleItemIndex, firstVisibleItemScrollOffset)
                }
            } else {
                onMove(
                    ItemPosition(draggingItem.itemIndex, draggingItem.itemKey),
                    ItemPosition(targetItem.itemIndex, targetItem.itemKey),
                )
            }
            draggingItemIndex = targetItem.itemIndex
        }

        with(calcAutoScrollOffset(0, maxScrollPerFrame)) {
            if (this != 0f) autoscroll(this)
        }
    }

    private fun autoscroll(scrollOffset: Float) {
        if (scrollOffset != 0f) {
            if (autoscroller?.isActive == true) return
            autoscroller = scope.launch {
                var scroll = scrollOffset
                var start = 0L
                while (scroll != 0f && autoscroller?.isActive == true) {
                    withFrameMillis {
                        if (start == 0L) {
                            start = it
                        } else {
                            scroll = calcAutoScrollOffset(it - start, maxScrollPerFrame)
                        }
                    }
                    scrollChannel.trySend(scroll)
                }
            }
        } else {
            cancelAutoScroll()
        }
    }

    private fun cancelAutoScroll() {
        autoscroller?.cancel()
        autoscroller = null
    }

    protected open fun findTargets(x: Int, y: Int, selected: T): List<T> {
        targets.clear()
        distances.clear()
        val left = x + selected.left
        val right = x + selected.right
        val top = y + selected.top
        val bottom = y + selected.bottom
        val centerX = (left + right) / 2
        val centerY = (top + bottom) / 2
        visibleItemsInfo.fastForEach { item ->
            if (
                item.itemIndex == draggingItemIndex ||
                    item.bottom < top ||
                    item.top > bottom ||
                    item.right < left ||
                    item.left > right
            ) {
                return@fastForEach
            }
            if (
                canDragOver?.invoke(
                        ItemPosition(item.itemIndex, item.itemKey),
                        ItemPosition(selected.itemIndex, selected.itemKey),
                    ) != false
            ) {
                val dx = (centerX - (item.left + item.right) / 2).absoluteValue
                val dy = (centerY - (item.top + item.bottom) / 2).absoluteValue
                val dist = dx * dx + dy * dy
                var pos = 0
                for (j in targets.indices) {
                    if (dist > distances[j]) {
                        pos++
                    } else {
                        break
                    }
                }
                targets.add(pos, item)
                distances.add(pos, dist)
            }
        }
        return targets
    }

    protected open fun chooseDropItem(
        draggedItemInfo: T?,
        items: List<T>,
        curX: Int,
        curY: Int,
    ): T? {
        if (draggedItemInfo == null) {
            return if (draggingItemIndex != null) items.lastOrNull() else null
        }
        var target: T? = null
        var highScore = -1
        val right = curX + draggedItemInfo.width
        val bottom = curY + draggedItemInfo.height
        val dx = curX - draggedItemInfo.left
        val dy = curY - draggedItemInfo.top

        items.fastForEach { item ->
            if (dx > 0) {
                val diff = item.right - right
                if (diff < 0 && item.right > draggedItemInfo.right) {
                    val score = diff.absoluteValue
                    if (score > highScore) {
                        highScore = score
                        target = item
                    }
                }
            }
            if (dx < 0) {
                val diff = item.left - curX
                if (diff > 0 && item.left < draggedItemInfo.left) {
                    val score = diff.absoluteValue
                    if (score > highScore) {
                        highScore = score
                        target = item
                    }
                }
            }
            if (dy < 0) {
                val diff = item.top - curY
                if (diff > 0 && item.top < draggedItemInfo.top) {
                    val score = diff.absoluteValue
                    if (score > highScore) {
                        highScore = score
                        target = item
                    }
                }
            }
            if (dy > 0) {
                val diff = item.bottom - bottom
                if (diff < 0 && item.bottom > draggedItemInfo.bottom) {
                    val score = diff.absoluteValue
                    if (score > highScore) {
                        highScore = score
                        target = item
                    }
                }
            }
        }
        return target
    }

    private fun calcAutoScrollOffset(time: Long, maxScroll: Float): Float {
        val draggingItem = draggingLayoutInfo ?: return 0f
        val startOffset: Float
        val endOffset: Float
        val delta: Float
        if (isVerticalScroll) {
            startOffset = draggingItem.top + draggingItemTop
            endOffset = startOffset + draggingItem.height
            delta = draggingDelta.y
        } else {
            startOffset = draggingItem.left + draggingItemLeft
            endOffset = startOffset + draggingItem.width
            delta = draggingDelta.x
        }
        return when {
            delta > 0 -> (endOffset - viewportEndOffset).coerceAtLeast(0f)
            delta < 0 -> (startOffset - viewportStartOffset).coerceAtMost(0f)
            else -> 0f
        }.let {
            interpolateOutOfBoundsScroll((endOffset - startOffset).toInt(), it, time, maxScroll)
        }
    }

    companion object {
        private const val ACCELERATION_LIMIT_TIME_MS: Long = 1500

        private val EaseOutQuadInterpolator: (Float) -> Float = {
            val t = 1 - it
            1 - t * t * t * t
        }

        private val EaseInQuintInterpolator: (Float) -> Float = { it * it * it * it * it }

        private fun interpolateOutOfBoundsScroll(
            viewSize: Int,
            viewSizeOutOfBounds: Float,
            time: Long,
            maxScroll: Float,
        ): Float {
            if (viewSizeOutOfBounds == 0f) return 0f
            val outOfBoundsRatio =
                min(1f, 1f * viewSizeOutOfBounds.absoluteValue / viewSize)
            val cappedScroll =
                sign(viewSizeOutOfBounds) * maxScroll * EaseOutQuadInterpolator(outOfBoundsRatio)
            val timeRatio =
                if (time > ACCELERATION_LIMIT_TIME_MS) 1f
                else time.toFloat() / ACCELERATION_LIMIT_TIME_MS
            return (cappedScroll * EaseInQuintInterpolator(timeRatio)).let {
                if (it == 0f) {
                    if (viewSizeOutOfBounds > 0) 1f else -1f
                } else {
                    it
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// LazyList (LazyRow + LazyColumn)
// ---------------------------------------------------------------------------

@Composable
fun rememberReorderableLazyListState(
    onMove: (ItemPosition, ItemPosition) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    canDragOver: ((draggedOver: ItemPosition, dragging: ItemPosition) -> Boolean)? = null,
    onDragEnd: ((startIndex: Int, endIndex: Int) -> Unit)? = null,
    maxScrollPerFrame: Dp = 20.dp,
    dragCancelledAnimation: DragCancelledAnimation = SpringDragCancelledAnimation(),
): ReorderableLazyListState {
    val maxScroll = with(LocalDensity.current) { maxScrollPerFrame.toPx() }
    val scope = rememberCoroutineScope()
    val state =
        remember(listState) {
            ReorderableLazyListState(
                listState,
                scope,
                maxScroll,
                onMove,
                canDragOver,
                onDragEnd,
                dragCancelledAnimation,
            )
        }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(state) {
        state.visibleItemsChanged().collect { state.onDrag(0, 0) }
    }

    LaunchedEffect(state) {
        var reverseDirection = !listState.layoutInfo.reverseLayout
        if (isRtl && listState.layoutInfo.orientation != Orientation.Vertical) {
            reverseDirection = !reverseDirection
        }
        val direction = if (reverseDirection) 1f else -1f
        while (true) {
            val diff = state.scrollChannel.receive()
            listState.scrollBy(diff * direction)
        }
    }
    return state
}

class ReorderableLazyListState(
    val listState: LazyListState,
    scope: CoroutineScope,
    maxScrollPerFrame: Float,
    onMove: (fromIndex: ItemPosition, toIndex: ItemPosition) -> Unit,
    canDragOver: ((draggedOver: ItemPosition, dragging: ItemPosition) -> Boolean)? = null,
    onDragEnd: ((startIndex: Int, endIndex: Int) -> Unit)? = null,
    dragCancelledAnimation: DragCancelledAnimation = SpringDragCancelledAnimation(),
) : ReorderableState<LazyListItemInfo>(
    scope,
    maxScrollPerFrame,
    onMove,
    canDragOver,
    onDragEnd,
    dragCancelledAnimation,
) {
    override val isVerticalScroll: Boolean
        get() = listState.layoutInfo.orientation == Orientation.Vertical

    override val LazyListItemInfo.left: Int
        get() =
            when {
                isVerticalScroll -> 0
                listState.layoutInfo.reverseLayout ->
                    listState.layoutInfo.viewportSize.width - offset - size
                else -> offset
            }

    override val LazyListItemInfo.top: Int
        get() =
            when {
                !isVerticalScroll -> 0
                listState.layoutInfo.reverseLayout ->
                    listState.layoutInfo.viewportSize.height - offset - size
                else -> offset
            }

    override val LazyListItemInfo.right: Int
        get() =
            when {
                isVerticalScroll -> 0
                listState.layoutInfo.reverseLayout ->
                    listState.layoutInfo.viewportSize.width - offset
                else -> offset + size
            }

    override val LazyListItemInfo.bottom: Int
        get() =
            when {
                !isVerticalScroll -> 0
                listState.layoutInfo.reverseLayout ->
                    listState.layoutInfo.viewportSize.height - offset
                else -> offset + size
            }

    override val LazyListItemInfo.width: Int
        get() = if (isVerticalScroll) 0 else size

    override val LazyListItemInfo.height: Int
        get() = if (isVerticalScroll) size else 0

    override val LazyListItemInfo.itemIndex: Int
        get() = index

    override val LazyListItemInfo.itemKey: Any
        get() = key

    override val visibleItemsInfo: List<LazyListItemInfo>
        get() = listState.layoutInfo.visibleItemsInfo

    override val viewportStartOffset: Int
        get() = listState.layoutInfo.viewportStartOffset

    override val viewportEndOffset: Int
        get() = listState.layoutInfo.viewportEndOffset

    override val firstVisibleItemIndex: Int
        get() = listState.firstVisibleItemIndex

    override val firstVisibleItemScrollOffset: Int
        get() = listState.firstVisibleItemScrollOffset

    override suspend fun scrollToItem(index: Int, offset: Int) {
        listState.scrollToItem(index, offset)
    }

    override fun onDragStart(offsetX: Int, offsetY: Int): Boolean =
        if (isVerticalScroll) {
            super.onDragStart(0, offsetY)
        } else {
            super.onDragStart(offsetX, 0)
        }

    override fun findTargets(x: Int, y: Int, selected: LazyListItemInfo) =
        if (isVerticalScroll) {
            super.findTargets(0, y, selected)
        } else {
            super.findTargets(x, 0, selected)
        }

    override fun chooseDropItem(
        draggedItemInfo: LazyListItemInfo?,
        items: List<LazyListItemInfo>,
        curX: Int,
        curY: Int,
    ) =
        if (isVerticalScroll) {
            super.chooseDropItem(draggedItemInfo, items, 0, curY)
        } else {
            super.chooseDropItem(draggedItemInfo, items, curX, 0)
        }
}

// ---------------------------------------------------------------------------
// Item wrapper + modifiers
// ---------------------------------------------------------------------------

@Composable
fun LazyItemScope.ReorderableItem(
    reorderableState: ReorderableState<*>,
    key: Any?,
    modifier: Modifier = Modifier,
    index: Int? = null,
    orientationLocked: Boolean = true,
    content: @Composable BoxScope.(isDragging: Boolean) -> Unit,
) {
    val isDragging =
        if (index != null) {
            index == reorderableState.draggingItemIndex
        } else {
            key == reorderableState.draggingItemKey
        }
    val draggingModifier =
        if (isDragging) {
            Modifier
                .zIndex(1f)
                .graphicsLayer {
                    translationX =
                        if (!orientationLocked || !reorderableState.isVerticalScroll)
                            reorderableState.draggingItemLeft
                        else 0f
                    translationY =
                        if (!orientationLocked || reorderableState.isVerticalScroll)
                            reorderableState.draggingItemTop
                        else 0f
                }
        } else {
            val cancel =
                if (index != null) {
                    index == reorderableState.dragCancelledAnimation.position?.index
                } else {
                    key == reorderableState.dragCancelledAnimation.position?.key
                }
            if (cancel) {
                Modifier
                    .zIndex(1f)
                    .graphicsLayer {
                        translationX =
                            if (!orientationLocked || !reorderableState.isVerticalScroll)
                                reorderableState.dragCancelledAnimation.offset.x
                            else 0f
                        translationY =
                            if (!orientationLocked || reorderableState.isVerticalScroll)
                                reorderableState.dragCancelledAnimation.offset.y
                            else 0f
                    }
            } else {
                Modifier
            }
        }
    Box(modifier = modifier.then(draggingModifier)) {
        content(isDragging)
    }
}

fun Modifier.reorderable(state: ReorderableState<*>) =
    then(
        Modifier.pointerInput(Unit) {
            forEachGesture {
                val dragStart = state.interactions.receive()
                val down =
                    awaitPointerEventScope {
                        currentEvent.changes.fastFirstOrNull { it.id == dragStart.id }
                    }
                if (down != null && state.onDragStart(down.position.x.toInt(), down.position.y.toInt())) {
                    dragStart.offset?.apply { state.onDrag(x.toInt(), y.toInt()) }
                    detectDrag(
                        down.id,
                        onDragEnd = { state.onDragCanceled() },
                        onDragCancel = { state.onDragCanceled() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            state.onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                        },
                    )
                }
            }
        },
    )

internal suspend fun PointerInputScope.detectDrag(
    down: PointerId,
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitPointerEventScope {
        if (
            drag(down) {
                onDrag(it, it.positionChange())
                it.consume()
            }
        ) {
            currentEvent.changes.forEach {
                if (it.changedToUp()) it.consume()
            }
            onDragEnd()
        } else {
            onDragCancel()
        }
    }
}

internal data class StartDrag(val id: PointerId, val offset: Offset? = null)

/**
 * Starts a reorder drag once the finger has moved past the touch slop.
 * Apply this to the drag handle only.
 */
fun Modifier.detectReorder(state: ReorderableState<*>) =
    then(
        Modifier.pointerInput(Unit) {
            forEachGesture {
                awaitPointerEventScope {
                    val down = awaitFirstDown()
                    var drag: PointerInputChange?
                    var overSlop = Offset.Zero
                    do {
                        drag =
                            awaitPointerSlopOrCancellation(down.id, down.type) { change, over ->
                                change.consume()
                                overSlop = over
                            }
                    } while (drag != null && !drag.isConsumed)
                    if (drag != null) {
                        state.interactions.trySend(StartDrag(down.id, overSlop))
                    }
                }
            }
        },
    )

// ---------------------------------------------------------------------------
// Drag-slop helper (from Compose's DragGestureDetector, pre-pointer-api style)
// ---------------------------------------------------------------------------

internal suspend fun AwaitPointerEventScope.awaitPointerSlopOrCancellation(
    pointerId: PointerId,
    pointerType: PointerType,
    onPointerSlopReached: (change: PointerInputChange, overSlop: Offset) -> Unit,
): PointerInputChange? {
    if (currentEvent.isPointerUp(pointerId)) {
        return null
    }
    var offset = Offset.Zero
    val touchSlop = viewConfiguration.pointerSlop(pointerType)
    var pointer = pointerId

    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.isConsumed) {
            return null
        } else if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return null
            } else {
                pointer = otherDown.id
            }
        } else {
            offset += dragEvent.positionChange()
            val distance = offset.getDistance()
            var acceptedDrag = false
            if (distance >= touchSlop) {
                val touchSlopOffset = offset / distance * touchSlop
                onPointerSlopReached(dragEvent, offset - touchSlopOffset)
                if (dragEvent.isConsumed) {
                    acceptedDrag = true
                } else {
                    offset = Offset.Zero
                }
            }

            if (acceptedDrag) {
                return dragEvent
            } else {
                awaitPointerEvent(PointerEventPass.Final)
                if (dragEvent.isConsumed) {
                    return null
                }
            }
        }
    }
}

private fun PointerEvent.isPointerUp(pointerId: PointerId): Boolean =
    changes.fastFirstOrNull { it.id == pointerId }?.pressed != true

// This value was determined using experiments and common sense.
// We can't use zero slop, because some devices send pointer events with a
// very high precision.
private val mouseSlop = 0.125.dp
private val defaultTouchSlop = 18.dp // The default touch slop on Android devices
private val mouseToTouchSlopRatio = mouseSlop / defaultTouchSlop

private fun ViewConfiguration.pointerSlop(pointerType: PointerType): Float {
    return when (pointerType) {
        PointerType.Mouse -> touchSlop * mouseToTouchSlopRatio
        else -> touchSlop
    }
}
