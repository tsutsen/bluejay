package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
fun GrayjayModalBottomSheet(
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
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                )
            }
            content()
        }
    }
}
