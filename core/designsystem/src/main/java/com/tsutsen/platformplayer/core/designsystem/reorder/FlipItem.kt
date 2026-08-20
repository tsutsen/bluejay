/*
 * Queue FLIP helper: a Box that translates its content by a component-driven
 * Animatable. The component owns the per-item Animatable and drives it from
 * data changes (never from position callbacks — those feed back on the
 * animation itself and fire constantly while scrolling).
 */
package com.tsutsen.platformplayer.core.designsystem.reorder

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex

/**
 * Wraps a queue row/card and translates it by [flip]. While a slide is
 * running ([flip] is non-zero) the item is raised above its neighbours.
 */
@Composable
fun FlipItem(
    flip: Animatable<Offset, *>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .zIndex(if (flip.value != Offset.Zero) 1f else 0f)
                .graphicsLayer {
                    translationX = flip.value.x
                    translationY = flip.value.y
                },
    ) { content() }
}
