package com.tsutsen.platformplayer.feature.player.impl

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.Window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * System volume + brightness in one place, shared by the player's gestures
 * and the companion screen's sliders so both move the same knobs.
 *
 * There is no public per-display brightness API, so "all screens" means:
 * the device-wide [Settings.System.SCREEN_BRIGHTNESS] (when the
 * WRITE_SETTINGS grant is in place) plus each window's own
 * [Window.attributes] [android.view.WindowManager.LayoutParams.screenBrightness],
 * applied to every window the app owns (main activity + companion
 * presentation). [applyBrightness] records the value in [brightness] so
 * every observing window follows a change made from either screen.
 */
object SystemControls {

    /**
     * Last brightness the user set (either screen). `null` until the first
     * change. Windows collect this and apply it to their own display.
     */
    private val _brightness = MutableStateFlow<Float?>(null)
    val brightness: StateFlow<Float?> = _brightness

    /**
     * Current brightness 0..1: the shared value when set, else the
     * device-wide setting (permission needed to read it reliably), else 1.
     */
    fun readBrightness(context: Context): Float =
        _brightness.value
            ?: if (canSetDeviceBrightness(context)) getDeviceBrightness(context) ?: 1f
            else 1f

    /**
     * Set brightness on ALL screens: device-wide when permitted, plus the
     * caller's [window] (the other windows follow through [brightness]).
     */
    fun applyBrightness(context: Context, value: Float, window: Window? = null) {
        val v = value.coerceIn(0f, 1f)
        _brightness.value = v
        if (!setDeviceBrightness(context, v)) {
            window?.let { setWindowBrightness(it, v) }
        }
    }

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
