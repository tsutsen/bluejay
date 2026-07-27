package com.tsutsen.platformplayer.feature.player.impl.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Resolved video geometry for the morph transition. All values are in pixels except
 * [cornerRadius] which is in Dp (a fixed 0 or 12.dp anchor, not density-dependent).
 *
 * This is a pure function of the animation progresses and anchor values — no Compose
 * state, no side effects. Unit-testable without a Compose runtime.
 */
data class VideoLayout(
    val widthPx: Float,
    val heightPx: Float,
    val offsetX: Float,
    val offsetY: Float,
    val cornerRadius: Dp
)

/**
 * Compute the current video rect from two continuous progress values:
 *
 * - `miniProgress` ∈ [0,1]: 0 = NORMAL, 1 = FLOATING (mini player anchored to bottom-end)
 * - `fullscreenProgress` ∈ [0,1]: 0 = NORMAL, 1 = FULLSCREEN (video fills container)
 *
 * The interpolation uses nested lerp: first between NORMAL↔FULLSCREEN by fullscreenProgress,
 * then between that intermediate and FLOATING by miniProgress. This is an approximation
 * (the path through NORMAL as an implicit third anchor) — acceptable for phase 1 since
 * fullscreenProgress is held at 0 during NORMAL↔FLOATING transitions. Validate the sag
 * during MINI↔FULLSCREEN before shipping.
 *
 * @param floatingRestX Absolute X of the mini player's resting position (BottomEnd + 16dp padding).
 *   Computed as `containerWidth - miniWidthPx - 16.dp.toPx()`.
 * @param floatingRestY Absolute Y of the mini player's resting position.
 *   Computed as `containerHeight - miniHeightPx - 16.dp.toPx()`.
 * @param dragOffsetX/Y Additional offset from user drag, added on top of the resting position.
 */
fun computeVideoLayout(
    miniProgress: Float,
    fullscreenProgress: Float,
    containerWidth: Float,
    containerHeight: Float,
    playerHeightPx: Float,
    miniWidthPx: Float,
    miniHeightPx: Float,
    floatingRestX: Float,
    floatingRestY: Float,
    dragOffsetX: Float,
    dragOffsetY: Float
): VideoLayout {
    // Three anchor positions
    val normalWidth = containerWidth
    val normalHeight = playerHeightPx.coerceAtLeast(0f)
    val normalX = 0f
    val normalY = 0f

    val floatingWidth = miniWidthPx
    val floatingHeight = miniHeightPx
    val floatingX = floatingRestX + dragOffsetX
    val floatingY = floatingRestY + dragOffsetY

    val fullscreenWidth = containerWidth
    val fullscreenHeight = containerHeight
    val fullscreenX = 0f
    val fullscreenY = 0f

    // Nested lerp: NORMAL→FULLSCREEN first, then result→FLOATING
    val intermediateWidth = lerp(normalWidth, fullscreenWidth, fullscreenProgress)
    val intermediateHeight = lerp(normalHeight, fullscreenHeight, fullscreenProgress)
    val intermediateX = lerp(normalX, fullscreenX, fullscreenProgress)
    val intermediateY = lerp(normalY, fullscreenY, fullscreenProgress)

    val width = lerp(intermediateWidth, floatingWidth, miniProgress)
    val height = lerp(intermediateHeight, floatingHeight, miniProgress)
    val offsetX = lerp(intermediateX, floatingX, miniProgress)
    val offsetY = lerp(intermediateY, floatingY, miniProgress)

    // Corner radius: 0 in NORMAL/FULLSCREEN, 12.dp in FLOATING
    val cornerRadius = lerp(0f, 12f, miniProgress).dp

    return VideoLayout(
        widthPx = width,
        heightPx = height,
        offsetX = offsetX,
        offsetY = offsetY,
        cornerRadius = cornerRadius
    )
}

internal fun lerp(a: Float, b: Float, t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    return a + (b - a) * clamped
}
