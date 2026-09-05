package com.tsutsen.platformplayer.core.designsystem.component

import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Modal bottom sheet wrapper for player options, chapters, etc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluejayModalBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    // false for lazily scrolling content (e.g. the queue list) — a
    // LazyColumn can't nest inside a verticalScroll ancestor. Must stay
    // before [content]: a trailing lambda needs the last param to be a
    // function type.
    scroll: Boolean = true,
    // Optional right-aligned action in the title row (e.g. "Reset to
    // defaults").
    headerAction: (@Composable () -> Unit)? = null,
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
                    .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(bottom = 32.dp),
        ) {
            if (title != null || headerAction != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = Tokens.SpaceLg,
                                end = Tokens.SpaceLg,
                                top = Tokens.SpaceSm,
                                bottom = Tokens.SpaceSm,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    headerAction?.invoke()
                }
            }
            content()
        }
    }
}


