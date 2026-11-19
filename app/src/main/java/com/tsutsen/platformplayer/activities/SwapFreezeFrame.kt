package com.tsutsen.platformplayer.activities

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.drawToBitmap
import java.util.concurrent.atomic.AtomicLong

/**
 * Freeze-frame mask for the "Swap screens" display relaunch.
 *
 * Toggling the setting relaunches MainActivity onto another display
 * (ActivityOptions.setLaunchDisplayId). That tears the old window down and
 * recreates it on the target, which flashes a hole / splash screen on both
 * panels. This holder smooths it over:
 *
 *  - BEFORE the relaunch, the old instance screenshots its content and shows
 *    it as a fullscreen overlay inside its own decorView, so the display
 *    being left keeps showing a static frame while the window dies.
 *  - The bitmap travels to the NEW instance through this process-wide holder
 *    (same process, same classloader). The new instance installs the same
 *    overlay on top of its content in onCreate and fades it out (~250 ms)
 *    once the first real frame is drawn, then removes and recycles it.
 *
 * Everything is guarded: a failed capture or overlay simply means the
 * relaunch falls back to the plain splash-screen behavior. The holder is
 * cleared when consumed, when the consuming activity is destroyed, and via a
 * generation-based timeout so a bitmap can never linger if the relaunch turns
 * out to be an in-place task move (no new instance, no onCreate consumer).
 */
object SwapFreezeFrame {
    /** Freeze frame pending delivery to a relaunched instance on [displayId]. */
    @Volatile
    private var pendingBitmap: Bitmap? = null

    @Volatile
    private var pendingDisplayId: Int = Int.MIN_VALUE

    /** Bumps on every capture; used to expire stale pending frames. */
    private val generation = AtomicLong(0)

    private const val FADE_OUT_MS = 250L
    private const val PENDING_TIMEOUT_MS = 3000L

    /** Max time the OLD instance's static mask may cover the window. */
    private const val OLD_OVERLAY_MAX_MS = 600L

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Capture the current window content. Null on failure (caller falls back). */
    fun capture(activity: android.app.Activity): Bitmap? = try {
        activity.window.decorView.drawToBitmap()
    } catch (e: Throwable) {
        android.util.Log.w("SwapFreezeFrame", "capture failed: ${e.message}")
        null
    }

    /**
     * Old instance: stash the frame for the new instance and cover this
     * window's content so the display being left never shows a hole while
     * the system tears the window down. [targetDisplayId] is the display the
     * relaunched instance will land on — the pending frame is only delivered
     * to an instance that actually started there. Returns true when the mask
     * is up.
     */
    fun prepareRelaunch(
        activity: android.app.Activity,
        bitmap: Bitmap?,
        targetDisplayId: Int,
    ): Boolean {
        if (bitmap == null || bitmap.isRecycled) return false
        val gen = generation.incrementAndGet()
        pendingBitmap = bitmap
        pendingDisplayId = targetDisplayId
        scheduleExpiry(gen)
        // Best-effort: hide the real content behind a static frame. If the
        // window is already detaching this may throw — the relaunch proceeds
        // either way.
        val installed = installOverlay(activity, bitmap, fadeOut = false)
        if (installed) {
            // Safety net: if the relaunch turns out to be an in-place task
            // move (same instance keeps running, no new consumer), don't
            // leave a permanent mask over live content — fade it out.
            // When the window is torn down first, this animates a detached
            // view and is a harmless no-op.
            mainHandler.postDelayed({
                try {
                    overlayRef?.get()?.let { fadeOutAndRemove(it, bitmap, recycle = false) }
                } catch (_: Throwable) {}
            }, OLD_OVERLAY_MAX_MS)
        }
        return installed
    }

    /** Weak ref to the old instance's overlay, for the delayed fade safety net. */
    private var overlayRef: java.lang.ref.WeakReference<ImageView>? = null

    private fun fadeOutAndRemove(overlay: ImageView, bitmap: Bitmap?, recycle: Boolean) {
        overlay.animate()
            .alpha(0f)
            .setDuration(FADE_OUT_MS)
            .withEndAction {
                try {
                    (overlay.parent as? ViewGroup)?.removeView(overlay)
                    if (recycle && bitmap != null && !bitmap.isRecycled) bitmap.recycle()
                } catch (_: Throwable) {}
            }
            .start()
    }

    /**
     * New instance (called from onCreate, after setContent): if a freeze
     * frame is pending for THIS display, cover the freshly-inflated content
     * with it and fade it out once the first real frame is drawn, then
     * remove + recycle. Returns true when an overlay was installed.
     */
    fun consumeOnCreate(activity: android.app.Activity): Boolean {
        val bitmap = pendingBitmap ?: return false
        if (ownDisplayId(activity) != pendingDisplayId) return false
        pendingBitmap = null
        pendingDisplayId = Int.MIN_VALUE
        generation.incrementAndGet() // invalidate any pending expiry for this frame
        if (!installOverlay(activity, bitmap, fadeOut = true)) {
            bitmap.recycle()
            return false
        }
        return true
    }

    /** Clear any overlay this object installed (call from onDestroy). */
    fun detach(activity: android.app.Activity) {
        try {
            val decor = activity.window?.decorView as? ViewGroup ?: return
            for (i in decor.childCount - 1 downTo 0) {
                val child = decor.getChildAt(i)
                if (child is ImageView && child.tag == TAG) {
                    decor.removeView(child)
                }
            }
        } catch (e: Throwable) {
            android.util.Log.w("SwapFreezeFrame", "detach failed: ${e.message}")
        }
        // Note: the PENDING frame is deliberately NOT cleared here. During a
        // display relaunch the OLD instance is destroyed before the NEW one
        // runs onCreate, and its onDestroy would otherwise delete the frame
        // the new instance is about to consume. Stale frames are reaped by
        // the generation-based expiry in prepareRelaunch instead.
    }

    private fun installOverlay(
        activity: android.app.Activity,
        bitmap: Bitmap,
        fadeOut: Boolean,
    ): Boolean = try {
        val decor = activity.window.decorView as ViewGroup
        // Idempotency: never stack two masks on the same window.
        detachOverlays(decor)
        val overlay = ImageView(activity)
        overlay.tag = TAG
        overlay.setImageBitmap(bitmap)
        overlay.scaleType = ImageView.ScaleType.FIT_XY
        overlay.isClickable = true
        overlay.isFocusable = true
        overlay.contentDescription = null
        decor.addView(
            overlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        if (fadeOut) {
            OneShotPreDrawListener.add(overlay) {
                fadeOutAndRemove(overlay, bitmap, recycle = true)
                true
            }
        } else {
            overlayRef = java.lang.ref.WeakReference(overlay)
        }
        true
    } catch (e: Throwable) {
        android.util.Log.w("SwapFreezeFrame", "install failed: ${e.message}")
        false
    }

    private fun detachOverlays(decor: ViewGroup) {
        for (i in decor.childCount - 1 downTo 0) {
            val child = decor.getChildAt(i)
            if (child is ImageView && child.tag == TAG) decor.removeView(child)
        }
    }

    private fun ownDisplayId(activity: android.app.Activity): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.displayId ?: android.view.Display.DEFAULT_DISPLAY
        } else {
            android.view.Display.DEFAULT_DISPLAY
        }

    private fun scheduleExpiry(gen: Long) {
        mainHandler.postDelayed({
            if (generation.get() == gen) {
                pendingBitmap?.let { try { it.recycle() } catch (_: Throwable) {} }
                pendingBitmap = null
                pendingDisplayId = Int.MIN_VALUE
            }
        }, PENDING_TIMEOUT_MS)
    }

    private const val TAG = "swap_freeze_frame_overlay"
}
