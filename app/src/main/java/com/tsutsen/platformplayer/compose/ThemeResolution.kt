package com.tsutsen.platformplayer.compose

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tsutsen.platformplayer.R
import com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences
import com.tsutsen.platformplayer.core.datastore.model.CustomTheme
import com.tsutsen.platformplayer.core.datastore.model.FontFamily as FontFamilyPref
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTypography
import com.tsutsen.platformplayer.core.designsystem.theme.SurfaceSkin

/** The bundled Inter set (app/src/main/res/font), wired to FontWeight slots. */
val AppInterFontFamily =
    FontFamily(
        Font(R.font.inter_thin, FontWeight.Thin),
        Font(R.font.inter_extra_light, FontWeight.ExtraLight),
        Font(R.font.inter_light, FontWeight.Light),
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
        Font(R.font.inter_extra_bold, FontWeight.ExtraBold),
        Font(R.font.inter_black, FontWeight.Black),
    )

/**
 * The app typography for the user's font choice + size scale. Returns the
 * stock [BluejayTypography] untouched at the defaults; otherwise every style
 * gets the chosen family and a scaled size/lineHeight.
 */
fun buildAppTypography(
    fontFamily: FontFamilyPref,
    fontSizeScale: Float,
): Typography {
    val fam =
        when (fontFamily) {
            // Roboto IS the system default; Open Sans is not bundled — the
            // system sans-serif is the closest available.
            FontFamilyPref.DEFAULT, FontFamilyPref.ROBOTO, FontFamilyPref.OPEN_SANS -> FontFamily.Default
            FontFamilyPref.INTER -> AppInterFontFamily
        }
    val scale = fontSizeScale.coerceIn(0.7f, 1.6f)
    if (fam == FontFamily.Default && scale == 1f) return BluejayTypography

    fun TextStyle.scaled(): TextStyle =
        copy(
            fontFamily = fam,
            fontSize = (fontSize.value * scale).sp,
            lineHeight = (lineHeight.value * scale).sp,
        )

    return Typography(
        displayLarge = BluejayTypography.displayLarge.scaled(),
        displayMedium = BluejayTypography.displayMedium.scaled(),
        displaySmall = BluejayTypography.displaySmall.scaled(),
        headlineLarge = BluejayTypography.headlineLarge.scaled(),
        headlineMedium = BluejayTypography.headlineMedium.scaled(),
        headlineSmall = BluejayTypography.headlineSmall.scaled(),
        titleLarge = BluejayTypography.titleLarge.scaled(),
        titleMedium = BluejayTypography.titleMedium.scaled(),
        titleSmall = BluejayTypography.titleSmall.scaled(),
        bodyLarge = BluejayTypography.bodyLarge.scaled(),
        bodyMedium = BluejayTypography.bodyMedium.scaled(),
        bodySmall = BluejayTypography.bodySmall.scaled(),
        labelLarge = BluejayTypography.labelLarge.scaled(),
        labelMedium = BluejayTypography.labelMedium.scaled(),
        labelSmall = BluejayTypography.labelSmall.scaled(),
    )
}

/**
 * Per-surface skins of the ACTIVE theme (surface styles are per-theme, not a
 * global layer): color + outline overrides, plus textures decoded from the
 * files the theme editor copied into app storage (downsampled to ≤ 640px).
 */
@Composable
fun rememberSurfaceSkins(appearance: AppearancePreferences): Map<String, SurfaceSkin> {
    val activeTheme = appearance.customThemes.firstOrNull { it.id == appearance.activeThemeId }
    return remember(activeTheme) { buildSurfaceSkins(activeTheme) }
}

fun buildSurfaceSkins(activeTheme: CustomTheme?): Map<String, SurfaceSkin> =
    (activeTheme?.surfaceStyles ?: emptyMap()).mapValues { (_, style) ->
        SurfaceSkin(
            color = style.colorArgb?.let { Color(it) },
            outlineColor = style.outlineColorArgb?.let { Color(it) },
            outlineWidth = style.outlineWidthDp?.let { Dp(it) },
            texture = style.texturePath?.let { decodeTexture(it) },
        )
    }

/** Max decoded texture edge in px — wallpapers tile, they don't need detail. */
private const val TEXTURE_MAX_EDGE = 640

private fun decodeTexture(path: String): ImageBitmap? =
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TEXTURE_MAX_EDGE &&
            bounds.outHeight / (sample * 2) >= TEXTURE_MAX_EDGE
        ) {
            sample *= 2
        }
        BitmapFactory
            .decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap()
    }.getOrNull()
