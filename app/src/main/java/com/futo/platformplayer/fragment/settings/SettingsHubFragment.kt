package com.futo.platformplayer.fragment.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futo.platformplayer.fragment.mainactivity.main.MainFragment
import com.futo.platformplayer.fragment.settings.AppearanceSettingsFragment

/**
 * Settings Hub — top-level navigation for the new hierarchical Settings.
 * Replaces the old flat SettingsFragment with a proper category-based navigation.
 */
class SettingsHubFragment : MainFragment() {
    override val isMainView: Boolean = true
    override val isComposeMode: Boolean = true
    override val hasBottomBar: Boolean get() = true

    @Composable
    override fun ComposeContent() {
        SettingsHubScreen(
            onCategoryClick = { categoryId ->
                when (categoryId) {
                    "appearance" -> navigate(AppearanceSettingsFragment.newInstance())
                    else -> {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "$categoryId coming soon",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onNavigateBack = { navigateBack() }
        )
    }

    companion object {
        fun newInstance() = SettingsHubFragment().apply {}
    }
}

/**
 * Settings category definition.
 */
data class SettingsCategory(
    val id: String,
    val titleRes: String,
    val descriptionRes: String,
    val icon: @Composable () -> Unit
)

/**
 * Settings categories for the hub screen.
 */
private val settingsCategories = listOf(
    SettingsCategory(
        id = "appearance",
        titleRes = "Appearance",
        descriptionRes = "Theme, colors, typography, icons, contrast",
        icon = { Icon(Icons.Default.Palette, contentDescription = null) }
    ),
    SettingsCategory(
        id = "feed",
        titleRes = "Feed & Content",
        descriptionRes = "Home feed, search, channels, subscriptions",
        icon = { Icon(Icons.Default.Feed, contentDescription = null) }
    ),
    SettingsCategory(
        id = "player",
        titleRes = "Player",
        descriptionRes = "Playback, downloads, gestures, casting",
        icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
    ),
    SettingsCategory(
        id = "privacy",
        titleRes = "Privacy & Data",
        descriptionRes = "Privacy, data management, backup & restore",
        icon = { Icon(Icons.Default.Lock, contentDescription = null) }
    ),
    SettingsCategory(
        id = "sync",
        titleRes = "Sync & Identity",
        descriptionRes = "Synchronization, Polycentric",
        icon = { Icon(Icons.Default.Sync, contentDescription = null) }
    ),
    SettingsCategory(
        id = "general",
        titleRes = "General",
        descriptionRes = "Language, tabs, link handling, FAQ",
        icon = { Icon(Icons.Default.Settings, contentDescription = null) }
    ),
    SettingsCategory(
        id = "about",
        titleRes = "About",
        descriptionRes = "Version, license, payment",
        icon = { Icon(Icons.Default.Info, contentDescription = null) }
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHubScreen(
    onCategoryClick: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(settingsCategories, key = { it.id }) { category ->
                SettingsCategoryCard(category) { onCategoryClick(category.id) }
            }
        }
    }
}

@Composable
private fun SettingsCategoryCard(category: SettingsCategory, onNavigate: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigate)
            .padding(vertical = 2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                category.icon()
            }

            // Title and description
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = category.titleRes,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = category.descriptionRes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Chevron
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
