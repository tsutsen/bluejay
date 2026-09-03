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
    val SpaceXxs: Dp = 2.dp
    val SpaceXs: Dp = 4.dp
    val SpaceSm: Dp = 8.dp
    val SpaceMd: Dp = 12.dp
    val SpaceLg: Dp = 16.dp
    val SpaceXl: Dp = 24.dp

    // Icons
    val IconXs: Dp = 16.dp
    val IconSm: Dp = 18.dp
    val IconMd: Dp = 24.dp

    // Avatars
    val AvatarSm: Dp = 28.dp
    val AvatarMd: Dp = 40.dp
    val AvatarLg: Dp = 48.dp
    val AvatarXl: Dp = 56.dp

    // Buttons & touch
    val TouchTarget: Dp = 48.dp // minimum touchable area (icon buttons, chips)
    val ButtonSm: Dp = 36.dp // compact action buttons (Subscribe, like/dislike)
    val ButtonMd: Dp = 40.dp // M3's minimum button height (native group items, dropdown triggers)
    val ControlSm: Dp = 34.dp // small queue move buttons
    val ControlLg: Dp = 80.dp // companion (second screen) playback control row

    // Queue strip cards
    val QueueCardW: Dp = 240.dp
    val QueueCardH: Dp = 160.dp

    // Color swatches (theme editor, previews)
    val SwatchXs: Dp = 20.dp
    val SwatchSm: Dp = 24.dp
    val SwatchMd: Dp = 28.dp
    val SwatchLg: Dp = 48.dp

    // Strokes
    val StrokeEmphasized: Dp = 2.dp // selection borders (swatch selectors)

    // Dialogs
    val DialogSm: Dp = 300.dp

    // Option-sheet section labels (fixed width so chip rows align)
    val OptionLabelWidth: Dp = 76.dp

    // Charts (watch-time stats)
    val ChartSm: Dp = 28.dp
    val ChartLg: Dp = 48.dp
}

/**
 * Corner radius scale. Base values are what the app ships with; the whole
 * scale scales down linearly toward sharp at [RadiusScale.fromRounding].
 */
data class RadiusScale(
    val xs: Dp, // badges, small chips
    val sm: Dp, // cards, thumbnails
    val md: Dp, // sheet tiles, search field
    val card: Dp, // dash cards (queue, stats)
    val lg: Dp, // large cards, player panels
) {
    companion object {
        private const val XS = 4f
        private const val SM = 8f
        private const val MD = 12f
        private const val CARD = 16f
        private const val LG = 24f

        val Default = RadiusScale(XS.dp, SM.dp, MD.dp, CARD.dp, LG.dp)

        /**
         * @param rounding 0..200 — 100 is the shipped look, 0 is sharp,
         * above 100 rounds further (200 = double the shipped radii).
         */
        fun fromRounding(rounding: Int): RadiusScale {
            val f = (rounding / 100f).coerceIn(0f, 2f)
            return RadiusScale(
                xs = (XS * f).roundToInt().dp,
                sm = (SM * f).roundToInt().dp,
                md = (MD * f).roundToInt().dp,
                card = (CARD * f).roundToInt().dp,
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
