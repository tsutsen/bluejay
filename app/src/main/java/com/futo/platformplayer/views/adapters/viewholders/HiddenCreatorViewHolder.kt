package com.futo.platformplayer.views.adapters.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.views.adapters.AnyAdapter

data class HiddenCreatorViewHolderData(val creatorUrl: String)

class HiddenCreatorViewHolder(viewGroup: ViewGroup) :
    AnyAdapter.AnyViewHolder<HiddenCreatorViewHolderData>(
        LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.list_hidden_creator, viewGroup, false)
    ) {
    var data: HiddenCreatorViewHolderData? = null
    private val _creatorLabel: TextView = _view.findViewById(R.id.text_creator_name)

    val onUnhide = Event0()

    init {
        val unhideButton: ImageButton = _view.findViewById(R.id.button_trash)
        unhideButton.setOnClickListener {
            onUnhide.emit()
        }
    }

    override fun bind(value: HiddenCreatorViewHolderData) {
        _creatorLabel.text = value.creatorUrl
        data = value
    }
}