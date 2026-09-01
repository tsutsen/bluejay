package com.tsutsen.platformplayer.core.designsystem.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
 * Parameterized values (radius, motion) flow through [LocalBluejayTokens]
 * so [BluejayTheme] can derive them from user preferences — e.g. the
 * "UI rounding" slider rescales every radius in the app.
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
 * Motion recipe. Three specs cover the app: [state] for small interactive
 * feedback (press, color, size of one element), [content] for larger
 * transitions (content swap, reveal), [spring] for big physical moves.
 * Every animation site uses these instead of ad-hoc durations so smoothness
 * is tuned in one place.
 */
data class Motion(
    val state: Int = 200, // ms
    val content: Int = 300, // ms
    val springDampingRatio: Float = Spring.DampingRatioMediumBouncy,
    val springStiffness: Float = Spring.StiffnessMedium,
) {
    /** Small interactive feedback (press, one-element color/size). */
    fun <T> stateSpec(): FiniteAnimationSpec<T> = tween(state, easing = FastOutSlowInEasing)

    /** Larger transitions (content swap, reveal). */
    fun <T> contentSpec(): FiniteAnimationSpec<T> = tween(content, easing = FastOutSlowInEasing)

    /** Big physical moves. */
    fun <T> springSpec(): SpringSpec<T> =
        spring(dampingRatio = springDampingRatio, stiffness = springStiffness)
}

/** The full token set for the current user preferences. */
data class BluejayTokens(
    val radius: RadiusScale,
    val motion: Motion,
) {
    companion object {
        val Default = BluejayTokens(RadiusScale.Default, Motion())
    }
}

val LocalBluejayTokens = compositionLocalOf { BluejayTokens.Default }

@Composable
fun BluejayTokens(): BluejayTokens = LocalBluejayTokens.current
