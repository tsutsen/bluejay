package com.futo.platformplayer.activities

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateMeta
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.adapters.viewholders.HiddenCreatorViewHolder
import com.futo.platformplayer.views.adapters.viewholders.HiddenCreatorViewHolderData
import com.futo.platformplayer.views.buttons.BigButton

class UnhideCreatorsActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unhide_creators)
        setNavigationBarColorAndIcons()

        val backButton: ImageButton = findViewById(R.id.button_back)
        val zeroState: LinearLayout = findViewById(R.id.zero_state)
        val returnButton: BigButton = findViewById(R.id.return_button)
        val recyclerHiddenCreators: RecyclerView = findViewById(R.id.recycler_tabs)

        val items = ArrayList(StateMeta.instance.hiddenCreators.values.map {
            HiddenCreatorViewHolderData(it)
        })

        if (items.isEmpty()) {
            zeroState.visibility = View.VISIBLE
            recyclerHiddenCreators.visibility = View.GONE
        }

        returnButton.onClick.subscribe {
            onBackPressedDispatcher.onBackPressed()
        }

        recyclerHiddenCreators.asAny<HiddenCreatorViewHolderData, HiddenCreatorViewHolder>(items) {
            it.onUnhide.subscribe {
                it.data?.creatorUrl?.let { creatorUrl ->
                    StateMeta.instance.removeHiddenCreator(creatorUrl)

                    val position = items.indexOfFirst { item -> item.creatorUrl == creatorUrl }
                    if (position != -1) {
                        items.removeAt(position)
                        recyclerHiddenCreators.adapter!!.notifyItemRemoved(position)
                    }

                    if (items.isEmpty()) {
                        zeroState.visibility = View.VISIBLE
                        recyclerHiddenCreators.visibility = View.GONE
                    }
                }
            }
        }

        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    companion object {
        @Suppress("unused")
        private const val TAG = "UnhideCreatorsActivity"
    }
}
