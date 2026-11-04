package com.tsutsen.platformplayer.sabr

import com.tsutsen.platformplayer.core.data.playback.RecoverableSourceError
import java.io.IOException

open class SabrException(message: String, cause: Throwable? = null) : IOException(message, cause)

// Both implement RecoverableSourceError: a re-resolve of the same video
// mints a fresh po token / player response, so the player repository can
// transparently rebuild playback at the current position instead of dying.
class SabrBlockedException(message: String) : SabrException(message), RecoverableSourceError

class SabrReloadRequiredException(message: String) : SabrException(message), RecoverableSourceError

class SabrFormatSubstitutedException(message: String) : SabrException(message)
