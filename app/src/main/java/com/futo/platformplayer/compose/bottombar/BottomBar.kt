package com.futo.platformplayer.compose.bottombar

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom navigation item with icon and label.
 */
data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit
)

/**
 * Material3 bottom navigation bar — the official Android way.
 * Uses NavigationBar + NavigationBarItem from material3.
 * Handles theming, accessibility, and window insets automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomBar(
    items: List<BottomNavItem>,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        tonalElevation = NavigationBarDefaults.Elevation
    ) {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    androidx.compose.material3.Icon(
                        imageVector = if (item.selected) item.selectedIcon else item.icon,
                        contentDescription = item.label
                    )
                },
                label = { androidx.compose.material3.Text(item.label) },
                selected = item.selected,
                onClick = item.onClick
            )
        }
    }
}
