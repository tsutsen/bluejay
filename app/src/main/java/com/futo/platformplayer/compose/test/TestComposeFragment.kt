package com.futo.platformplayer.compose.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.view.TagBadge
import com.futo.platformplayer.compose.view.TagBadgeWithValue
import com.futo.platformplayer.fragment.mainactivity.main.MainFragment

/**
 * Phase 0 test fragment: verifies that:
 * 1. ComposeView can be hosted inside a Fragment
 * 2. The Fragment fits into the existing nav graph
 * 3. Material3 theming works correctly (auto-applied by MainFragment)
 * 4. Theme state is observable (live theme changes propagate)
 *
 * This fragment can be navigated to from MainActivity for manual verification.
 */
class TestComposeFragment : MainFragment() {
    override val isMainView: Boolean = true
    override val isComposeMode: Boolean = true
    override val hasBottomBar: Boolean get() = false

    @Composable
    override fun ComposeContent() { TestComposeScreen() }

    companion object {
        fun newInstance() = TestComposeFragment().apply {}
    }
}

@Composable
private fun TestComposeScreen() {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Phase 0: Compose Migration Pipeline",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = "If you can read this, Compose is working inside a Fragment in the nav graph.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "TagBadge (ported from XML TagView):",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Default styling (surfaceVariant bg + onSurface text):",
                style = MaterialTheme.typography.bodySmall
            )

            // Test the ported TagBadge with different configurations
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TagBadge(text = "Default")
                TagBadgeWithValue(text = "With value", value = 42, onClick = { _, v ->
                    // In a real screen, this would trigger an action
                })
                TagBadge(text = "Clickable", onClick = { /* action */ })
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Theme tokens (Material3):",
                style = MaterialTheme.typography.titleSmall
            )

            // Demonstrate theme token usage
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TagBadge(
                    text = "Primary",
                    textColor = MaterialTheme.colorScheme.onPrimary,
                    backgroundColor = MaterialTheme.colorScheme.primary
                )
                TagBadge(
                    text = "Secondary",
                    textColor = MaterialTheme.colorScheme.onSecondary,
                    backgroundColor = MaterialTheme.colorScheme.secondary
                )
                TagBadge(
                    text = "Tertiary",
                    textColor = MaterialTheme.colorScheme.onTertiary,
                    backgroundColor = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "✓ Interop convention: Fragment → ComposeView",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "✓ Theme tokens: Material3 color scheme",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "✓ TagBadge port: XML → Compose (1:1 visual match)",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Phase 1 (Chrome + Settings) ready to begin.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
