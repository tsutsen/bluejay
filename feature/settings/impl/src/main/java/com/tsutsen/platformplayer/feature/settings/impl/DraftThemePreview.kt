package com.tsutsen.platformplayer.feature.settings.impl

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Process-wide holder for the theme editor's live preview: while the editor
 * sheet is open, [active] is true and [light]/[dark] carry the scheme built
 * from the current draft values. The app theme root ([com.tsutsen.platformplayer.compose.BluejayAppThemeRoot])
 * reads these to override the active color scheme so the settings screen
 * behind the sheet re-renders as colors/styles change.
 *
 * Only the color scheme is overridden — typography, dynamic-color flag and
 * per-surface skins keep coming from prefs, so the preview shows the draft
 * palette on the real app chrome without faking the rest.
 *
 * Lifecycle is owned by the editor composable: it activates on entering
 * composition (sheet opens), refreshes on every draft change and deactivates
 * on disposal (sheet dismissed OR saved), so nothing leaks after the sheet
 * closes.
 */
object DraftThemePreview {
    var active by mutableStateOf(false)
        private set

    var light by mutableStateOf<ColorScheme?>(null)
        private set

    var dark by mutableStateOf<ColorScheme?>(null)
        private set

    /** Editor activates + pushes a snapshot; called on every draft change. */
    fun update(light: ColorScheme?, dark: ColorScheme?) {
        this.light = light
        this.dark = dark
        active = true
    }

    /** Editor clears on disposal (dismiss or save). */
    fun clear() {
        active = false
        light = null
        dark = null
    }
}
