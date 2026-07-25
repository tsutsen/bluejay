package com.tsutsen.platformplayer.navigation

import androidx.annotation.StringRes

/**
 * Minimal button definition for use with TabViewHolderData.
 * Replaces MenuBottomBarFragment.ButtonDefinition for legacy XML UI.
 */
data class ButtonDefinition(
    val id: Int,
    val icon: String,
    val iconActive: String,
    @StringRes val string: Int,
    val canToggle: Boolean = false
)
