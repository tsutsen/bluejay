package com.tsutsen.platformplayer.core.model

/**
 * A playback-blocking error raised by a plugin script (e.g. a content gate).
 * Thrown by the app-side resolver so core modules can recognise it without
 * depending on the engine, and can offer a "Go to plugin settings" action.
 */
class PluginScriptException(
    val pluginId: String,
    message: String,
) : Exception(message)
