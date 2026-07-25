/*
 * Bluejay Navigation State
 *
 * Manages per-tab back stacks for the Compose navigation system.
 * Each top-level tab has its own NavBackStack.
 */

package com.tsutsen.platformplayer.compose.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * Create a navigation state with per-tab back stacks.
 *
 * @param startRoute The start route (default tab).
 * @param topLevelRoutes The set of top-level tab routes.
 */
@Composable
fun rememberGrayjayNavigationState(
    startRoute: NavKey = Home,
    topLevelRoutes: Set<NavKey> = setOf(Home, Subscriptions, Playlists, Notifications, Search, Settings)
): GrayjayNavigationState {

    val topLevelRoute = remember { mutableStateOf(startRoute) }

    val backStacks = topLevelRoutes.associateWith { key ->
        rememberNavBackStack(key)
    }

    return remember(startRoute, topLevelRoutes) {
        GrayjayNavigationState(
            startRoute = startRoute,
            topLevelRoute = topLevelRoute,
            backStacks = backStacks
        )
    }
}

/**
 * State holder for Bluejay navigation state.
 *
 * @property startRoute The start route.
 * @property topLevelRoute The current top-level route (tab).
 * @property backStacks The back stacks for each top-level route.
 */
class GrayjayNavigationState(
    val startRoute: NavKey,
    val topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>
) {
    /**
     * Returns the back stack for the current top-level route.
     */
    val currentBackStack: NavBackStack<NavKey>?
        get() = backStacks[topLevelRoute.value]

    /**
     * Returns the list of back stacks currently in use.
     */
    val stacksInUse: List<NavKey>
        get() = if (topLevelRoute.value == startRoute) {
            listOf(startRoute)
        } else {
            listOf(startRoute, topLevelRoute.value)
        }
}
