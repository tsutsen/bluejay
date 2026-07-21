package com.futo.platformplayer.fragment.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import com.futo.platformplayer.compose.settings.SettingsOptionCard
import com.futo.platformplayer.compose.settings.SettingsScreen
import com.futo.platformplayer.fragment.mainactivity.main.MainFragment

/**
 * Settings Hub — top-level navigation for the new hierarchical Settings.
 * Each category opens a new SettingsFragment with its own items.
 */
class SettingsHubFragment : MainFragment() {
    override val isMainView: Boolean = true
    override val isComposeMode: Boolean = true
    override val hasBottomBar: Boolean get() = true

    @Composable
    override fun ComposeContent() {
        SettingsScreen(
            title = "Settings",
            onBack = { navigateBack() }
        ) {
            SettingsOptionCard(Icons.Default.Palette, "Appearance", "Theme, colors, typography, icons, contrast") {
                navigateTo("appearance")
            }
            SettingsOptionCard(Icons.Default.Feed, "Feed & Content", "Home feed, search, channels, subscriptions") {
                navigateTo("feed")
            }
            SettingsOptionCard(Icons.Default.PlayArrow, "Player", "Playback, downloads, gestures, casting") {
                navigateTo("player")
            }
            SettingsOptionCard(Icons.Default.Lock, "Privacy & Data", "Privacy, data management, backup & restore") {
                navigateTo("privacy")
            }
            SettingsOptionCard(Icons.Default.Sync, "Sync & Identity", "Synchronization, Polycentric") {
                navigateTo("sync")
            }
            SettingsOptionCard(Icons.Default.Settings, "General", "Language, tabs, link handling, FAQ") {
                navigateTo("general")
            }
            SettingsOptionCard(Icons.Default.Info, "About", "Version, license, payment") {
                navigateTo("about")
            }
        }
    }

    private fun navigateTo(category: String) {
        navigate(SettingsFragment.newInstance(category), null, true)
    }

    companion object {
        fun newInstance() = SettingsHubFragment().apply {}
    }
}
