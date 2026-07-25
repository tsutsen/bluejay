package com.tsutsen.platformplayer.compose.topbar

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import com.tsutsen.platformplayer.theming.AppearancePreferencesManager
import com.tsutsen.platformplayer.theming.ThemeMode

@Composable
fun rememberThemeMode(initial: ThemeMode = ThemeMode.AUTO): State<ThemeMode> {
    val context = LocalContext.current
    return produceState(initial, context) {
        snapshotFlow { context }.collect { ctx ->
            AppearancePreferencesManager(ctx).preferences.collect { prefs ->
                value = prefs.themeMode
            }
        }
    }
}
