package com.tsutsen.platformplayer.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTheme
import com.tsutsen.platformplayer.core.designsystem.theme.ThemeEngine
import com.tsutsen.platformplayer.core.ui.LocalThumbnailSaturation
import com.tsutsen.platformplayer.core.ui.LocalThumbnailTint

/**
 * The single deepened theme root shared by both presentation surfaces
 * (MainActivity on the main display, CompanionPresentation on the second
 * screen): prefs gate, dark-mode resolution, active custom theme, user
 * typography, per-surface skins and the thumbnail filter locals.
 *
 * Every theme feature lands here once — this used to be ~40 near-verbatim
 * lines duplicated between the two roots (same pasted comment included).
 */
@Composable
fun BluejayAppThemeRoot(
    settingsRepository: SettingsRepository,
    content: @Composable (AppPreferences) -> Unit,
) {
    // Gate on the real prefs instead of a default initial: with defaults the
    // first frame renders default tokens and the saved values arrive a frame
    // later — radius springs then animate default→saved and overshoot into
    // negative corner radii, which is fatal.
    val prefsState = settingsRepository.preferences.collectAsState(initial = null)
    val p = prefsState.value ?: return
    val darkTheme =
        when (p.appearance.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AUTO -> isSystemInDarkTheme()
        }
    val appearance = p.appearance
    val activeTheme = appearance.customThemes.firstOrNull { it.id == appearance.activeThemeId }
    val customSchemes =
        remember(activeTheme) {
            activeTheme?.let {
                ThemeEngine.generate(
                    it.primary,
                    it.secondary,
                    it.tertiary,
                    it.paletteStyle,
                    background = it.background,
                    contrast = it.contrast,
                )
            }
        }
    val appTypography =
        remember(appearance.fontFamily, appearance.fontSizeScale) {
            buildAppTypography(appearance.fontFamily, appearance.fontSizeScale)
        }
    val surfaceSkins = rememberSurfaceSkins(appearance)

    CompositionLocalProvider(
        LocalThumbnailTint provides appearance.thumbnailTintArgb?.let { Color(it) },
        LocalThumbnailSaturation provides appearance.thumbnailSaturation,
    ) {
        BluejayTheme(
            darkTheme = darkTheme,
            dynamicColor = appearance.dynamicColor,
            uiRounding = appearance.uiRounding,
            colorScheme = customSchemes?.let { if (darkTheme) it.dark else it.light },
            typography = appTypography,
            surfaceSkins = surfaceSkins,
        ) {
            content(p)
        }
    }
}
