package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.tsutsen.platformplayer.core.designsystem.theme.spatialSpec

/**
 * Expressive press feedback for custom button-like surfaces: the element
 * expands with a spring while pressed and returns on release — the same
 * physics M3's connected button groups use (their EnlargeOnPressElement is
 * internal, so custom surfaces replicate the pattern here).
 *
 * Use this in place of a bare [clickable] on tiles, swatches, and other
 * button-shaped elements. Do NOT apply to list rows, cards, or native M3
 * buttons — those are not buttons (rows/cards ripple only) or already
 * animate their own state.
 *
 * Owns the interaction source, so the scale tracks the press of the
 * clickable it returns. Place it before the visual modifiers (clip,
 * background) so the whole element scales as one.
 */
@Composable
fun Modifier.expressiveClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    pressScale: Float = 1.04f,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val value by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = spatialSpec<Float>(),
        label = "expressivePress",
    )
    val click =
        if (onLongClick != null) {
            Modifier.combinedClickable(
                enabled = enabled,
                onClick = onClick,
                interactionSource = source,
                onLongClick = {
                    onLongClick()
                    true
                },
            )
        } else {
            Modifier.clickable(
                enabled = enabled,
                onClick = onClick,
                interactionSource = source,
            )
        }
    return this.scale(value).then(click)
}
