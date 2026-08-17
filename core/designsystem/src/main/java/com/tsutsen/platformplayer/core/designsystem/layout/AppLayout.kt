package com.tsutsen.platformplayer.core.designsystem.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass

/**
 * Configuration for the app layout.
 */
data class AppLayoutConfig(
    val isWide: Boolean = false,
    val showNavigation: Boolean = true,
)

/**
 * Remember the app layout config based on current window size.
 */
@Composable
fun rememberAppLayoutConfig(): AppLayoutConfig {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isWide =
        adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM ||
            adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
    return AppLayoutConfig(isWide = isWide)
}

/**
 * Navigation item definition for the app chrome.
 */
data class NavItemDef(
    val key: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: String,
)

/**
 * Standard navigation items for the app chrome.
 */
val grayjayNavItems =
    listOf(
        NavItemDef("home", Icons.Outlined.Home, Icons.Filled.Home, "Home"),
        NavItemDef("search", Icons.Outlined.Search, Icons.Filled.Search, "Search"),
        NavItemDef("subscriptions", Icons.Outlined.Subscriptions, Icons.Filled.Subscriptions, "Subscriptions"),
        NavItemDef("library", Icons.Outlined.LibraryBooks, Icons.Filled.LibraryBooks, "Library"),
        NavItemDef("notifications", Icons.Outlined.Notifications, Icons.Filled.Notifications, "Notifications"),
        NavItemDef("settings", Icons.Outlined.Settings, Icons.Filled.Settings, "Settings"),
    )

/**
 * Navigation rail chrome for landscape/wide layouts.
 */
@Composable
fun AppNavigationRail(
    items: List<NavItemDef>,
    currentDestination: String?,
    onTabSelected: (String) -> Unit,
) {
    NavigationRail(
        modifier =
            Modifier
                .width(80.dp)
                .statusBarsPadding(),
    ) {
        items.forEach { item ->
            NavigationRailItem(
                selected = item.key == currentDestination,
                onClick = { onTabSelected(item.key) },
                icon = {
                    Icon(
                        imageVector = if (item.key == currentDestination) item.selectedIcon else item.icon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label) },
            )
        }
    }
}

/**
 * Navigation bar chrome for portrait/narrow layouts.
 */
@Composable
fun AppNavigationBar(
    items: List<NavItemDef>,
    currentDestination: String?,
    onTabSelected: (String) -> Unit,
) {
    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.key == currentDestination,
                onClick = { onTabSelected(item.key) },
                icon = {
                    Icon(
                        imageVector = if (item.key == currentDestination) item.selectedIcon else item.icon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label) },
            )
        }
    }
}

/**
 * App navigation chrome that switches between rail and bar based on orientation.
 */
@Composable
fun AppNavigationChrome(
    currentDestination: String?,
    onTabSelected: (String) -> Unit,
    isWide: Boolean =
        currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM ||
            currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED,
) {
    if (isWide) {
        AppNavigationRail(
            items = grayjayNavItems,
            currentDestination = currentDestination,
            onTabSelected = onTabSelected,
        )
    } else {
        AppNavigationBar(
            items = grayjayNavItems,
            currentDestination = currentDestination,
            onTabSelected = onTabSelected,
        )
    }
}

/**
 * Main app layout composable.
 * Hosts the navigation chrome and content area.
 * Switches between NavigationRail (landscape) and NavigationBar (portrait).
 */
@Composable
fun AppLayout(
    config: AppLayoutConfig = rememberAppLayoutConfig(),
    navigationContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (config.isWide) {
        // Landscape: NavigationRail on left + content
        Row(modifier = modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = config.showNavigation,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)),
            ) {
                navigationContent()
            }
            // Status-bar inset: MainActivity is edge-to-edge, so the content
            // area must start below the status bar (all screens rely on this).
            Box(modifier = Modifier.weight(1f).fillMaxSize().statusBarsPadding()) {
                content()
            }
        }
    } else {
        // Portrait: Content + NavigationBar at bottom
        Column(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxSize().statusBarsPadding()) {
                content()
            }
            AnimatedVisibility(
                visible = config.showNavigation,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)),
            ) {
                navigationContent()
            }
        }
    }
}
