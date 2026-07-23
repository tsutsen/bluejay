package com.futo.platformplayer.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reusable filter chip component.
 */
@Composable
fun GrayjayFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        },
        modifier = modifier.padding(horizontal = 4.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}
