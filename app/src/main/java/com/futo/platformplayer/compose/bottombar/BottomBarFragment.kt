package com.futo.platformplayer.compose.bottombar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.fragment.mainactivity.main.*
import com.futo.platformplayer.fragment.settings.SettingsHubFragment

/**
 * Compose-based adaptive bottom bar fragment — replaces MenuBottomBarFragment.
 * Uses Material3 NavigationSuiteScaffold for native adaptive behavior:
 * - Compact/medium: Bottom Navigation Bar
 * - Expanded: Navigation Rail
 */
class BottomBarFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setId(android.R.id.content)
            setContent {
                MaterialTheme {
                    val ma = activity as? MainActivity ?: return@MaterialTheme
                    val currentFragment by produceState<MainFragment?>(initialValue = null, ma) {
                        while (true) {
                            value = ma.fragCurrent
                            kotlinx.coroutines.delay(100)
                        }
                    }

                    val items = listOf(
                        BottomNavItem(
                            "Home", Icons.Outlined.Home, Icons.Filled.Home, 0,
                            onClick = { navigateToTab(0, ma) }
                        )
                        ,
                        BottomNavItem(
                            "Subscriptions", Icons.Outlined.Subscriptions, Icons.Filled.Subscriptions, 1,
                            onClick = { navigateToTab(1, ma) }
                        )
                        ,
                        BottomNavItem(
                            "Playlists", Icons.Outlined.PlaylistPlay, Icons.Filled.PlaylistPlay, 2,
                            onClick = { navigateToTab(2, ma) }
                        )
                        ,
                        BottomNavItem(
                            "History", Icons.Outlined.History, Icons.Filled.History, 3,
                            onClick = { navigateToTab(3, ma) }
                        )
                        ,
                        BottomNavItem(
                            "Downloads", Icons.Outlined.Download, Icons.Filled.Download, 4,
                            onClick = { navigateToTab(4, ma) }
                        )
                        ,
                        BottomNavItem(
                            "Settings", Icons.Outlined.Settings, Icons.Filled.Settings, 5,
                            onClick = { navigateToTab(5, ma) }
                        )
                    )

                    BottomBar(items = items)
                }
            }
        }.also { view ->
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun navigateToTab(tabId: Int, ma: MainActivity) {
        when (tabId) {
            0 -> ma.navigate(ma._fragMainHome as com.futo.platformplayer.fragment.mainactivity.main.MainFragment, null, true, false)
            1 -> ma.navigate(ma._fragMainSubscriptionsFeed, null, true, false)
            2 -> ma.navigate(ma._fragMainPlaylists, null, true, false)
            3 -> ma.navigate(ma._fragHistory, null, true, false)
            4 -> ma.navigate(ma._fragDownloads, null, true, false)
            5 -> ma.navigate(ma._fragSettingsHub, null, true, false)
        }
    }

    fun onBackPressed(): Boolean = false
    fun isAtTabRoot(): Boolean = true
}
