package com.futo.platformplayer.core.designsystem.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import com.futo.platformplayer.core.designsystem.icons.GrayjayIcons
import com.futo.platformplayer.core.designsystem.icons.IconStyle

/**
 * Orientation-aware layout that switches between NavigationRail (landscape)
 * and NavigationBar (portrait) based on window width.
 */
data class AppLayoutConfig(
    val iconStyle: IconStyle = IconStyle.ROUNDED,
    val isWide: Boolean = false
)

@Composable
fun rememberAppLayoutConfig(): AppLayoutConfig {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isWide = adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM ||
                  adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
    return AppLayoutConfig(isWide = isWide)
}

/**
 * Main app layout composable.
 * Hosts the navigation chrome and content area.
 */
@Composable
fun AppLayout(
    config: AppLayoutConfig = rememberAppLayoutConfig(),
    navigationContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    if (config.isWide) {
        // Landscape: NavigationRail on left + content
        Row(modifier = modifier.fillMaxSize()) {
            navigationContent()
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                content()
            }
        }
    } else {
        // Portrait: Content + NavigationBar at bottom
        Column(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                content()
            }
            navigationContent()
        }
    }
}
