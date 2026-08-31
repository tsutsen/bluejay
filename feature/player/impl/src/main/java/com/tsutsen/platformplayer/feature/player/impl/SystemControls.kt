package com.tsutsen.platformplayer.feature.player.impl

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.Window

/**
 * System volume + brightness in one place, shared by the player's gestures
 * and the companion screen's sliders so both move the same knobs.
 *
 * Brightness prefers the device-wide setting (affects every window/screen,
 * requires the WRITE_SETTINGS grant); callers fall back to window-local
 * [Window] brightness when it is unavailable.
 */
object SystemControls {

    /** Music stream volume, 0..1. */
    fun getVolume(context: Context): Float {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0f
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else 0f
    }

    fun setVolume(context: Context, value: Float) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (value.coerceIn(0f, 1f) * max).toInt().coerceIn(0, max),
            0,
        )
    }

    /** True when device-wide brightness writes are permitted (WRITE_SETTINGS). */
    fun canSetDeviceBrightness(context: Context): Boolean =
        runCatching { Settings.System.canWrite(context) }.getOrDefault(false)

    /** Device-wide brightness, 0..1 (null when unavailable). */
    fun getDeviceBrightness(context: Context): Float? =
        runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                .takeIf { it in 0..255 }
                ?.let { it / 255f }
        }.getOrNull()

    /** Set device-wide brightness. True on success (permission granted). */
    fun setDeviceBrightness(context: Context, value: Float): Boolean =
        runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                (value.coerceIn(0f, 1f) * 255).toInt(),
            )
        }.isSuccess

    fun getWindowBrightness(window: Window): Float {
        val b = window.attributes.screenBrightness
        return if (b < 0f) 1f else b // < 0 = follow system (auto)
    }

    fun setWindowBrightness(window: Window, value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value.coerceIn(0f, 1f) }
    }
}
