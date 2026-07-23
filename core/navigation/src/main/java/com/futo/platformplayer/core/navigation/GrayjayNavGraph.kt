package com.futo.platformplayer.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay

/**
 * NavHost configuration for Grayjay.
 * Registers all composable destinations and provides entry providers.
 *
 * Note: Full implementation will be in Phase 1 when feature modules are populated.
 * This provides the structure; individual scenes are implemented per feature.
 */
@Composable
fun GrayjayNavGraph(
    navigator: Navigator,
    startDestination: NavKey = NavDestination.Home,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    // NavGraph registration placeholder — scenes will be wired in subsequent phases
    // Each feature module will register its scenes here
}

/**
 * Entry provider function type for NavDisplay.
 */
typealias EntryProvider = (NavKey) -> NavEntry<NavKey>
