package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.feature.player.impl.gesture.GestureAnimationConstants
import com.tsutsen.platformplayer.feature.player.impl.gesture.GestureIndicator
import kotlinx.coroutines.delay

/**
 * State for the centre text badge (seek, speed, etc).
 */
data class GestureBadgeState(
    val key: String = "",
    val label: String = "",
    val icon: ImageVector = Icons.Default.Replay10,
    val visible: Boolean = false,
    val keepAlive: Int = 0, // increments on every emission to force effect restart
)

/**
 * Unified gesture indicator overlay.
 *
 * Session-based: the container animates in/out once per badge session.
 * Content (label/icon) updates without animation during the session.
 * The hide timer resets on every emission, so hold badges stay visible.
 */
@Composable
internal fun GestureIndicatorOverlay(
    activeProgressIndicator: GestureIndicator.Progress?,
    badgeState: GestureBadgeState,
) {
    // Session tracks the current badge and whether it's visible.
    // Key changes → new session (fade in). Same key + visible → just update content + reset timer.
    data class Session(
        val key: String,
        val label: String,
        val icon: ImageVector,
        val visible: Boolean,
        val hideAt: Long,
    )

    var session by remember { mutableStateOf<Session?>(null) }

    // Process incoming badge state — keepAlive forces restart on every emission
    LaunchedEffect(badgeState.key, badgeState.keepAlive) {
        when {
            !badgeState.visible -> {
                // Hide request (from onIndicatorEnd)
                if (session?.visible == true) {
                    session = session?.copy(visible = false)
                }
            }
            session == null || session!!.key != badgeState.key || !session!!.visible -> {
                // New session: new key or was hidden
                session = Session(
                    key = badgeState.key,
                    label = badgeState.label,
                    icon = badgeState.icon,
                    visible = true,
                    hideAt = System.currentTimeMillis() + GestureAnimationConstants.INDICATOR_HIDE_DELAY_MS,
                )
            }
            else -> {
                // Same session, already visible: update content + reset hide timer
                session = session!!.copy(
                    label = badgeState.label,
                    icon = badgeState.icon,
                    hideAt = System.currentTimeMillis() + GestureAnimationConstants.INDICATOR_HIDE_DELAY_MS,
                )
            }
        }
    }

    // Auto-hide: when hideAt arrives and the session is still the same one, fade out
    LaunchedEffect(session?.hideAt) {
        val s = session ?: return@LaunchedEffect
        if (!s.visible) return@LaunchedEffect

        val waitMs = s.hideAt - System.currentTimeMillis()
        if (waitMs > 0) delay(waitMs)

        // Only fade out if this session is still current (no new emission intervened)
        if (session == s) {
            session = s.copy(visible = false)
        }
    }

    // Progress indicator (brightness/volume — side bars)
    activeProgressIndicator?.let { indicator ->
        val alignment = if (indicator.key == "brightness") Alignment.CenterStart else Alignment.CenterEnd
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = alignment) {
            Box(modifier = Modifier.fillMaxHeight().width(120.dp)) {
                ProgressIndicator(
                    value = indicator.value,
                    icon = indicator.icon,
                    format = indicator.format,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    // Centre text badge — AnimatedVisibility owns enter/exit, content recomposes freely
    AnimatedVisibility(
        visible = session?.visible == true,
        enter = fadeIn(animationSpec = tween(GestureAnimationConstants.INDICATOR_ANIM_MS)),
        exit = fadeOut(animationSpec = tween(GestureAnimationConstants.INDICATOR_ANIM_MS)),
    ) {
        val s = session!!
        Surface(
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = s.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = s.label,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun ProgressIndicator(
    value: Float,
    icon: ImageVector,
    format: (Float) -> String = { "${(it * 100).toInt()}%" },
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
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
                    .fillMaxHeight(value)
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = format(value),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
