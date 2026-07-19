package com.futo.platformplayer.views.buttons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.utils.Icons

class ShortsButton : LinearLayout {
    private val _root: LinearLayout;
    private val _icon: TextView;
    private val _textPrimary: TextView;
    val onClick = Event0();

    var iconName: String? = null;

    constructor(context : Context, text: String, icon: String, action: ()->Unit) : super(context) {
        inflate(context, R.layout.view_shorts_button, this);
        _icon = findViewById(R.id.button_icon);
        _textPrimary = findViewById(R.id.button_text);
        _root = findViewById(R.id.root);
        
        // Set Material Symbols font
        try {
            _icon.typeface = Typeface.createFromAsset(context.assets, "font/material_symbols_rounded.ttf")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        withPrimaryText(text);
        withIcon(icon);

        _root.apply {
            isClickable = true;
            setOnClickListener {
                if(!isEnabled)
                    return@setOnClickListener;
                action();
                onClick.emit();
                UIDialogs.toast("Clicked button: " + _textPrimary.text);
            };
        }
    }
    
    constructor(context : Context, text: String, icon: Int, action: ()->Unit) : super(context) {
        inflate(context, R.layout.view_shorts_button, this);
        _icon = findViewById(R.id.button_icon);
        _textPrimary = findViewById(R.id.button_text);
        _root = findViewById(R.id.root);
        
        // Set Material Symbols font
        try {
            _icon.typeface = Typeface.createFromAsset(context.assets, "font/material_symbols_rounded.ttf")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        withPrimaryText(text);
        withIcon(icon);

        _root.apply {
            isClickable = true;
            setOnClickListener {
                if(!isEnabled)
                    return@setOnClickListener;
                action();
                onClick.emit();
                UIDialogs.toast("Clicked button: " + _textPrimary.text);
            };
        }
    }
    
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_shorts_button, this);
        _icon = findViewById(R.id.button_icon);
        _textPrimary = findViewById(R.id.text_title);
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

        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.ShortsButton, 0, 0);
        val attrIconName = attrArr.getString(R.styleable.ShortsButton_buttonIconName_s);
        val attrIconRef = attrArr.getResourceId(R.styleable.ShortsButton_buttonIcon_s, -1);
        val attrText = attrArr.getText(R.styleable.ShortsButton_buttonText_s) ?: "";
        attrArr.recycle()

        if (attrIconName != null) {
            withIcon(attrIconName)
        } else {
            withIcon(attrIconRef);
        }
        withPrimaryText(attrText.toString());
    }

    fun withMargin(bottom: Int, side: Int = 0): ShortsButton {
        setPadding(side, 0, side, bottom)
        return this;
    }
    fun withPrimaryText(text: String): ShortsButton {
        _textPrimary.text = text;

        if(text.isNullOrBlank())
            _textPrimary.visibility = View.GONE;
        else
            _textPrimary.visibility = View.VISIBLE;
        return this;
    }

    fun withIcon(iconName: String): ShortsButton {
        _icon.visibility = View.VISIBLE;
        _icon.text = Icons[iconName];
        this.iconName = iconName;
        return this;
    }

    fun withIcon(resourceId: Int): ShortsButton {
        _icon.visibility = View.VISIBLE;
        _icon.text = Icons["ic_image"];
        this.iconName = null;
        return this;
    }

    fun withIcon(bitmap: Bitmap): ShortsButton {
        _icon.visibility = View.VISIBLE;
        _icon.text = Icons["ic_image"];
        this.iconName = null;
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