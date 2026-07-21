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
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.fragment.mainactivity.main.*
import com.futo.platformplayer.fragment.settings.SettingsHubFragment
import kotlinx.coroutines.launch

/**
 * Compose-based bottom bar fragment — replaces MenuBottomBarFragment.
 * Uses Material3 NavigationBar + NavigationBarItem for native look/feel.
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
                    val currentFragment = ma.fragCurrent

                    val items = listOf(
                        BottomNavItem(
                            "Home", Icons.Outlined.Home, Icons.Filled.Home,
                            currentFragment is com.futo.platformplayer.compose.feed.FeedFragment
                        ) { navigateToTab(0, ma) }
                        ,
                        BottomNavItem(
                            "Subscriptions", Icons.Outlined.Subscriptions, Icons.Filled.Subscriptions,
                            currentFragment is SubscriptionsFeedFragment
                        ) { navigateToTab(1, ma) }
                        ,
                        BottomNavItem(
                            "Creators", Icons.Outlined.People, Icons.Filled.People,
                            currentFragment is CreatorsFragment
                        ) { navigateToTab(2, ma) }
                        ,
                        BottomNavItem(
                            "Sources", Icons.Outlined.Source, Icons.Filled.Source,
                            currentFragment is SourcesFragment
                        ) { navigateToTab(3, ma) }
                        ,
                        BottomNavItem(
                            "Playlists", Icons.Outlined.PlaylistPlay, Icons.Filled.PlaylistPlay,
                            currentFragment is PlaylistsFragment
                        ) { navigateToTab(4, ma) }
                        ,
                        BottomNavItem(
                            "History", Icons.Outlined.History, Icons.Filled.History,
                            currentFragment is HistoryFragment
                        ) { navigateToTab(5, ma) }
                        ,
                        BottomNavItem(
                            "Downloads", Icons.Outlined.Download, Icons.Filled.Download,
                            currentFragment is DownloadsFragment
                        ) { navigateToTab(6, ma) }
                        ,
                        BottomNavItem(
                            "Settings", Icons.Outlined.Settings, Icons.Filled.Settings,
                            currentFragment is SettingsHubFragment
                        ) { navigateToTab(7, ma) }
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
            2 -> ma.navigate(ma._fragMainSubscriptions, null, true, false)
            3 -> ma.navigate(ma._fragMainSources, null, true, false)
            4 -> ma.navigate(ma._fragMainPlaylists, null, true, false)
            5 -> ma.navigate(ma._fragHistory, null, true, false)
            6 -> ma.navigate(ma._fragDownloads, null, true, false)
            7 -> ma.navigate(ma._fragSettingsHub, null, true, false)
        }
    }

    fun onBackPressed(): Boolean = false
    fun isAtTabRoot(): Boolean = true
}
