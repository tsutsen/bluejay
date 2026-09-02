package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.designsystem.theme.spatialSpec
import kotlin.math.roundToInt

/**
 * A row of text tabs with a single rounded "pill" that slides behind the
 * selected tab. Tabs are laid out adjacent (no gap) so the pill glides from
 * one to the next. Used by the main player's Comments/Recommended tabs and the
 * secondary-screen Comments/Chapters/Recommended tabs.
 */
@Composable
fun PillTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val n = labels.size
    // Observable sizes: `onSizeChanged` writes into them, which triggers a
    // recomposition so the pill appears as soon as the first layout pass
    // measures the tabs (no tap required).
    val tabSizes = remember { List(n) { mutableStateOf(0 to 0) } }
    // Horizontal scroll: with more tabs than fit (e.g. the second screen's
    // six), the strip scrolls instead of wrapping labels onto extra lines.
    val scrollState = rememberScrollState()

    // Tabs are adjacent, so a tab's x-offset is the running sum of the
    // widths of the tabs before it.
    val sel = selected.coerceIn(0, (n - 1).coerceAtLeast(0))
    var px = 0
    val tabX = IntArray(n)
    for (i in 0 until n) {
        tabX[i] = px
        px += tabSizes[i].value.first
    }
    val targetX = if (n > 0) tabX[sel].toFloat() else 0f
    val targetW = if (n > 0) tabSizes[sel].value.first.toFloat() else 0f
    val targetH = tabSizes.maxOfOrNull { it.value.second }?.toFloat() ?: 0f
    // Read in the body (not the layout lambda) so scroll updates
    // recompose and re-offset the pill.
    val scrollX = scrollState.value

    val pillX by animateFloatAsState(targetValue = targetX, animationSpec = spatialSpec<Float>(), label = "pillX")
    val pillW by animateFloatAsState(targetValue = targetW, animationSpec = spatialSpec<Float>(), label = "pillW")
    val pillH by animateFloatAsState(targetValue = targetH, animationSpec = spatialSpec<Float>(), label = "pillH")

    Box(modifier) {
        // The pill (drawn first, so it sits behind the tab labels). The
        // Row scrolls but the pill doesn't, so shift by the scroll offset.
        if (pillW > 1f) {
            Box(
                modifier =
                    Modifier
                        .offset { IntOffset((pillX - scrollX).roundToInt(), 0) }
                        .width(with(density) { pillW.toDp() })
                        .height(with(density) { pillH.toDp() })
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(percent = 50),
                        ),
            )
        }
        Row(modifier = Modifier.horizontalScroll(scrollState)) {
            labels.forEachIndexed { i, label ->
                Box(
                    modifier =
                        Modifier
                            .clickable { onSelect(i) }
                            .onSizeChanged { tabSizes[i].value = it.width to it.height }
                            .padding(horizontal = Tokens.SpaceLg, vertical = Tokens.SpaceSm),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color =
                            if (i == selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        fontWeight =
                            if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
