package com.tsutsen.platformplayer.compose

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsutsen.platformplayer.R
import com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences
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

/** Per-surface skins resolved from the user's stored ARGB overrides. */
fun buildSurfaceSkins(appearance: AppearancePreferences): Map<String, SurfaceSkin> =
    appearance.surfaceStyles.mapValues { (_, style) ->
        SurfaceSkin(
            color = style.colorArgb?.let { Color(it) },
            outlineColor = style.outlineColorArgb?.let { Color(it) },
            outlineWidth = style.outlineWidthDp?.let { androidx.compose.ui.unit.Dp(it) },
        )
    }
