package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun TabsSection(
    showComments: Boolean,
    showRecommended: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (showComments) {
            TabItem(
                text = "Comments",
                isSelected = selectedTab == 0,
                onClick = { onTabSelected(0) },
            )
        }
        if (showRecommended) {
            TabItem(
                text = "Recommended",
                isSelected = selectedTab == 1,
                onClick = { onTabSelected(1) },
            )
        }
    }
}

@Composable
internal fun TabItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
