package com.futo.platformplayer.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Reusable UI components for settings-style screens.
 *
 * These components ensure visual consistency across all settings screens
 * in the app. Import this file in any settings fragment to use them.
 *
 * Usage in a fragment:
 *   @Composable override fun ComposeContent() {
 *       SettingsScreen(title = "Appearance", onBack = { navigateBack() }) {
 *           SettingsSection("Display")
 *           SettingsOptionCard(
 *               icon = Icons.Default.BrightnessAuto,
 *               title = "Theme",
 *               subtitle = "Dark mode"
 *           ) { showThemeDialog = true }
 *           if (showThemeDialog) {
 *               RadioButtonDialog(
 *                   title = "Theme",
 *                   options = themeOptions,
 *                   selected = currentTheme,
 *                   onSelected = { /* handle */ },
 *                   onDismiss = { showThemeDialog = false }
 *               )
 *           }
 *       }
 *   }
 */

/**
 * Full settings screen scaffold with top bar, back button, and scrollable content.
 *
 * @param title Screen title shown in the top bar
 * @param onBack Back navigation callback
 * @param content Settings items to display
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            item { content() }
        }
    }
}

/**
 * A settings option card with icon, title, subtitle, and trailing chevron.
 * Used for navigable settings items.
 *
 * @param icon Icon displayed on the left
 * @param title Primary text
 * @param subtitle Secondary text (shown below title)
 * @param onClick Called when the card is tapped
 */
@Composable
fun SettingsOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Title and subtitle
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
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

/**
 * A settings section separator — a thin line with optional label.
 * Used to group related settings items.
 */
@Composable
fun SettingsSection(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

/**
 * A reusable radio button dialog for settings options.
 *
 * @param title Dialog title
 * @param options Available options to choose from
 * @param selected Currently selected option
 * @param onSelected Called when an option is selected
 * @param onDismiss Called when the dialog is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioButtonDialog(
    title: String,
    options: List<SettingsOption>,
    selected: SettingsOption,
    onSelected: (SettingsOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelected(option) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(option.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * A settings option for use with RadioButtonDialog.
 */
data class SettingsOption(
    val label: String
)
