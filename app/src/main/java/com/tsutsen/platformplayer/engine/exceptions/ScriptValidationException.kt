package com.tsutsen.platformplayer.engine.exceptions

class ScriptValidationException(error: String, ex: Exception? = null) : Exception(error, ex);