package com.futo.platformplayer.views.pills

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.core.view.isVisible
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.dp
import com.futo.platformplayer.utils.Icons
import com.futo.platformplayer.views.LoaderView

class PillButton : LinearLayout {
    val root: LinearLayout;
    val icon: TextView;
    val text: TextView;
    val loaderView: LoaderView;
    val onClick = Event0();
    private var _isLoading = false;

    constructor(context : Context, attrs : AttributeSet?) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.pill_button, this, true);
        icon = findViewById(R.id.pill_icon);
        text = findViewById(R.id.pill_text);
        loaderView = findViewById(R.id.loader)
        root = findViewById<LinearLayout>(R.id.root);

        // Set Material Symbols font on icon TextView
        try {
            icon.typeface = Typeface.createFromAsset(context.assets, "font/material_symbols_rounded.ttf");
        } catch (e: Exception) {
            e.printStackTrace();
        }

        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.PillButton, 0, 0);
        val attrIconName = attrArr.getString(R.styleable.PillButton_pillIcon);
        if(attrIconName != null && !attrIconName.isNullOrEmpty()) {
            icon.text = Icons[attrIconName];
            icon.visibility = View.VISIBLE;
        } else {
            icon.visibility = View.GONE;
        }

        val attrText = attrArr.getText(R.styleable.PillButton_pillText) ?: "";
        text.text = attrText;

        if(text.text.isNullOrBlank()) {
            val dp6 = 6.dp(resources);
            val dp7 = 7.dp(resources);
            val dp12 = 12.dp(resources);
            root.setPadding(dp7, dp6, dp7, dp7)
        }

        findViewById<LinearLayout>(R.id.root).setOnClickListener {
            if (_isLoading) {
                return@setOnClickListener
            }

            onClick.emit();
        };
    }

    fun setTransparant() {
        root.setBackgroundColor(0);
    }

    fun setLoading(loading: Boolean) {
        if (loading == _isLoading) {
            return
        }

        if (loading) {
            text.visibility = View.GONE
            loaderView.visibility = View.VISIBLE
            loaderView.start()
        } else {
            loaderView.stop()
            text.visibility = View.VISIBLE
            loaderView.visibility = View.GONE
        }

        _isLoading = loading
    }
}