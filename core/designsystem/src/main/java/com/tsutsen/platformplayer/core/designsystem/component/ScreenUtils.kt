package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowWidthSizeClass

/**
 * Returns true if the current window is wide enough for multi-column layouts.
 *
 * @param threshold Minimum width size class to consider "wide" (default: MEDIUM)
 * @return true if the window width is at least the threshold
 */
@Composable
fun rememberIsWide(threshold: WindowWidthSizeClass = WindowWidthSizeClass.MEDIUM): Boolean {
    val windowWidthSizeClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    return when (threshold) {
        WindowWidthSizeClass.MEDIUM -> windowWidthSizeClass == WindowWidthSizeClass.MEDIUM ||
                windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
        WindowWidthSizeClass.EXPANDED -> windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
        else -> false
    }
}
