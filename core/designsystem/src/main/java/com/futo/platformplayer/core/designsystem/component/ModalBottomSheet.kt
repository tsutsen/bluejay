package com.futo.platformplayer.core.designsystem.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Modal bottom sheet wrapper for player options, chapters, etc.
 */
@Composable
fun GrayjayModalBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                ModalBottomSheetDefaults.DragHandle()
            }
        }
    ) {
        Column(
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            content()
        }
    }
}
