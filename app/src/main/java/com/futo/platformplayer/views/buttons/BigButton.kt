package com.futo.platformplayer.views.buttons

import android.content.Context
import android.graphics.Typeface
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.utils.Icons
import android.widget.ImageView
import android.graphics.Bitmap
import android.util.TypedValue
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel

open class BigButton : LinearLayout {
    private val _root: LinearLayout;
    private val _icon: TextView;
    private val _textPrimary: TextView;
    private val _textSecondary: TextView;

    val title: String get() = _textPrimary.text.toString();
    val description: String get() = _textSecondary.text.toString();

    val onClick = Event0();

    constructor(context : Context, text: String, subText: String, icon: String, action: ()->Unit) : super(context) {
        inflate(context, R.layout.big_button, this);
        _icon = findViewById(R.id.button_icon);
        _textPrimary = findViewById(R.id.button_text);
        _textSecondary = findViewById(R.id.button_sub_text);
        _root = findViewById(R.id.root);

        // Set Material Symbols font
        try {
            _icon.typeface = Typeface.createFromAsset(context.assets, "font/material_symbols_rounded.ttf")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _textPrimary.text = text;
        _textSecondary.text = subText;
        _icon.text = Icons[icon];

        _root.setBackgroundResource(R.drawable.background_big_button);

        _root.apply {
            isClickable = true;
            setOnClickListener {
                if(!isEnabled)
                    return@setOnClickListener;
                action();
                onClick.emit();
            };
        }
    }

    constructor(context : Context, text: String, subText: String, icon: Int, action: ()->Unit) : super(context) {
        inflate(context, R.layout.big_button, this);
        _icon = findViewById(R.id.button_icon);
        _textPrimary = findViewById(R.id.button_text);
        _textSecondary = findViewById(R.id.button_sub_text);
        _root = findViewById(R.id.root);

        _textPrimary.text = text;
        _textSecondary.text = subText;
        _icon.text = Icons["ic_image"];

        _root.setBackgroundResource(R.drawable.background_big_button);

        _root.apply {
            isClickable = true;
            setOnClickListener {
                if(!isEnabled)
                    return@setOnClickListener;
                action();
                onClick.emit();
            };
        }
    }
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.big_button, this);
        _icon = findViewById(R.id.button_icon);
        _textPrimary = findViewById(R.id.button_text);
        _textSecondary = findViewById(R.id.button_sub_text);
        _root = findViewById(R.id.root);
        
        // Set Material Symbols font
        try {
            _icon.typeface = Typeface.createFromAsset(context.assets, "font/material_symbols_rounded.ttf")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        _root.apply {
            isClickable = true;
            setOnClickListener {
                if(!isEnabled)
                    return@setOnClickListener;
                onClick.emit();
            };
        }

        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.BigButton, 0, 0);
        val attrIconName = attrArr.getString(R.styleable.BigButton_buttonIconName);
        val attrBackgroundRef = attrArr.getResourceId(R.styleable.BigButton_buttonBackground, -1);
        val attrText = attrArr.getText(R.styleable.BigButton_buttonText) ?: "";
        val attrTextSecondary = attrArr.getText(R.styleable.BigButton_buttonSubText) ?: "";
        attrArr.recycle()

        if (attrIconName != null) {
            _icon.text = Icons[attrIconName]
        } else {
            _icon.visibility = View.GONE
        }
        withBackground(attrBackgroundRef);
        _textPrimary.text = attrText;
        _textSecondary.text = attrTextSecondary;
    }

    fun withMargin(bottom: Int, side: Int = 0): BigButton {
        setPadding(side, 0, side, bottom)
        return this;
    }

    fun setSecondaryText(text: String?) {
        _textSecondary.text = text
    }

    fun withPrimaryText(text: String): BigButton {
        _textPrimary.text = text;
        return this;
    }

    fun withSecondaryText(text: String): BigButton {
        _textSecondary.text = text;
        return this;
    }
    fun withSecondaryTextMaxLines(lines: Int): BigButton {
        _textSecondary.maxLines = lines;
        return this;
    }

    fun withIcon(iconName: String): BigButton {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _icon.visibility = View.VISIBLE
            _icon.text = Icons[iconName]
        } else {
            post { _icon.text = Icons[iconName] }
        }
        return this
    }

    fun withIcon(iconName: String?, rounded: Boolean = false): BigButton {
        if (iconName != null) {
            return withIcon(iconName)
        }
        _icon.visibility = View.GONE
        return this
    }

    fun withIcon(resourceId: Int, rounded: Boolean = false): BigButton {
        // Fallback for backward compatibility - use generic icon
        _icon.visibility = View.VISIBLE
        _icon.text = Icons["ic_image"]
        return this
    }

    fun withIcon(bitmap: Bitmap, rounded: Boolean = false): BigButton {
        // Fallback for backward compatibility - use generic icon
        _icon.visibility = View.VISIBLE
        _icon.text = Icons["ic_image"]
        return this
    }

    fun withBackground(resourceId: Int): BigButton {
        if (resourceId != -1) {
            _root.visibility = View.VISIBLE;
            _root.setBackgroundResource(resourceId);
        } else
            _root.setBackgroundResource(R.drawable.background_big_button);

        return this;
    }

    fun setButtonEnabled(enabled: Boolean) {
        if(enabled) {
            alpha = 1f;
            isEnabled = true;
            isClickable = true;
        }
        else {
            alpha = 0.5f;
            isEnabled = false;
            isClickable = false;
        }
    }
}