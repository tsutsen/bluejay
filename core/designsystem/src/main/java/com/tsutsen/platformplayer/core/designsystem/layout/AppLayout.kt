package com.tsutsen.platformplayer.core.designsystem.layout

import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import androidx.compose.animation.AnimatedVisibility
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.effectsSpec
import com.tsutsen.platformplayer.core.designsystem.theme.spatialSpec
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
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
        NavItemDef("notifications", Icons.Outlined.Notifications, Icons.Filled.Notifications, "Dash"),
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
                    PulseOnSelect(selected = item.key == currentDestination) {
                        Icon(
                            imageVector = if (item.key == currentDestination) item.selectedIcon else item.icon,
                            contentDescription = item.label,
                        )
                    }
                },
                // Alpha (not visibility) so the item height never reflows
                // while the rail fades in/out of fullscreen. maxLines=1 +
                // clip: while the rail shrinks around the morphing player,
                // labels must clip, never wrap to a second line.
                label = {
                    Text(
                        text = item.label,
                        modifier = Modifier.alpha(labelAlpha),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
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
                    PulseOnSelect(selected = item.key == currentDestination) {
                        Icon(
                            imageVector = if (item.key == currentDestination) item.selectedIcon else item.icon,
                            contentDescription = item.label,
                        )
                    }
                },
                // Alpha (not visibility) so the bar height never reflows
                // while the bar fades in/out of fullscreen. maxLines=1 +
                // clip: while the bar shrinks around the morphing player,
                // labels must clip, never wrap to a second line.
                label = {
                    Text(
                        text = item.label,
                        modifier = Modifier.alpha(labelAlpha),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                },
            )
        }
    }
}

/**
 * Scales a nav icon 1 → 1.06 → 1 when it becomes selected (the borrowed
 * "pill pulse"). Applied to the icon only — the keep-alive content layer
 * never slides, so tab persistence is untouched.
 */
@Composable
private fun PulseOnSelect(
    selected: Boolean,
    content: @Composable () -> Unit,
) {
    val pulse = remember { Animatable(1f) }
    val spec = spatialSpec<Float>()
    LaunchedEffect(selected) {
        if (selected) {
            pulse.animateTo(1.06f, spec)
            pulse.animateTo(1f, spec)
        }
    }
    Box(modifier = Modifier.graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }) {
        content()
    }
}

/**
 * Padding between the nav card edge and the nav items.
 */
private val NavSurfacePadH = Tokens.SpaceLg
private val NavSurfacePadV = Tokens.SpaceMd

/**
 * Vertical padding for the PORTRAIT bottom bar. Reduced from [NavSurfacePadV]
 * (the shared rail/card value) so the portrait bar is shorter. The portrait
 * bar is always a flat, full-width surface (no rounded floating card).
 */
private val PortraitNavPadV = 4.dp

/**
 * Corner radius that eases to 0 when [rounded] is false (edge flush with the
 * screen edge gets no rounding). The 300ms FastOutSlowIn spec matches the gap
 * animations exactly, so corners and size move in the same window — pass
 * `navMorphed` into the [rounded] decision (not the animated gap) so the
 * corner starts moving at t=0 when the morph begins, not when the gap
 * finally crosses zero.
 */
@Composable
// Clamped: the spatial spring overshoots past its 0.dp target when a
// corner squares off, and a negative radius is invalid.
private fun animatedCorner(rounded: Boolean, label: String): Dp =
    animateDpAsState(
        targetValue = if (rounded) BluejayTokens().radius.lg else 0.dp,
        animationSpec = spatialSpec<Dp>(),
        label = label,
    ).value.coerceAtLeast(0.dp)

/**
 * Surface behind the portrait bottom navigation bar.
 *
 * Always a flat, full-width rectangle — no rounded floating card in portrait
 * (this also means there are no corners to "restore" when the video closes).
 *
 * Native edge-to-edge: the background bleeds to the very bottom of the screen
 * (under the system gesture bar); only the CONTENT is inset by the system bar
 * inset. Insetting the whole bar (the old approach) left a gap at the bottom
 * where app content peeked through.
 */
@Composable
private fun NavigationBarSurface(
    @Suppress("UNUSED_PARAMETER") navMorphed: Boolean,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.systemBars.getBottom(density).toDp() }

    // Full-bleed background — the bar's bottom edge is the screen's bottom edge.
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // Inset only the content: horizontal card padding on the sides, the
        // system bar inset (plus a little) below so the items clear the gesture bar.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = NavSurfacePadH,
                        end = NavSurfacePadH,
                        top = PortraitNavPadV,
                        bottom = bottomInset + PortraitNavPadV,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            content()
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
            animationSpec = spatialSpec<Dp>(),
            label = "navRailGapTop",
        )
    val vBottom by
        animateDpAsState(
            targetValue = if (navMorphed) bottomInset else with(density) { verticalGapPx.toDp() },
            animationSpec = spatialSpec<Dp>(),
            label = "navRailGapBottom",
        )
    val hStart by
        animateDpAsState(
            targetValue = if (navMorphed) startInset else with(density) { horizontalGapPx.toDp() },
            animationSpec = spatialSpec<Dp>(),
            label = "navRailGapStart",
        )
    val hEnd by
        animateDpAsState(
            targetValue = if (navMorphed) endInset else with(density) { horizontalGapPx.toDp() },
            animationSpec = spatialSpec<Dp>(),
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
                    // Clamped: springs overshoot past 0 when an edge inset is 0
                    // (gesture bar / screen edge) — negative padding is fatal.
                    .padding(
                        top = vTop.coerceAtLeast(0.dp),
                        bottom = vBottom.coerceAtLeast(0.dp),
                        start = hStart.coerceAtLeast(0.dp),
                        end = hEnd.coerceAtLeast(0.dp),
                    )
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
    // content down to the real status-bar inset when the app chrome is
    // visible, and to 0 when the player goes fullscreen, in step with the
    // rail/300ms morph. The morphed nav rail stops at this same inset, so
    // the content and the nav share one top line.
    val density = LocalDensity.current
    val statusBarTop = with(density) { WindowInsets.systemBars.getTop(density).toDp() }
    val topInset by
        animateDpAsState(
            targetValue = if (config.showNavigation) statusBarTop else 0.dp,
            animationSpec = spatialSpec<Dp>(),
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
                    animationSpec = spatialSpec<Dp>(),
                    label = "navRailWidth",
                )
            val railAlpha by
                animateFloatAsState(
                    targetValue = if (config.showNavigation) 1f else 0f,
                    animationSpec = effectsSpec<Float>(),
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
                // Clamped: the spring overshoots below 0 when the player goes
                // fullscreen (inset target is 0) — negative padding is fatal.
                Box(modifier = Modifier.weight(1f).fillMaxSize().padding(top = topInset.coerceAtLeast(0.dp))) {
                    content()
                }
            }
        } else {
            // Portrait: Content + NavigationBar at bottom
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxSize().padding(top = topInset.coerceAtLeast(0.dp))) {
                    content()
                }
                AnimatedVisibility(
                    visible = config.showNavigation,
                    enter = fadeIn(animationSpec = effectsSpec<Float>()),
                    exit = fadeOut(animationSpec = effectsSpec<Float>()),
                ) {
                    NavigationBarSurface(navMorphed = navMorphed) {
                        navigationContent()
                    }
                }
            }
        }
    }
}
