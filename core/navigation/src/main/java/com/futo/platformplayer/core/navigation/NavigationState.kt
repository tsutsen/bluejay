package com.futo.platformplayer.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStackEntry
import androidx.navigation3.runtime.NavKey

/**
 * Tracks the current navigation state for the app chrome.
 * Used by NavigationRail/NavigationBar to highlight the active tab.
 */
@Stable
class NavigationState {

    var topLevelRoute: NavKey? by mutableStateOf(null)
        internal set

    var currentBackStack: NavBackStackEntry? by mutableStateOf(null)
        internal set

    var startRoute: NavKey = NavDestination.Home
        internal set

    fun updateRoute(route: NavKey) {
        topLevelRoute = route
    }

    fun updateBackStack(entry: NavBackStackEntry?) {
        currentBackStack = entry
    }
}

@Composable
fun rememberNavigationState(): NavigationState {
    return remember { NavigationState() }
}
