package com.futo.platformplayer.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Tracks the current navigation state for the app chrome.
 * Used by NavigationRail/NavigationBar to highlight the active tab.
 */
@Stable
class NavigationState {

    var topLevelRoute: NavDestination? by mutableStateOf(null)
        internal set

    var startRoute: NavDestination = NavDestination.Home
        internal set

    fun updateRoute(route: NavDestination) {
        topLevelRoute = route
    }
}

@Composable
fun rememberNavigationState(): NavigationState {
    return remember { NavigationState() }
}
