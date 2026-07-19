package com.futo.platformplayer.widget

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.futo.platformplayer.R
import com.futo.platformplayer.utils.Icons

/**
 * Custom TextView that displays Material Symbols Rounded icons.
 * 
 * Usage in XML:
 * <com.futo.platformplayer.widget.MaterialIconView
 *     app:iconName="ic_home"
 *     app:iconSize="24sp"
 *     ... />
 * 
 * Usage in Kotlin:
 * materialIconView.setIconName("ic_home")
 * materialIconView.setIconSize(24f)
 */
class MaterialIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var iconName: String? = null
    private var iconSize: Float = 24f

    init {
        // Remove default padding and margins
        setPadding(0, 0, 0, 0)
        layoutParams = ViewGroup.LayoutParams(-2, -2)
        gravity = android.view.Gravity.CENTER
        setLineSpacing(0f, 1f)
        
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.MaterialIconView)
            iconName = typedArray.getString(R.styleable.MaterialIconView_iconName)
            iconSize = typedArray.getDimension(R.styleable.MaterialIconView_iconSize, 24f)
            typedArray.recycle()
        }
        
        // Set the font
        try {
            typeface = Typeface.createFromAsset(context.assets, "font/material_symbols_rounded.ttf")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        applyIcon()
    }

    fun setIconName(name: String?) {
        iconName = name
        applyIcon()
    }

    fun setIconSize(size: Float) {
        iconSize = size
        textSize = size
    }

    private fun applyIcon() {
        if (iconName != null) {
            text = Icons[iconName!!]
            textSize = iconSize
        }
    }
}
