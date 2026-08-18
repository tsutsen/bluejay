package com.tsutsen.platformplayer.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design tokens — the single source for spacing, corner radii, icon and
 * avatar sizes. Every shared component sources its dimensions from here so
 * the whole app can be re-themed/retuned from one place.
 *
 * Scales (derived from the values actually in use):
 * spacing: 4 / 8 / 12 / 16 / 24
 * radii:   4 / 8 / 12 (circle = CircleShape)
 * icons:   18 / 24
 * avatars: 40 / 48 / 56
 */
object Tokens {
    // Spacing
    val SpaceXs: Dp = 4.dp
    val SpaceSm: Dp = 8.dp
    val SpaceMd: Dp = 12.dp
    val SpaceLg: Dp = 16.dp
    val SpaceXl: Dp = 24.dp

    // Corner radii
    val RadiusXs: Dp = 4.dp // badges, small chips
    val RadiusSm: Dp = 8.dp // cards, thumbnails
    val RadiusMd: Dp = 12.dp // sheets' tiles, search field

    // Icons
    val IconSm: Dp = 18.dp
    val IconMd: Dp = 24.dp

    // Avatars
    val AvatarMd: Dp = 40.dp
    val AvatarLg: Dp = 48.dp
    val AvatarXl: Dp = 56.dp
}
