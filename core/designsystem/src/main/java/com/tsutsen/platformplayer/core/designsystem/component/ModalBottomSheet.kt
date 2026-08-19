package com.tsutsen.platformplayer.core.designsystem.component

import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Modal bottom sheet wrapper for player options, chapters, etc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluejayModalBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    // Sheets with several rows open in the partial state and clip their
    // bottom rows until the user drags — skip the partial state so the
    // sheet opens fully expanded (M3 1.4.0 bottom-sheet API).
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        // Scrollable so long sheets (e.g. many playlists) never clip their
        // bottom rows ("Go to channel" was cut off below the fold).
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = Tokens.SpaceLg, end = Tokens.SpaceLg, top = Tokens.SpaceSm, bottom = Tokens.SpaceSm),
                )
            }
            content()
        }
    }
}

/**
 * Animated bottom sheet for surfaces that can't host material3's
 * [ModalBottomSheet] (a Presentation can't spawn the child Popup window it
 * needs). Mirrors [BluejayModalBottomSheet]: a fading scrim, a rounded
 * surface that springs up from the bottom with a grab handle, tap the scrim
 * to dismiss. [progress] (0f..1f) drives both the slide and the scrim.
 */
@Composable
fun BluejayBottomSheetPanel(
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var containerHeight by remember { mutableIntStateOf(0) }
    var sheetHeight by remember { mutableIntStateOf(0) }

    // Spring up once the sheet has a measured height, so the off-screen start
    // offset is real (the pre-measure frame is pushed fully off-screen, which
    // avoids a one-frame flash at the rest position).
    LaunchedEffect(sheetHeight) {
        if (sheetHeight > 0) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(stiffness = 300f, dampingRatio = 0.85f),
            )
        }
    }

    val dismiss: () -> Unit = {
        scope.launch {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
            onDismiss()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { containerHeight = it.height },
    ) {
        // Scrim behind the sheet; tap to dismiss.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f * progress.value))
                    .clickable { dismiss() },
        )

        // The sheet, in front: slides up from the bottom edge.
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = if (containerHeight > 0) (containerHeight * 0.88f).dp else Dp.Infinity)
                    .background(MaterialTheme.colorScheme.surface)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .onSizeChanged { sheetHeight = it.height }
                    .offset(y = if (sheetHeight > 0) ((1f - progress.value) * sheetHeight).dp else 10_000.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
        ) {
            // Grab handle, matching material3's sheet handle.
            Box(
                modifier =
                    Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)),
            )
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier =
                        Modifier.padding(
                            start = Tokens.SpaceLg,
                            end = Tokens.SpaceLg,
                            top = Tokens.SpaceSm,
                            bottom = Tokens.SpaceSm,
                        ),
                )
            }
            content()
        }
    }
}
