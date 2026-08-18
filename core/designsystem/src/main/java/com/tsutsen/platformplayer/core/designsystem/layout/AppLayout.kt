package com.tsutsen.platformplayer.core.designsystem.layout

import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
 * Width of the navigation rail. Shared with [AppLayout]'s inset animation so the
 * fullscreen player morph can ease against the same value.
 */
val AppNavigationRailWidth = 80.dp

/**
 * The single global top inset applied by [AppLayout] to all content. Screens
 * never add their own status-bar compensation on top of this.
 */
val AppContentTopInset = 28.dp

/** Height of [AppHeader]. */
val AppHeaderHeight = 56.dp

/**
 * Top padding for a tab screen's first content element when the tab does NOT
 * start with an [AppHeader].
 *
 * Convention: every tab places its first visible element on the same line —
 * 42dp below the top of the screen. An [AppHeader] does this by construction
 * (its centered `titleLarge` line, 28sp tall, starts at
 * `AppContentTopInset + (AppHeaderHeight - 28) / 2` = 42dp), so header tabs
 * add nothing. Content tabs (Home, Search, Subscriptions, Library) add this
 * `TabContentTopPadding = (AppHeaderHeight - 28) / 2 = 14dp` above their
 * first element to land on the same line. Do not change one without the
 * other.
 */
val TabContentTopPadding = 14.dp

/**
 * Flat top header shared by every screen (bottom-bar tabs and detail pages).
 * Its centered title sits on the shared tab content line (see
 * [TabContentTopPadding]), so screens add no top padding of their own.
 */
@Composable
fun AppHeader(
    title: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AppHeaderHeight)
                .padding(horizontal = Tokens.SpaceLg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Row(modifier = Modifier.weight(1f)) {
            title()
        }
        actions()
    }
}

/**
 * Navigation rail chrome for landscape/wide layouts.
 */
@Composable
fun AppNavigationRail(
    items: List<NavItemDef>,
    currentDestination: String?,
    onTabSelected: (String) -> Unit,
) {
    // Plain Column (not M3 NavigationRail) so the item group centers
    // vertically in the full-height rail instead of stacking from the top.
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
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
    // Status-bar offset for the content area (edge-to-edge window): ease the
    // content down 28dp when the app chrome is visible, and to 0 when the
    // player goes fullscreen, in step with the rail/300ms morph.
    val topInset by
        animateDpAsState(
            targetValue = if (config.showNavigation) AppContentTopInset else 0.dp,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "appContentTopInset",
        )

    Box(modifier = modifier.fillMaxSize()) {
        if (config.isWide) {
            // Landscape: NavigationRail on left + content.
            // The rail's width/alpha ease (instead of AnimatedVisibility) so the
            // content edge — and the player morphing to fullscreen — moves with
            // the same 300ms tween rather than jumping when the rail is removed.
            val railWidth by
                animateDpAsState(
                    targetValue = if (config.showNavigation) AppNavigationRailWidth else 0.dp,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "navRailWidth",
                )
            val railAlpha by
                animateFloatAsState(
                    targetValue = if (config.showNavigation) 1f else 0f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "navRailAlpha",
                )
            Row(modifier = Modifier.fillMaxSize()) {
                if (config.showNavigation || railWidth > 0.dp) {
                    Box(
                        modifier =
                            Modifier
                                .width(railWidth)
                                .graphicsLayer { alpha = railAlpha },
                    ) {
                        navigationContent()
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxSize().padding(top = topInset)) {
                    content()
                }
            }
        } else {
            // Portrait: Content + NavigationBar at bottom
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxSize().padding(top = topInset)) {
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
}
