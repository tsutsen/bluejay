package com.futo.platformplayer.views.containers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.constraintlayout.motion.widget.MotionLayout
import com.futo.platformplayer.R
import kotlin.math.abs

class CustomMotionLayout(context: Context, attributeSet: AttributeSet? = null) :
    MotionLayout(context, attributeSet) {

    private val viewToDetectTouch by lazy {
        findViewById<View>(R.id.layout_player_container) //TODO move to Attributes
    }
    private val viewToDetectTouch2 by lazy {
        findViewById<View>(R.id.minimize_controls) //TODO move to Attributes
    }

    private var savedActionDown: MotionEvent? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // intercepting touch events is necessary because something to do with PlayerControlView makes things not work
    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        val ev = event ?: return super.onInterceptTouchEvent(null)

        // special touch interception logic is unnecessary if interaction is disabled
        if (!isInteractionEnabled) {
            return super.onInterceptTouchEvent(ev)
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val viewRect = Rect()
                viewToDetectTouch.getHitRect(viewRect)
                val isInView = viewRect.contains(ev.x.toInt(), ev.y.toInt())
                viewToDetectTouch2.getHitRect(viewRect)
                val isInView2 = viewRect.contains(ev.x.toInt(), ev.y.toInt())

                // Don't intercept touches if they are outside of the player or the mini player controls
                if (!isInView && !isInView2) {
                    return false
                }

                val thing = super.onInterceptTouchEvent(ev)
                // If the MotionLayout is already intercepting this touch then don't track it
                if (thing) {
                    return true
                }

                // MotionLayout didn't intercept the touch but the touch is over the player/mini controls views
                // in the future the class will
                // save the touch event for later
                // need to replay this initial touch to the MotionLayout if it ends up turning into a drag
                // return false because that matches the return from the super call above
                savedActionDown?.recycle() // Recycle the old event to prevent memory leaks (if for some reason it wasn't cleaned up in the other code paths)
                savedActionDown = MotionEvent.obtain(ev)

                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val localSavedActionDown = savedActionDown

                // only handle the move event if there is a saved action stored
                // then check to see if it has turned into a drag
                if (localSavedActionDown != null) {
                    val dy = abs(ev.y - localSavedActionDown.y)
                    if (dy > touchSlop) {
                        // if it has turned into a drag then
                        // replay the down action saved earlier
                        // clean up our data
                        // return true so that the MotionLayout's onTouchEvent will receive future events for this gesture
                        //
                        // it is necessary to replay the down action because otherwise MotionLayout will not always initialize the drag correctly
                        super.onTouchEvent(localSavedActionDown)
                        localSavedActionDown.recycle() // Clean up the saved event after replaying
                        savedActionDown = null
                        return true
                    }
                }
            }

            // if it's an up or cancel action clean up our tracking
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                savedActionDown?.recycle()
                savedActionDown = null
            }
        }

        // since the function hasn't handled the even this far send it to the parent class
        return super.onInterceptTouchEvent(ev)
    }

    // onTouchEvent is necessary to make sure that only touch and drag on the video triggers the animation (instead of everywhere on the screen)
    @SuppressLint("ClickableViewAccessibility") // pretty sure this issue doesn't apply
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val ev = event ?: return super.onTouchEvent(null)

        // special touch event handling logic is unnecessary if interaction is disabled
        if (!isInteractionEnabled) {
            return super.onTouchEvent(ev)
        }

        val viewRect = Rect()
        viewToDetectTouch.getHitRect(viewRect)
        val isInView = viewRect.contains(ev.x.toInt(), ev.y.toInt())
        viewToDetectTouch2.getHitRect(viewRect)
        val isInView2 = viewRect.contains(ev.x.toInt(), ev.y.toInt())

        // don't want to handle touches outside of the player/mini controls views
        if ((!isInView && !isInView2) && event.actionMasked == MotionEvent.ACTION_DOWN) {
            return false
        }
        return super.onTouchEvent(event)
    }
}
