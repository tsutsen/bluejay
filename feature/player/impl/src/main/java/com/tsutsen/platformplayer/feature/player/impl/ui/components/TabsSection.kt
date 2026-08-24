package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.component.PillTabs

@Composable
internal fun TabsSection(
    showComments: Boolean,
    showRecommended: Boolean,
    isLive: Boolean = false,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    val tabs =
        buildList {
            if (showComments) add(0 to if (isLive) "Live chat" else "Comments")
            if (showRecommended) add(1 to "Recommended")
        }
    if (tabs.isEmpty()) return
    val selectedPill = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0)
    PillTabs(
        labels = tabs.map { it.second },
        selected = selectedPill,
        onSelect = { pillIndex -> onTabSelected(tabs[pillIndex].first) },
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
