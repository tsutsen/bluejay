package com.tsutsen.platformplayer.core.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Design tokens — the single source for spacing, corner radii, icon/avatar
 * sizes, and motion. Every shared component sources its dimensions from
 * here so the whole app can be re-themed/retuned from one place.
 *
 * Static values (spacing, icons, avatars) never change per-user.
 * The radius scale flows through [LocalBluejayTokens] so [BluejayTheme]
 * can derive it from user preferences — the "UI rounding" slider rescales
 * every radius in the app. Motion specs come from the theme's
 * [androidx.compose.material3.MotionScheme] via [spatialSpec]/[effectsSpec].
 */
object Tokens {
    // Spacing
    val SpaceXs: Dp = 4.dp
    val SpaceSm: Dp = 8.dp
    val SpaceMd: Dp = 12.dp
    val SpaceLg: Dp = 16.dp
    val SpaceXl: Dp = 24.dp

    // Icons
    val IconSm: Dp = 18.dp
    val IconMd: Dp = 24.dp

    // Avatars
    val AvatarMd: Dp = 40.dp
    val AvatarLg: Dp = 48.dp
    val AvatarXl: Dp = 56.dp

    // Static radii for things that must not follow the user's rounding
    // preference (stadium/pill shapes are round at every setting).
    val RadiusFull: Dp = 500.dp
}

/**
 * Corner radius scale. Base values are what the app ships with; the whole
 * scale scales down linearly toward sharp at [RadiusScale.fromRounding].
 */
data class RadiusScale(
    val xs: Dp, // badges, small chips
    val sm: Dp, // cards, thumbnails
    val md: Dp, // sheet tiles, search field
    val lg: Dp, // large cards, player panels
) {
    companion object {
        private const val XS = 4f
        private const val SM = 8f
        private const val MD = 12f
        private const val LG = 24f

        val Default = RadiusScale(XS.dp, SM.dp, MD.dp, LG.dp)

        /**
         * @param rounding 0..100 — 100 is the shipped look, 0 is sharp.
         */
        fun fromRounding(rounding: Int): RadiusScale {
            val f = (rounding / 100f).coerceIn(0f, 1f)
            return RadiusScale(
                xs = (XS * f).roundToInt().dp,
                sm = (SM * f).roundToInt().dp,
                md = (MD * f).roundToInt().dp,
                lg = (LG * f).roundToInt().dp,
            )
        }
    }
}

/**
 * Motion specs pulled from the theme's [androidx.compose.material3.MotionScheme]
 * (M3 expressive motion physics — the token-based replacement for ad-hoc
 * tweens/springs). Every animation site uses these so the whole app moves
 * as one and retunes from the theme:
 *
 * - [spatialSpec]: position / size / shape / offset changes
 * - [effectsSpec]: opacity / color / blur changes
 */
@Composable
fun <T> spatialSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultSpatialSpec()

@Composable
fun <T> effectsSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()

/** The full token set for the current user preferences. */
data class BluejayTokens(
    val radius: RadiusScale,
) {
    companion object {
        val Default = BluejayTokens(RadiusScale.Default)
    }
}

val LocalBluejayTokens = compositionLocalOf { BluejayTokens.Default }

@Composable
fun BluejayTokens(): BluejayTokens = LocalBluejayTokens.current
