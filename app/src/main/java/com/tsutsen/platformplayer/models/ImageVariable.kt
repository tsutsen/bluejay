package com.tsutsen.platformplayer.models

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tsutsen.platformplayer.R
import com.tsutsen.platformplayer.logging.Logger
import kotlinx.serialization.Contextual
import kotlinx.serialization.Transient
import java.io.File

@kotlinx.serialization.Serializable
data class ImageVariable(
    val url: String? = null,
    val resId: Int? = null,
    @Transient
    @Contextual
    private val bitmap: Bitmap? = null,
    val presetName: String? = null,
    var subscriptionUrl: String? = null) {

    companion object {
        fun fromUrl(url: String): ImageVariable {
            return ImageVariable(url, null, null);
        }
        fun fromResource(id: Int): ImageVariable {
            return ImageVariable(null, id, null);
        }
        fun fromBitmap(bitmap: Bitmap): ImageVariable {
            return ImageVariable(null, null, bitmap);
        }
        fun fromPresetName(str: String): ImageVariable {
            return ImageVariable(null, null, null, str);
        }
        fun fromFile(file: File): ImageVariable {
            try {
                return ImageVariable.fromBitmap(BitmapFactory.decodeFile(file.absolutePath));
            }
            catch(ex: Throwable) {
                Logger.e("ImageVariable", "Unsupported image format? " + ex.message, ex);
                return fromResource(R.drawable.ic_error_pred);
            }
        }
    }
}