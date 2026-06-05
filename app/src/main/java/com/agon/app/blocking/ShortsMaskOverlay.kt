package com.agon.app.blocking

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.agon.app.R
import com.agon.app.utils.AppLogger

/**
 * Shortstop §3.3 Method A — **Overlay Masking**.
 *
 * The accessibility service injects a WindowManager overlay on top
 * of the addictive content (e.g. a YouTube Shorts player) while
 * leaving the rest of the app functional. The user can still use
 * the back button, the top app bar, and the bottom navigation —
 * only the player surface is masked.
 *
 * Two flavours:
 *  - **Full-screen mask**: covers the entire app window — used when
 *    the rule engine (blocked hours / quota / break) demands a
 *    hard block.
 *  - **Surgical mask**: covers only the bounding rect of the
 *    offending node — used for the regular Reels/Shorts detection
 *    so the user can still read comments, write DMs, etc.
 *
 * Both use [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY]
 * (added in API 26 for exactly this use case). The overlay is
 * application-level and survives activity recreations.
 */
class ShortsMaskOverlay(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** Currently-shown overlay, if any. Capped to 1 per service instance. */
    private var currentView: View? = null
    private var currentParams: WindowManager.LayoutParams? = null
    private var lastShownMs: Long = 0L

    /** True if an overlay is currently shown. */
    val isShowing: Boolean get() = currentView != null

    /**
     * Show a full-screen mask — used when the rule engine says
     * "block now" (quota, blocked hours, break).
     */
    @SuppressLint("InflateParams")
    fun showFullScreen(
        title: CharSequence,
        subtitle: CharSequence,
        onClose: () -> Unit,
        onTakeBreak: () -> Unit,
    ) {
        val root = LayoutInflater.from(context)
            .inflate(R.layout.layout_shorts_mask, null) as FrameLayout
        root.setBackgroundColor(Color.parseColor("#CC101317"))
        root.findViewById<TextView>(R.id.shorts_mask_title).text = title
        root.findViewById<TextView>(R.id.shorts_mask_subtitle).text = subtitle
        root.findViewById<View>(R.id.shorts_mask_close).setOnClickListener {
            dismiss()
            onClose()
        }
        root.findViewById<View>(R.id.shorts_mask_break).setOnClickListener {
            dismiss()
            onTakeBreak()
        }
        val params = baseParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.CENTER
        }
        showInternal(root, params)
    }

    /**
     * Show a surgical mask — covers only [boundsInScreen] within the
     * foreground app. Used for the regular Reels/Shorts detection
     * so the user can still use the rest of the app.
     */
    @SuppressLint("InflateParams")
    fun showSurgical(
        boundsInScreen: Rect,
        title: CharSequence,
        subtitle: CharSequence,
    ) {
        val root = LayoutInflater.from(context)
            .inflate(R.layout.layout_shorts_mask, null) as FrameLayout
        root.setBackgroundColor(Color.parseColor("#E6080A0F"))
        root.findViewById<TextView>(R.id.shorts_mask_title).text = title
        root.findViewById<TextView>(R.id.shorts_mask_subtitle).text = subtitle
        // Hide the action buttons for the surgical flavour — the
        // user can dismiss with a single tap on the mask itself.
        root.findViewById<View>(R.id.shorts_mask_close).visibility = View.GONE
        root.findViewById<View>(R.id.shorts_mask_break).visibility = View.GONE
        root.setOnClickListener { dismiss() }

        val params = baseParams().apply {
            width = boundsInScreen.width().coerceAtLeast(1)
            height = boundsInScreen.height().coerceAtLeast(1)
            gravity = Gravity.TOP or Gravity.START
            x = boundsInScreen.left
            y = boundsInScreen.top
        }
        showInternal(root, params)
    }

    /** Hide the overlay if one is shown. Idempotent. */
    fun dismiss() {
        val v = currentView ?: return
        try {
            windowManager.removeView(v)
        } catch (e: Exception) {
            AppLogger.w("ShortsMaskOverlay: removeView failed: ${e.message}")
        }
        currentView = null
        currentParams = null
    }

    /**
     * Return true if a fresh overlay is allowed now (debounce).
     * Use this to avoid the overlay flickering on every scroll
     * event.
     */
    fun canShowNow(now: Long = System.currentTimeMillis()): Boolean {
        if (currentView != null) return false
        return now - lastShownMs > BlockingConfig.SHORTSTOP_OVERLAY_COOLDOWN_MS
    }

    /** Time the last overlay was first shown, or 0. */
    fun lastShownTimeMs(): Long = lastShownMs

    private fun showInternal(view: View, params: WindowManager.LayoutParams) {
        dismiss()
        try {
            windowManager.addView(view, params)
            currentView = view
            currentParams = params
            lastShownMs = System.currentTimeMillis()
        } catch (e: Exception) {
            AppLogger.w("ShortsMaskOverlay: addView failed: ${e.message}")
            currentView = null
            currentParams = null
        }
    }

    /**
     * Build the base [WindowManager.LayoutParams] for an overlay.
     *
     *  - `TYPE_ACCESSIBILITY_OVERLAY` (API 26+) is the right type
     *    for an accessibility service that wants to inject
     *    content above another app without requesting
     *    `SYSTEM_ALERT_WINDOW`.
     *  - `FLAG_NOT_FOCUSABLE` keeps the user focused on the
     *    underlying app (they can keep scrolling the feed).
     *  - `FLAG_LAYOUT_IN_SCREEN` ensures the mask uses the full
     *    screen real estate.
     */
    @Suppress("DEPRECATION")
    private fun baseParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
    }
}
