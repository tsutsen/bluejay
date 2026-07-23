package com.futo.platformplayer.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shimmer loading skeleton animation.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    width: Dp = 320.dp,
    height: Dp = 180.dp,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp)
) {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value - 200f, translateAnim.value - 200f),
        end = Offset(translateAnim.value, translateAnim.value)
    )

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(brush)
    )
}

/**
 * Shimmer loading skeleton for a list of video cards.
 */
@Composable
fun VideoCardSkeleton(
    count: Int = 6,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        repeat(count) {
            ShimmerBox(
                width = 320.dp,
                height = 180.dp,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBox(
                width = 280.dp,
                height = 16.dp,
                shape = RoundedCornerShape(4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            ShimmerBox(
                width = 200.dp,
                height = 12.dp,
                shape = RoundedCornerShape(4.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
