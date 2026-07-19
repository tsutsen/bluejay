package com.futo.platformplayer.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.GONE
import androidx.constraintlayout.widget.ConstraintLayout.inflate
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.utils.Icons
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.adapters.AnyAdapter.AnyViewHolder
import com.google.android.material.imageview.ShapeableImageView

class LibrarySection: ConstraintLayout {
    val textName: TextView;
    val imageNavigate: TextView;
    val recycler: RecyclerView;

    val noContent: NoResultsView;

    constructor(context: Context, attr: AttributeSet? = null) : super(context, attr) {
        inflate(context, R.layout.view_library_section, this);
        textName = findViewById(R.id.text_label)
        imageNavigate = findViewById(R.id.image_nav)
        recycler = findViewById(R.id.recycler_collection);
        noContent = findViewById(R.id.container_no_content);
        
        // Set Material Symbols font
        try {
            imageNavigate.typeface = Typeface.createFromAsset(context.assets, "font/material_symbols_rounded.ttf")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setNavIcon(iconName: String) {
        imageNavigate.text = Icons[iconName];
    }

    // Backward compatibility method
    fun setNavIcon(resId: Int) {
        imageNavigate.text = Icons["ic_image"];
    }

    fun setContentEmptyMessage(icon: String, msg: String) {
        noContent.setText("", msg, icon)
    }
    
    // Backward compatibility method
    fun setContentEmptyMessage(icon: Int, msg: String) {
        noContent.setText("", msg, "ic_image")
    }
    
    inline fun <T, reified V: AnyViewHolder<T>> getAnyAdapter(noinline onCreate: ((V)->Unit)? = null, orientation: Int = RecyclerView.HORIZONTAL): AnyAdapterView<T, V> {
        return recycler.asAny<T, V>(orientation, false, onCreate);
    }

    inline fun setSection(title: String, crossinline onOpen: (()->Unit)) {
        textName.text = title;
        imageNavigate.setOnClickListener { onOpen.invoke() };
    }

    fun setEmpty(title: String, txt: String, iconName: String) {
        noContent.isVisible = true;
        recycler.isVisible = false;
        noContent.setText(title, txt, iconName);
    }
    
    // Backward compatibility method
    fun setEmpty(title: String, txt: String, iconId: Int) {
        noContent.isVisible = true;
        recycler.isVisible = false;
        noContent.setText(title, txt, "ic_image");
    }
    
    fun clearEmpty() {
        noContent.isVisible = false;
        recycler.isVisible = true;
    }
}