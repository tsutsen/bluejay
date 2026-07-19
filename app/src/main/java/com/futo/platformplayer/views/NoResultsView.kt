package com.futo.platformplayer.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.utils.Icons

class NoResultsView: ConstraintLayout {

    val textTitle: TextView;
    val textCentered: TextView;
    val icon: TextView;
    val containerExtraViews: LinearLayout;

    constructor(context: Context, attributes: AttributeSet? = null) : super(context, attributes){
        inflate(context, R.layout.view_no_results, this);
        textTitle = findViewById(R.id.text_title)
        textCentered = findViewById(R.id.text_centered);
        icon = findViewById(R.id.icon);
        containerExtraViews = findViewById(R.id.container_extra_views);
    }

    constructor(context: Context, title: String, text: String, iconName: String, extraViews: List<View>) : super(context) {
        inflate(context, R.layout.view_no_results, this);
        textTitle = findViewById(R.id.text_title)
        textCentered = findViewById(R.id.text_centered);
        icon = findViewById(R.id.icon);
        containerExtraViews = findViewById(R.id.container_extra_views);
        
        // Set Material Symbols font
        try {
            icon.typeface = Typeface.createFromAsset(context.assets, "font/material_symbols_rounded.ttf")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setText(title, text, iconName, extraViews);
    }

    // Backward compatibility constructor
    constructor(context: Context, title: String, text: String, iconId: Int, extraViews: List<View>) : super(context) {
        inflate(context, R.layout.view_no_results, this);
        textTitle = findViewById(R.id.text_title)
        textCentered = findViewById(R.id.text_centered);
        icon = findViewById(R.id.icon);
        containerExtraViews = findViewById(R.id.container_extra_views);
        
        // Set Material Symbols font
        try {
            icon.typeface = Typeface.createFromAsset(context.assets, "font/material_symbols_rounded.ttf")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setText(title, text, "ic_image", extraViews);
    }


    fun setText(title: String, text: String, iconName: String, extraViews: List<View>? = null) {
        textTitle.text = title;
        textCentered.text = text;
        if (iconName.isEmpty()) {
            icon.visibility = GONE;
        } else {
            icon.visibility = VISIBLE;
            icon.text = Icons[iconName];
        }

        if(extraViews != null)
            for(view in extraViews)
                containerExtraViews.addView(view);
    }

    // Backward compatibility method
    fun setText(title: String, text: String, iconId: Int, extraViews: List<View>? = null) {
        setText(title, text, "ic_image", extraViews)
    }
}