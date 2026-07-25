package com.futo.platformplayer.others

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView

/**
 * Stub for PlatformLinkMovementMethod.
 * The original PlatformLinkMovementMethod was a custom TextView movement method.
 * It has been removed during the Compose migration.
 */
class PlatformLinkMovementMethod(context: Context) : LinkMovementMethod() {
    companion object {
        @Volatile
        private var _instance: PlatformLinkMovementMethod? = null
        
        fun getInstance(context: Context): PlatformLinkMovementMethod {
            return _instance ?: synchronized(this) {
                _instance ?: PlatformLinkMovementMethod(context).also { _instance = it }
            }
        }
    }
}
