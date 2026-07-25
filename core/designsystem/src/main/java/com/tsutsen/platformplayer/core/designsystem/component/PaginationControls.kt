package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Pagination controls with Previous/Page/Next buttons and batch loading.
 */
@Composable
fun PaginationControls(
    currentPage: Int,
    totalPages: Int,
    isLoading: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onPrevious,
            enabled = currentPage > 0 && !isLoading
        ) {
            Text("Previous")
        }

        Text(
            text = "Page ${currentPage + 1} of $totalPages",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        TextButton(
            onClick = onNext,
            enabled = currentPage < totalPages - 1 && !isLoading
        ) {
            Text("Next")
        }
    }
}
