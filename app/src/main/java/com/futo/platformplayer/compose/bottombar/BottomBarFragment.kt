package com.futo.platformplayer.compose.bottombar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.fragment.mainactivity.main.MainFragment
import com.futo.platformplayer.fragment.mainactivity.main.*
import com.futo.platformplayer.fragment.settings.SettingsHubFragment
import kotlinx.coroutines.launch

/**
 * Compose-based bottom bar fragment — replaces MenuBottomBarFragment.
 *
 * Hosts the BottomBar composable and handles navigation to different tabs.
 * Tracks the active tab based on which fragment is currently shown.
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
                val ma = activity as? MainActivity ?: return@setContent
                val currentFragment = ma.fragCurrent

                val items = listOf(
                    BottomNavItem(0, "Home", Icons.Default.Home, isTabActive(currentFragment, 0)) { navigateToTab(0) },
                    BottomNavItem(1, "Subscriptions", Icons.Default.Subscriptions, isTabActive(currentFragment, 1)) { navigateToTab(1) },
                    BottomNavItem(2, "Creators", Icons.Default.People, isTabActive(currentFragment, 2)) { navigateToTab(2) },
                    BottomNavItem(3, "Sources", Icons.Default.Source, isTabActive(currentFragment, 3)) { navigateToTab(3) },
                    BottomNavItem(4, "Playlists", Icons.Default.PlaylistPlay, isTabActive(currentFragment, 4)) { navigateToTab(4) },
                    BottomNavItem(5, "History", Icons.Default.History, isTabActive(currentFragment, 5)) { navigateToTab(5) },
                    BottomNavItem(6, "Downloads", Icons.Default.Download, isTabActive(currentFragment, 6)) { navigateToTab(6) },
                    BottomNavItem(7, "Settings", Icons.Default.Settings, isTabActive(currentFragment, 7)) { navigateToTab(7) },
                )
                BottomBar(
                    items = items,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }.also { view ->
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun isTabActive(currentFragment: MainFragment?, tabId: Int): Boolean {
        return when (tabId) {
            0 -> currentFragment is com.futo.platformplayer.compose.feed.FeedFragment
            1 -> currentFragment is SubscriptionsFeedFragment
            2 -> currentFragment is CreatorsFragment
            3 -> currentFragment is SourcesFragment
            4 -> currentFragment is PlaylistsFragment
            5 -> currentFragment is HistoryFragment
            6 -> currentFragment is DownloadsFragment
            7 -> currentFragment is SettingsHubFragment
            else -> false
        }
    }

    private fun navigateToTab(tabId: Int) {
        val ma = activity as? MainActivity ?: return
        when (tabId) {
            0 -> ma.navigate(ma._fragMainHome as MainFragment, null, true, false)
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
