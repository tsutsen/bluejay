/*
 * Vendored and adapted from ComposeReorderable (https://github.com/aclassen/ComposeReorderable),
 * Apache License 2.0, copyright André Claßen and the Android Open Source Project.
 *
 * Adapted for this app (Compose 1.11.2):
 * - Non-lazy layouts (Row + horizontalScroll / Column + verticalScroll).
 *   Item positions are registered by each item in the list's visible space
 *   (screen position minus the list's screen position), the same space a
 *   pointer-down arrives in.
 * - The reorder drag starts on LONG-PRESS of the drag handle (hold the dots,
 *   then drag). Immediate slop-based dragging fights the list's own scroll
 *   gesture.
 * - FLIP animation of the other items is driven by the component from
 *   data changes (index deltas), NOT from position watching — watching
 *   positions feeds back on the animation itself (and on scrolling).
 * Dropped: grid support, the lazy-list state, auto-scroll while dragging.
 */
package com.tsutsen.platformplayer.core.designsystem.reorder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.absoluteValue

data class ItemPosition(val index: Int, val key: Any?)

/** IntOffset does not exist in this Compose version. */
private data class IntPoint(val x: Int, val y: Int)

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
    private var _position: ItemPosition? = null
    override val position: ItemPosition? get() = _position
    override val offset: Offset get() = animatable.value

    override suspend fun dragCancelled(position: ItemPosition, offset: Offset) {
        _position = position
        animatable.snapTo(offset)
        animatable.animateTo(
            targetValue = Offset.Zero,
            animationSpec = spring(stiffness = stiffness),
        )
        _position = null
    }
}

// ---------------------------------------------------------------------------
// State (ported from the library's ReorderableState, sans auto-scroll)
// ---------------------------------------------------------------------------

abstract class ReorderableState<T>(
    private val scope: CoroutineScope,
    private val onMove: (ItemPosition, ItemPosition) -> Unit,
    private val canDragOver: ((draggedOver: ItemPosition, dragging: ItemPosition) -> Boolean)?,
    private val onDragEnd: ((startIndex: Int, endIndex: Int) -> Unit)?,
    val dragCancelledAnimation: DragCancelledAnimation,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    val draggingItemKey: Any? get() = selected?.itemKey

    protected abstract val T.left: Int
    protected abstract val T.top: Int
    protected abstract val T.right: Int
    protected abstract val T.bottom: Int
    protected abstract val T.width: Int
    protected abstract val T.height: Int
    protected abstract val T.itemIndex: Int
    protected abstract val T.itemKey: Any
    abstract val isVerticalScroll: Boolean
    protected abstract val visibleItemsInfo: List<T>
    protected abstract val viewportStartOffset: Int
    protected abstract val viewportEndOffset: Int
    protected abstract val firstVisibleItemIndex: Int
    protected abstract val firstVisibleItemScrollOffset: Int
    protected abstract suspend fun scrollToItem(index: Int, offset: Int)

    val draggingItemLeft: Float
        get() = draggingLayoutInfo?.let { item ->
            (selected?.left ?: 0) + draggingDelta.x - item.left
        } ?: 0f

    val draggingItemTop: Float
        get() = draggingLayoutInfo?.let { item ->
            (selected?.top ?: 0) + draggingDelta.y - item.top
        } ?: 0f

    internal val interactions = Channel<StartDrag>()

    private val draggingLayoutInfo: T?
        get() = visibleItemsInfo.firstOrNull { it.itemIndex == draggingItemIndex }

    private var draggingDelta by mutableStateOf(Offset.Zero)
    private var selected by mutableStateOf<T?>(null)
    private val targets = mutableListOf<T>()
    private val distances = mutableListOf<Int>()

    internal open fun onDragStart(offsetX: Int, offsetY: Int): Boolean {
        val x = if (isVerticalScroll) offsetX else offsetX + viewportStartOffset
        val y = if (isVerticalScroll) offsetY + viewportStartOffset else offsetY
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
            scope.launch {
                dragCancelledAnimation.dragCancelled(position, offset)
            }
        }
        val startIndex = selected?.itemIndex
        val endIndex = draggingItemIndex
        selected = null
        draggingDelta = Offset.Zero
        draggingItemIndex = null
        onDragEnd?.apply {
            if (startIndex != null && endIndex != null) {
                invoke(startIndex, endIndex)
            }
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
            onMove(
                ItemPosition(draggingItem.itemIndex, draggingItem.itemKey),
                ItemPosition(targetItem.itemIndex, targetItem.itemKey),
            )
            draggingItemIndex = targetItem.itemIndex
        }
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
}

// ---------------------------------------------------------------------------
// Non-lazy (map) state: Row + horizontalScroll / Column + verticalScroll
// ---------------------------------------------------------------------------

/**
 * An item registered by [ReorderableListItem]. Coordinates are in the list's
 * visible space (same space pointer events arrive in), refreshed on every
 * layout change including scrolls.
 */
class ItemRect(
    val index: Int,
    val key: Any?,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
}

class ReorderableMapState(
    scope: CoroutineScope,
    onMove: (ItemPosition, ItemPosition) -> Unit,
    canDragOver: ((draggedOver: ItemPosition, dragging: ItemPosition) -> Boolean)?,
    onDragEnd: ((startIndex: Int, endIndex: Int) -> Unit)?,
    val orientation: Orientation,
    dragCancelledAnimation: DragCancelledAnimation,
) : ReorderableState<ItemRect>(scope, onMove, canDragOver, onDragEnd, dragCancelledAnimation) {
    private val rects = mutableMapOf<Any?, ItemRect>()

    internal fun registerItem(index: Int, key: Any?, left: Int, top: Int, width: Int, height: Int) {
        rects[key] = ItemRect(index, key, left, top, width, height)
    }

    internal fun clearItems() {
        rects.clear()
    }

    override val isVerticalScroll: Boolean get() = orientation == Orientation.Vertical
    override val ItemRect.left: Int get() = left
    override val ItemRect.top: Int get() = top
    override val ItemRect.right: Int get() = right
    override val ItemRect.bottom: Int get() = bottom
    override val ItemRect.width: Int get() = width
    override val ItemRect.height: Int get() = height
    override val ItemRect.itemIndex: Int get() = index
    override val ItemRect.itemKey: Any get() = key!!
    override val visibleItemsInfo: List<ItemRect> get() = rects.values.toList()
    override val viewportStartOffset: Int get() = 0
    override val viewportEndOffset: Int get() = Int.MAX_VALUE
    override val firstVisibleItemIndex: Int get() = -1
    override val firstVisibleItemScrollOffset: Int get() = 0
    override suspend fun scrollToItem(index: Int, offset: Int) {
        // ponytail: no edge auto-scroll — the queues are short, scroll manually.
    }
}

@Composable
fun rememberReorderableState(
    onMove: (ItemPosition, ItemPosition) -> Unit,
    orientation: Orientation = Orientation.Horizontal,
    canDragOver: ((draggedOver: ItemPosition, dragging: ItemPosition) -> Boolean)? = null,
    onDragEnd: ((startIndex: Int, endIndex: Int) -> Unit)? = null,
    dragCancelledAnimation: DragCancelledAnimation = SpringDragCancelledAnimation(),
): ReorderableMapState {
    val scope = rememberCoroutineScope()
    return remember(orientation) {
        ReorderableMapState(
            scope,
            onMove,
            canDragOver,
            onDragEnd,
            orientation,
            dragCancelledAnimation,
        )
    }
}

// ---------------------------------------------------------------------------
// Non-lazy item: drag translation + FLIP placement animation
// ---------------------------------------------------------------------------

/**
 * A queue row/card inside a plain (non-lazy) Row/Column. Registers the
 * item's visible-space rect with [state] and applies translations: the drag
 * offset while this card is dragged, the cancelled-drag offset while
 * springing back, or the component-driven FLIP offset otherwise.
 *
 * [container] must be the list's own [LayoutCoordinates] (the component
 * captures it via [onGloballyPositioned] on the scrollable Row/Column).
 * [flip] is owned by the component, keyed per item, and driven from data
 * changes — never from position callbacks.
 */
@Composable
fun ReorderableListItem(
    state: ReorderableMapState,
    index: Int,
    key: Any?,
    container: LayoutCoordinates?,
    flip: Animatable<Offset, *>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(isDragging: Boolean) -> Unit,
) {
    val isDragging = state.draggingItemIndex == index
    val cancel = state.dragCancelledAnimation
    val isCancelling = !isDragging && (cancel.position?.index == index || cancel.position?.key == key)

    Box(
        modifier =
            modifier
                .zIndex(if (isDragging || isCancelling) 1f else 0f)
                .graphicsLayer {
                    val t =
                        if (isDragging) {
                            if (state.isVerticalScroll) {
                                Offset(0f, state.draggingItemTop)
                            } else {
                                Offset(state.draggingItemLeft, 0f)
                            }
                        } else if (isCancelling) {
                            cancel.offset
                        } else {
                            flip.value
                        }
                    translationX = t.x
                    translationY = t.y
                }
                .onGloballyPositioned { coords ->
                    val c = container ?: return@onGloballyPositioned
                    // Visible space = same space pointer events arrive in.
                    val visual = coords.positionInRoot() - c.positionInRoot()
                    state.registerItem(
                        index,
                        key,
                        visual.x.toInt(),
                        visual.y.toInt(),
                        coords.size.width,
                        coords.size.height,
                    )
                },
    ) { content(isDragging) }
}

// ---------------------------------------------------------------------------
// Gestures: hold the dots, then drag
internal data class StartDrag(val id: PointerId)

/**
 * Attach to the list (the scrollable Row/Column). Blocks until a drag starts
 * via [detectReorder], then tracks the pointer directly and feeds deltas into
 * the [state].
 */
fun Modifier.reorderable(state: ReorderableMapState): Modifier =
    Modifier.pointerInput(Unit) {
        // forEachGesture's block receiver is PointerInputScope in this Compose
        // version.
        forEachGesture {
            val dragStart = state.interactions.receive()
            val down =
                awaitPointerEventScope {
                    currentEvent.changes.fastFirstOrNull { it.id == dragStart.id }
                }
            if (
                down != null &&
                state.onDragStart(down.position.x.toInt(), down.position.y.toInt())
            ) {
                trackDrag(down.id) { dx, dy -> state.onDrag(dx.toInt(), dy.toInt()) }
                state.onDragCanceled()
            }
        }
    }

/**
 * Attach to the drag handle (the dots). Holding the pointer still for the
 * system long-press duration starts the reorder drag; moving or releasing
 * first cancels it, so taps and list scrolling keep their normal behaviour.
 */
fun Modifier.detectReorder(state: ReorderableMapState): Modifier =
    Modifier.pointerInput(Unit) {
        forEachGesture {
            val down = awaitPointerEventScope { awaitFirstDown() }
            awaitLongPressOrCancellation(down)?.also {
                state.interactions.trySend(StartDrag(down.id))
            }
        }
    }

private suspend fun PointerInputScope.trackDrag(
    pointerId: PointerId,
    onDrag: (Float, Float) -> Unit,
) {
    awaitPointerEventScope {
        while (true) {
            val change =
                awaitPointerEvent().changes.fastFirstOrNull { it.id == pointerId }
                    ?: break
            change.consume()
            val delta = change.positionChange()
            if (delta != Offset.Zero) onDrag(delta.x, delta.y)
            if (change.changedToUpIgnoreConsumed()) break
        }
    }
}

/**
 * Ported from ComposeReorderable's own long-press detection, minus the
 * `isOutOfBounds` check (absent in this Compose version). Returns the
 * long-press change when the hold succeeds, null when the pointer moves
 * away, lifts, or gets consumed first.
 */
private suspend fun PointerInputScope.awaitLongPressOrCancellation(
    initialDown: PointerInputChange,
): PointerInputChange? {
    var longPress: PointerInputChange? = null
    var currentDown = initialDown
    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
    return try {
        withTimeout(longPressTimeout) {
            awaitPointerEventScope {
                var finished = false
                while (!finished) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.changes.fastAll { it.changedToUpIgnoreConsumed() }) {
                        finished = true // all pointers up
                    }
                    if (event.changes.fastAny { it.isConsumed }) {
                        finished = true // cancelled by another gesture
                    }
                    val consumeCheck = awaitPointerEvent(PointerEventPass.Final)
                    if (consumeCheck.changes.fastAny { it.isConsumed }) {
                        finished = true
                    }
                    if (!event.isPointerUp(currentDown.id)) {
                        longPress = event.changes.fastFirstOrNull { it.id == currentDown.id }
                    } else {
                        val newPressed = event.changes.fastFirstOrNull { it.pressed }
                        if (newPressed != null) {
                            currentDown = newPressed
                            longPress = currentDown
                        } else {
                            finished = true
                        }
                    }
                }
            }
        }
        null
    } catch (e: TimeoutCancellationException) {
        longPress
    }
}

private fun PointerEvent.isPointerUp(pointerId: PointerId): Boolean =
    changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
