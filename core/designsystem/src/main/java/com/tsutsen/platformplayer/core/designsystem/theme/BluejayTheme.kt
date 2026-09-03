package com.tsutsen.platformplayer.core.designsystem.theme

import android.app.Activity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DefaultColorScheme = lightColorScheme()
private val DarkColorSchemeCustom = darkColorScheme()

/**
 * M3's expressive scheme, except the default effects spec never overshoots.
 *
 * M3 morphs button/chip/icon corner radii with [androidx.compose.material3.internal.rememberAnimatedShape],
 * driven by this spec. When a toggle is reversed mid-animation M3 resumes
 * the spring with the previous velocity, and even a critically damped
 * spring can push the morph progress past 1.0 — the corner lerp then
 * extrapolates to negative radii and crashes with "Corner size in Px can't
 * be negative" (seen on the like/dislike pill). A tween ignores the
 * initial velocity, so it structurally cannot overshoot: the same clamp
 * used on our own animated radii, applied at the one place every M3 shape
 * morph routes through.
 */
private val safeExpressiveMotionScheme: MotionScheme =
    object : MotionScheme by MotionScheme.expressive() {
        override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
            tween(durationMillis = 200, easing = FastOutSlowInEasing)
    }

/**
 * Semantic colors (warning etc.) for the current effective theme.
 * Danger and highlight use the M3 colorScheme roles directly; this
 * Local only carries what M3 has no role for.
 */
val LocalSemanticColors = compositionLocalOf<SemanticColors> { SemanticColorsLight }

@Composable
fun BluejayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    /**
     * Explicit scheme (e.g. a generated custom theme). Null = fall back to
     * the dynamic-color or brand scheme per [dynamicColor]/[darkTheme].
     */
    colorScheme: ColorScheme? = null,
    typography: Typography = BluejayTypography,
    /** 0..100 — user's UI rounding preference (Settings > Appearance). */
    uiRounding: Int = 100,
    /** M3 expressive motion physics for every animation in the app. */
    motionScheme: MotionScheme = safeExpressiveMotionScheme,
    content: @Composable () -> Unit,
) {
    val tokens = remember(uiRounding) {
        BluejayTokens(radius = RadiusScale.fromRounding(uiRounding))
    }
    val view = LocalView.current
    val resolvedScheme =
        colorScheme
            ?: if (dynamicColor) {
                if (darkTheme) dynamicDarkColorScheme(view.context)
                else dynamicLightColorScheme(view.context)
            } else {
                if (darkTheme) DarkColorSchemeCustom else DefaultColorScheme
            }
    if (!view.isInEditMode) {
        SideEffect {
            // The context is not an Activity when the UI is hosted in a
            // Presentation (second screen) — skip window chrome there.
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = resolvedScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = resolvedScheme,
        motionScheme = motionScheme,
        shapes = Shapes(),
        typography = typography,
        content = {
            CompositionLocalProvider(
                LocalSemanticColors provides
                    if (darkTheme) SemanticColorsDark else SemanticColorsLight,
                LocalBluejayTokens provides tokens,
            ) {
                // Surface (not Box) so LocalContentColor resolves to onSurface —
                // bare Text/Icons would otherwise default to hardcoded Color.Black.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = resolvedScheme.surface,
                    contentColor = resolvedScheme.onSurface,
                ) {
                    content()
                }
            }
        },
    )
}
