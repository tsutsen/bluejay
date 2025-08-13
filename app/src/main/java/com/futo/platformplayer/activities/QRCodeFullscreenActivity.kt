package com.futo.platformplayer.activities

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.futo.platformplayer.R
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp

class QRCodeFullscreenActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_QR_BITMAP = "qr_bitmap"
        private const val EXTRA_QR_TEXT = "qr_text"
        
        fun createIntent(context: Context, qrBitmap: Bitmap, qrText: String): android.content.Intent {
            return android.content.Intent(context, QRCodeFullscreenActivity::class.java).apply {
                putExtra(EXTRA_QR_BITMAP, qrBitmap)
                putExtra(EXTRA_QR_TEXT, qrText)
            }
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code_fullscreen)
        setNavigationBarColorAndIcons()

        val qrBitmap = intent.getParcelableExtra<Bitmap>(EXTRA_QR_BITMAP)
        val qrText = intent.getStringExtra(EXTRA_QR_TEXT)

        val imageQR = findViewById<ImageView>(R.id.image_qr_fullscreen)
        val buttonBack = findViewById<ImageButton>(R.id.button_back_fullscreen)
        val buttonClose = findViewById<ImageButton>(R.id.button_close_fullscreen)

        // Set the QR code image
        qrBitmap?.let { bitmap ->
            imageQR.setImageBitmap(bitmap)
        }

        // Set click listeners
        buttonBack.setOnClickListener {
            finish()
        }

        buttonClose.setOnClickListener {
            finish()
        }

        // Make the entire QR code area clickable to close
        imageQR.setOnClickListener {
            finish()
        }
    }
}
