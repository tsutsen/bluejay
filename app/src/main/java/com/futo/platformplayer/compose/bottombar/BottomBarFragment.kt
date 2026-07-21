package com.futo.platformplayer.compose.bottombar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.fragment.mainactivity.main.MainFragment
import com.futo.platformplayer.fragment.mainactivity.main.*
import kotlinx.coroutines.launch

/**
 * Compose-based bottom bar fragment — replaces MenuBottomBarFragment.
 *
 * Hosts the BottomBar composable and handles navigation to different tabs.
 * This fragment is hosted in the fragment_bottom_bar container in activity_main.xml.
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
                    val items = listOf(
                        BottomNavItem(0, "Home", Icons.Default.Home, true) { navigateToTab(0) },
                        BottomNavItem(1, "Subscriptions", Icons.Default.Subscriptions, false) { navigateToTab(1) },
                        BottomNavItem(2, "Creators", Icons.Default.People, false) { navigateToTab(2) },
                        BottomNavItem(3, "Sources", Icons.Default.Source, false) { navigateToTab(3) },
                        BottomNavItem(4, "Playlists", Icons.Default.PlaylistPlay, false) { navigateToTab(4) },
                        BottomNavItem(5, "History", Icons.Default.History, false) { navigateToTab(5) },
                        BottomNavItem(6, "Downloads", Icons.Default.Download, false) { navigateToTab(6) },
                        BottomNavItem(7, "Settings", Icons.Default.Settings, false) { navigateToTab(7) },
                    )
                    BottomBar(
                        items = items,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }.also { view ->
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
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

    /**
     * Handle back press — no more overlay in Compose version, always returns false.
     */
    fun onBackPressed(): Boolean = false

    /**
     * Check if we're at a tab root — always true for Compose bottom bar.
     */
    fun isAtTabRoot(): Boolean = true
}
