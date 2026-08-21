package com.tsutsen.platformplayer.core.designsystem.layout

import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
val bluejayNavItems =
    listOf(
        NavItemDef("home", Icons.Outlined.Home, Icons.Filled.Home, "Home"),
        NavItemDef("search", Icons.Outlined.Search, Icons.Filled.Search, "Search"),
        NavItemDef("subscriptions", Icons.Outlined.Subscriptions, Icons.Filled.Subscriptions, "Subs"),
        NavItemDef("library", Icons.Outlined.LibraryBooks, Icons.Filled.LibraryBooks, "Library"),
        NavItemDef("notifications", Icons.Outlined.Notifications, Icons.Filled.Notifications, "Feed"),
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
    labelAlpha: Float = 1f,
) {
    // Plain wrap-content Column (not M3 NavigationRail) — the parent
    // [NavigationRailSurface] in [AppLayout] centers it and draws the card.
    Column(
        modifier = Modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
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
                // Alpha (not visibility) so the item height never reflows
                // while the rail fades in/out of fullscreen.
                label = {
                    Text(
                        text = item.label,
                        modifier = Modifier.alpha(labelAlpha),
                    )
                },
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
    labelAlpha: Float = 1f,
) {
    // Plain Row hugging the items (M3's NavigationBar is fillMaxWidth
    // internally, which would prevent the floating surface from wrapping it
    // exactly). The surface is drawn by [NavigationBarSurface] in [AppLayout].
    Row(
        modifier = Modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                // Alpha (not visibility) so the bar height never reflows
                // while the bar fades in/out of fullscreen.
                label = {
                    Text(
                        text = item.label,
                        modifier = Modifier.alpha(labelAlpha),
                    )
                },
            )
        }
    }
}

/**
 * Padding between the nav card edge and the nav items.
 */
private val NavSurfacePadH = 16.dp
private val NavSurfacePadV = 12.dp

private val NavSurfaceCorner = 24.dp

/**
 * Corner radius that eases to 0 when [rounded] is false (edge flush with the
 * screen edge gets no rounding). The 300ms FastOutSlowIn spec matches the gap
 * animations exactly, so corners and size move in the same window — pass
 * `navMorphed` into the [rounded] decision (not the animated gap) so the
 * corner starts moving at t=0 when the morph begins, not when the gap
 * finally crosses zero.
 */
@Composable
private fun animatedCorner(rounded: Boolean, label: String): Dp =
    animateDpAsState(
        targetValue = if (rounded) NavSurfaceCorner else 0.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = label,
    ).value

/**
 * Surface behind the bottom navigation bar. Normally a rounded card hugging
 * the nav items with inner padding, floating above the bottom edge; morphs to
 * a flat full-width rectangle while the video page is in normal mode, stopping
 * at the bottom system inset so it never sits under the system bars. When
 * morphed every corner is squared; when shrunken, a corner is squared only
 * when it sits on a screen edge (zero gap).
 */
@Composable
private fun NavigationBarSurface(
    navMorphed: Boolean,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val containerWidthPx = remember { mutableIntStateOf(0) }
    val barWidthPx = remember { mutableIntStateOf(0) }
    val bottomInset = with(density) { WindowInsets.systemBars.getBottom(density).toDp() }
    val sideGapTargetPx =
        with(density) {
            if (navMorphed) {
                0f
            } else {
                val c = containerWidthPx.intValue
                val b = barWidthPx.intValue + NavSurfacePadH.toPx() * 2
                when {
                    c <= 0 || b <= 0 -> 8.dp.toPx()
                    b >= c -> 0f
                    else -> maxOf(8.dp.toPx(), (c - b) / 2f)
                }
            }
        }
    val sideGap by
        animateDpAsState(
            targetValue = with(density) { sideGapTargetPx.toDp() },
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "navBarSurfaceSideGap",
        )
    val bottomGap by
        animateDpAsState(
            targetValue = if (navMorphed) bottomInset else 8.dp + bottomInset,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "navBarSurfaceBottomGap",
        )
    // Morphed: fully flat rectangle. Shrunken: a corner is squared only when
    // it sits on a screen edge (zero gap). The bar's top edge never touches
    // the screen, so the top corners follow the side gaps; the bottom corners
    // also follow the bottom gap. (Dp is not Comparable in 1.11.x — compare
    // .value.)
    val sideOnEdge = sideGap.value <= 0.5f
    val bottomOnEdge = bottomGap.value <= 0.5f
    val topStart = animatedCorner(!sideOnEdge && !navMorphed, "navBarCornerTopStart")
    val topEnd = animatedCorner(!sideOnEdge && !navMorphed, "navBarCornerTopEnd")
    val bottomEnd = animatedCorner(!sideOnEdge && !bottomOnEdge && !navMorphed, "navBarCornerBottomEnd")
    val bottomStart = animatedCorner(!sideOnEdge && !bottomOnEdge && !navMorphed, "navBarCornerBottomStart")

    Box(
        modifier = Modifier.fillMaxWidth().onSizeChanged { containerWidthPx.intValue = it.width },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = sideGap, end = sideGap, bottom = bottomGap)
                    .clip(
                        RoundedCornerShape(
                            topStart = topStart,
                            topEnd = topEnd,
                            bottomEnd = bottomEnd,
                            bottomStart = bottomStart,
                        ),
                    )
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = NavSurfacePadH, vertical = NavSurfacePadV),
        ) {
            Box(modifier = Modifier.onSizeChanged { barWidthPx.intValue = it.width }) {
                content()
            }
        }
    }
}

/**
 * Surface behind the navigation rail. Normally a rounded card hugging the
 * rail items with inner padding (the start side hugs the screen edge, so its
 * corners are squared — D-shape); morphs to a flat full-height rectangle
 * while the video page is in normal mode, stopping at the system insets so it
 * never sits under the status bar. When morphed every corner is squared;
 * when shrunken, corners on a screen edge (zero gap) are squared.
 */
@Composable
private fun NavigationRailSurface(
    navMorphed: Boolean,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val containerHeightPx = remember { mutableIntStateOf(0) }
    val containerWidthPx = remember { mutableIntStateOf(0) }
    val columnHeightPx = remember { mutableIntStateOf(0) }
    val columnWidthPx = remember { mutableIntStateOf(0) }
    val insets = WindowInsets.systemBars
    val topInset = with(density) { insets.getTop(density).toDp() }
    val bottomInset = with(density) { insets.getBottom(density).toDp() }
    // 1.11.x has no getStart/getEnd — derive from left/right.
    val leftInset = with(density) { insets.getLeft(density, layoutDirection).toDp() }
    val rightInset = with(density) { insets.getRight(density, layoutDirection).toDp() }
    val startInset = if (layoutDirection == LayoutDirection.Ltr) leftInset else rightInset
    val endInset = if (layoutDirection == LayoutDirection.Ltr) rightInset else leftInset

    val verticalGapPx =
        with(density) {
            val c = containerHeightPx.intValue
            val b = columnHeightPx.intValue + NavSurfacePadV.toPx() * 2
            when {
                c <= 0 || b <= 0 -> 8.dp.toPx()
                b >= c -> 0f
                else -> maxOf(8.dp.toPx(), (c - b) / 2f)
            }
        }
    val horizontalGapPx =
        with(density) {
            val c = containerWidthPx.intValue
            val b = columnWidthPx.intValue + NavSurfacePadH.toPx() * 2
            if (c <= 0 || b <= 0 || b >= c) 0f else (c - b) / 2f
        }
    val vTop by
        animateDpAsState(
            targetValue = if (navMorphed) topInset else with(density) { verticalGapPx.toDp() },
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "navRailGapTop",
        )
    val vBottom by
        animateDpAsState(
            targetValue = if (navMorphed) bottomInset else with(density) { verticalGapPx.toDp() },
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "navRailGapBottom",
        )
    val hStart by
        animateDpAsState(
            targetValue = if (navMorphed) startInset else with(density) { horizontalGapPx.toDp() },
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "navRailGapStart",
        )
    val hEnd by
        animateDpAsState(
            targetValue = if (navMorphed) endInset else with(density) { horizontalGapPx.toDp() },
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "navRailGapEnd",
        )
    // Morphed: fully flat rectangle (all corners 0). Shrunken: the rail's
    // end (right) side never touches the screen edge — only its start, top
    // and bottom sides can — so end-side corners only square off via the
    // vertical gaps. (Dp is not Comparable in 1.11.x — compare .value.)
    val startOnEdge = hStart.value <= 0.5f
    val topOnEdge = vTop.value <= 0.5f
    val bottomOnEdge = vBottom.value <= 0.5f
    val topStart = animatedCorner(!startOnEdge && !topOnEdge && !navMorphed, "navRailCornerTopStart")
    val topEnd = animatedCorner(!topOnEdge && !navMorphed, "navRailCornerTopEnd")
    val bottomEnd = animatedCorner(!bottomOnEdge && !navMorphed, "navRailCornerBottomEnd")
    val bottomStart = animatedCorner(!startOnEdge && !bottomOnEdge && !navMorphed, "navRailCornerBottomStart")

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged {
                    containerHeightPx.intValue = it.height
                    containerWidthPx.intValue = it.width
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = vTop, bottom = vBottom, start = hStart, end = hEnd)
                    .clip(
                        RoundedCornerShape(
                            topStart = topStart,
                            topEnd = topEnd,
                            bottomEnd = bottomEnd,
                            bottomStart = bottomStart,
                        ),
                    )
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = NavSurfacePadH, vertical = NavSurfacePadV),
        ) {
            Box(
                modifier =
                    Modifier.onSizeChanged {
                        columnHeightPx.intValue = it.height
                        columnWidthPx.intValue = it.width
                    },
            ) {
                content()
            }
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
    labelsVisible: Boolean = true,
) {
    // Faded labels: out immediately when fullscreen engages, back only
    // after the rail/bar fade-in (300ms) from fullscreen has completed.
    val labelAlpha by
        animateFloatAsState(
            targetValue = if (labelsVisible) 1f else 0f,
            animationSpec = if (labelsVisible) tween(250, delayMillis = 300) else tween(250),
            label = "navLabelAlpha",
        )
    if (isWide) {
        AppNavigationRail(
            items = bluejayNavItems,
            currentDestination = currentDestination,
            onTabSelected = onTabSelected,
            labelAlpha = labelAlpha,
        )
    } else {
        AppNavigationBar(
            items = bluejayNavItems,
            currentDestination = currentDestination,
            onTabSelected = onTabSelected,
            labelAlpha = labelAlpha,
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
    navMorphed: Boolean = false,
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
                        NavigationRailSurface(navMorphed = navMorphed) {
                            navigationContent()
                        }
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
                    NavigationBarSurface(navMorphed = navMorphed) {
                        navigationContent()
                    }
                }
            }
        }
    }
}
