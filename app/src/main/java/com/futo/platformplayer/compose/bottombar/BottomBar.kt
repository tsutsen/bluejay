package com.futo.platformplayer.compose.bottombar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.futo.platformplayer.R

/**
 * Bottom navigation item definition.
 */
data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val index: Int,
    val onClick: () -> Unit
)

/**
 * Adaptive navigation suite — automatically switches between:
 * - Bottom Navigation Bar (compact/medium windows)
 * - Navigation Rail (expanded windows)
 * - Navigation Drawer (expanded windows with many items)
 *
 * Uses Material3 NavigationSuiteScaffold for native adaptive behavior.
 */
@Composable
fun BottomBar(
    items: List<BottomNavItem>,
    modifier: Modifier = Modifier
) {
    var selectedIndex by mutableIntStateOf(0)

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            items.forEach { item ->
                item(
                    icon = {
                        Icon(
                            imageVector = if (item.index == selectedIndex) item.selectedIcon else item.icon,
                            contentDescription = item.label
                        )
                    },
                    label = { Text(item.label) },
                    selected = item.index == selectedIndex,
                    onClick = {
                        selectedIndex = item.index
                        item.onClick()
                    }
                )
            }
        }
    ) {
        // Content area — empty, navigation handled by fragments
    }
}
