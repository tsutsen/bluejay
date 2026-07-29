package com.tsutsen.platformplayer.feature.player.impl

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.sqrt

private const val TAG = "PlayerGestures"

@Composable
fun PlayerGestures(
    modifier: Modifier,
    videoLayout: VideoLayout,
    miniProgress: Float,
    fullscreenProgress: Float,
    containerWidth: Float,
    containerHeight: Float,
    miniWidthPx: Float,
    miniHeightPx: Float,
    floatingRestX: Float,
    floatingRestY: Float,
    currentOffsetX: Float,
    currentOffsetY: Float,
    isDraggingMiniPlayer: Boolean,
    onDragStateChanged: (Boolean) -> Unit,
    onOffsetChanged: (x: Float, y: Float) -> Unit,
    gestureCallbacks: PlayerGestureCallbacks,
    onExpand: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedHoldStart: () -> Unit = {},
    onSpeedHoldEnd: () -> Unit = {}
) {
    val density = LocalDensity.current

    Box(modifier = modifier) {
        // ==================== FULLSCREEN mode gestures ====================
        if (fullscreenProgress > PlayerMorphConfig.Default.fullscreenSettledThreshold) {
            // Brightness/volume swipe
            var touchX = 0f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                touchX = it.x
                                gestureCallbacks.onVerticalDragStart(it.x)
                            },
                            onVerticalDrag = { _, dragAmount ->
                                gestureCallbacks.onVerticalDrag(touchX, dragAmount, containerWidth)
                            }
                        )
                    }
            )

            // 2x playback speed while held down
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                onSpeedHoldStart()
                            },
                            onDragEnd = {
                                onSpeedHoldEnd()
                            },
                            onDragCancel = {
                                onSpeedHoldEnd()
                            },
                            onDrag = { _, _ -> }
                        )
                    }
            )

            // Double-tap left/right thirds → rewind ±5 seconds
            val thirdWidthDp = with(density) { (containerWidth / 3).toDp() }
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Left third
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(thirdWidthDp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    onSeek(-5000)
                                }
                            )
                        }
                )
                // Right third
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(thirdWidthDp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    onSeek(5000)
                                }
                            )
                        }
                )
            }
        }

        // ==================== NORMAL/COMPACT mode gestures ====================
        if (fullscreenProgress < PlayerMorphConfig.Default.fullscreenSettledThreshold && miniProgress < PlayerMorphConfig.Default.miniDragThreshold) {
            // Double-tap left/right thirds → rewind ±5 seconds
            val thirdWidthDp = with(density) { (containerWidth / 3).toDp() }
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Left third
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(thirdWidthDp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    onSeek(-5000)
                                }
                            )
                        }
                )
                // Right third
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(thirdWidthDp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    onSeek(5000)
                                }
                            )
                        }
                )
            }

            // 2x playback speed while held down
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                onSpeedHoldStart()
                            },
                            onDragEnd = {
                                onSpeedHoldEnd()
                            },
                            onDragCancel = {
                                onSpeedHoldEnd()
                            },
                            onDrag = { _, _ -> }
                        )
                    }
            )
        }

        // ==================== FLOATING mode gestures ====================
        if (miniProgress > PlayerMorphConfig.Default.miniDragThreshold) {
            val latestOffsetX by rememberUpdatedState(currentOffsetX)
            val latestOffsetY by rememberUpdatedState(currentOffsetY)
            val latestRestX by rememberUpdatedState(floatingRestX)
            val latestRestY by rememberUpdatedState(floatingRestY)

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = videoLayout.offsetX.toInt(),
                            y = videoLayout.offsetY.toInt()
                        )
                    }
                    .size(
                        width = with(density) { videoLayout.widthPx.toDp() },
                        height = with(density) { videoLayout.heightPx.toDp() }
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                Log.d(TAG, "Mini tap → expand")
                                onExpand()
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        var localOffsetX = latestOffsetX
                        var localOffsetY = latestOffsetY

                        detectDragGestures(
                            onDragStart = {
                                Log.d(TAG, "Mini drag start")
                                onDragStateChanged(true)
                                localOffsetX = latestOffsetX
                                localOffsetY = latestOffsetY
                            },
                            onDrag = { change, dragAmount: Offset ->
                                change.consume()
                                localOffsetX += dragAmount.x
                                localOffsetY += dragAmount.y
                                onOffsetChanged(localOffsetX, localOffsetY)
                            },
                            onDragEnd = {
                                Log.d(TAG, "Mini drag end")
                                onDragStateChanged(false)
                                val edgeThreshold = 100f
                                val initialX = latestRestX
                                val initialY = latestRestY
                                val actualX = initialX + localOffsetX
                                val actualY = initialY + localOffsetY

                                var snappedX = localOffsetX
                                if (actualX < edgeThreshold) {
                                    snappedX = -initialX
                                } else if (actualX > containerWidth - miniWidthPx - edgeThreshold) {
                                    snappedX = (containerWidth - miniWidthPx) - initialX
                                }

                                var snappedY = localOffsetY
                                if (actualY < edgeThreshold) {
                                    snappedY = -initialY
                                } else if (actualY > containerHeight - miniHeightPx - edgeThreshold) {
                                    snappedY = (containerHeight - miniHeightPx) - initialY
                                }

                                onOffsetChanged(snappedX, snappedY)
                            },
                            onDragCancel = {
                                Log.d(TAG, "Mini drag cancel")
                                onDragStateChanged(false)
                            }
                        )
                    }
            )
        }
    }
}

