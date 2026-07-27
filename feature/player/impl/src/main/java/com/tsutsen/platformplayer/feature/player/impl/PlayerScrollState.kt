package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Scroll-driven state for the windowed player: height, nested scroll connection,
 * and the COMPACT detection threshold.
 *
 * Separated from PlayerScreen.kt so the orchestrator stays focused on state hoisting
 * and mode dispatch, while scroll math lives in its own file with clear boundaries.
 */
class PlayerScrollState(
    private val containerHeightPx: Float
) {
    lateinit var scrollState: LazyListState
        internal set

    val maxPlayerHeightPx: Float = containerHeightPx * 0.6f
    val minPlayerHeightPx: Float = containerHeightPx * 0.2f

    var playerHeightPx by mutableStateOf(0f)

    val nestedScrollConnection: NestedScrollConnection
        get() = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val previousHeight = playerHeightPx
                val consumed = when {
                    delta < 0f -> {
                        val newHeight = (previousHeight + delta).coerceIn(minPlayerHeightPx, maxPlayerHeightPx)
                        newHeight - previousHeight
                    }
                    delta > 0f &&
                        scrollState.firstVisibleItemIndex == 0 &&
                        scrollState.firstVisibleItemScrollOffset == 0 -> {
                        val newHeight = (previousHeight + delta).coerceIn(minPlayerHeightPx, maxPlayerHeightPx)
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

    /**
     * Whether the video box has been scrolled down enough to show only a slim control row
     * (COMPACT mode). Threshold: height <= 30% of container.
     */
    val isCollapsedControls: Boolean
        get() = containerHeightPx > 0f && (playerHeightPx / containerHeightPx) <= 0.3f
}

/**
 * Remember a [PlayerScrollState] tied to the given container height.
 * When the container height changes, a new state is created.
 */
@Composable
fun rememberPlayerScrollState(containerHeightPx: Float): PlayerScrollState {
    val state = remember(containerHeightPx) { PlayerScrollState(containerHeightPx) }
    state.scrollState = rememberLazyListState()
    return state
}
