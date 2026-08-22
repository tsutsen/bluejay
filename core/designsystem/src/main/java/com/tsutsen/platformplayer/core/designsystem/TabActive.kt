package com.tsutsen.platformplayer.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow

/**
 * Whether the keep-alive tab containing this composition is the one on screen.
 * The nav graph provides this per tab; hidden keep-alive tabs report false so
 * screens can pause non-essential collection (e.g. shared live state like
 * watch progress) until they're visible again. Defaults to true so screens
 * outside the keep-alive graph (detail screens, previews) behave normally.
 */
val LocalTabActive = staticCompositionLocalOf { true }

/**
 * [Flow] collection that pauses while [enabled] is false, keeping the last
 * value. For keep-alive tabs: hidden tabs stop recomposing on shared-state
 * changes, and refresh immediately when shown again (the collection restarts
 * and emits the current value right away).
 */
@Composable
fun <T> Flow<T>.collectAsActiveState(
    initial: T,
    enabled: Boolean = LocalTabActive.current,
): State<T> {
    val value = remember { mutableStateOf(initial) }
    LaunchedEffect(enabled) {
        if (enabled) {
            collect { value.value = it }
        }
    }
    return value
}
