package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import kotlin.math.roundToInt

/**
 * An equal-width segmented control: one pill that slides behind the
 * selected segment (same indicator pattern as [PillTabs]). For small,
 * fixed, mutually exclusive option sets (search type, quality mode).
 */
@Composable
fun <T> SegmentedGroup(
    options: List<T>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val n = options.size
    val totalPx = remember { mutableIntStateOf(0) }
    val motion = BluejayTokens().motion
    val segmentW = totalPx.value / n.toFloat()
    val x by
        animateFloatAsState(
            targetValue = selectedIndex * segmentW,
            animationSpec = motion.stateSpec<Float>(),
            label = "segX",
        )
    val w by
        animateFloatAsState(
            targetValue = segmentW,
            animationSpec = motion.stateSpec<Float>(),
            label = "segW",
        )

    Box(
        modifier =
            modifier
                .height(40.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .onSizeChanged { totalPx.value = it.width },
        contentAlignment = Alignment.Center,
    ) {
        if (w > 1f) {
            Box(
                modifier =
                    Modifier
                        .offset { IntOffset(x.roundToInt(), 0) }
                        .width(with(density) { w.toDp() })
                        .height(36.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.primaryContainer),
            )
        }
        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { i, option ->
                val selected = i == selectedIndex
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { onSelected(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
