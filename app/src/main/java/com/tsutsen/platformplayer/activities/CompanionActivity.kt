package com.tsutsen.platformplayer.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.theme.GrayjayTheme
import com.tsutsen.platformplayer.feature.dualscreen.ScreenCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "CompanionActivity"

/**
 * Companion activity for secondary display.
 * Shows player controls and companion content tabs.
 */
@AndroidEntryPoint
class CompanionActivity : ComponentActivity() {

    @Inject
    lateinit var screenCoordinator: ScreenCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GrayjayTheme {
                CompanionScreenContent(screenCoordinator)
            }
        }
    }
}

@Composable
private fun CompanionScreenContent(screenCoordinator: ScreenCoordinator) {
    val appState by screenCoordinator.appState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        when (appState) {
            is com.tsutsen.platformplayer.feature.dualscreen.AppState.Browsing -> {
                CompanionPlaceholder("Browsing", "Waiting for video to start...")
            }
            is com.tsutsen.platformplayer.feature.dualscreen.AppState.VideoOpen -> {
                CompanionPlaceholder(
                    "Now Playing",
                    (appState as com.tsutsen.platformplayer.feature.dualscreen.AppState.VideoOpen).videoId
                )
            }
            is com.tsutsen.platformplayer.feature.dualscreen.AppState.VideoMinimized -> {
                CompanionPlaceholder(
                    "Mini Player",
                    (appState as com.tsutsen.platformplayer.feature.dualscreen.AppState.VideoMinimized).videoId
                )
            }
        }
    }
}

@Composable
private fun CompanionPlaceholder(title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$title\n$subtitle",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
