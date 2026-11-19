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
import com.tsutsen.platformplayer.core.ui.LocalThumbnailContrast
import com.tsutsen.platformplayer.core.ui.LocalThumbnailSaturation
import com.tsutsen.platformplayer.core.ui.LocalThumbnailTint
import com.tsutsen.platformplayer.core.ui.LocalThumbnailTintBlendMode
import com.tsutsen.platformplayer.feature.settings.impl.DraftThemePreview
import com.tsutsen.platformplayer.core.ui.LocalThumbnailTintStrength

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
    // Live theme-editor preview: while the editor sheet is open it pushes the
    // draft scheme here; only the color scheme is overridden — typography and
    // per-surface skins keep coming from prefs. Read of DraftThemePreview is
    // snapshot-tracked, so scheme changes recompose the whole tree instantly.
    val effectiveSchemes =
        if (DraftThemePreview.active) {
            DraftThemePreview.light?.let { l -> DraftThemePreview.dark?.let { d -> l to d } }
        } else {
            customSchemes?.let { it.light to it.dark }
        }

    CompositionLocalProvider(
        // The tint color is normalized opaque: how much mixes in is the
        // strength pref, not whatever alpha an old picker baked in.
        LocalThumbnailTint provides appearance.thumbnailTintArgb?.let { Color(it).copy(alpha = 1f) },
        LocalThumbnailTintStrength provides appearance.thumbnailTintStrength,
        LocalThumbnailTintBlendMode provides appearance.thumbnailTintBlendMode,
        LocalThumbnailSaturation provides appearance.thumbnailSaturation,
        LocalThumbnailContrast provides appearance.thumbnailContrast,
    ) {
        BluejayTheme(
            darkTheme = darkTheme,
            dynamicColor = appearance.dynamicColor,
            uiRounding = appearance.uiRounding,
            colorScheme = effectiveSchemes?.let { if (darkTheme) it.first else it.second },
            typography = appTypography,
            surfaceSkins = surfaceSkins,
        ) {
            content(p)
        }
    }
}
