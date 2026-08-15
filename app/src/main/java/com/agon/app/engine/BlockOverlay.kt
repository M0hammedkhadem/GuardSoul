package com.agon.app.engine

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen blocking shield drawn above any app via the accessibility
 * overlay layer (no SYSTEM_ALERT_WINDOW needed for this window type).
 */
class BlockOverlay(private val service: AccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private val wm: WindowManager
        get() = service.getSystemService(WindowManager::class.java)

    val isShowing: Boolean get() = view != null

    fun show(
        title: String,
        message: String,
        autoHideMs: Long = 3500L,
        buttonLabel: String = "أخرجني من هنا",
        buttonGoesHome: Boolean = true,
        opaque: Boolean = false,
        secondaryLabel: String? = null,
        onSecondary: (() -> Unit)? = null,
    ) {
        handler.post {
            // A repeat block while the shield is still visible must refresh the
            // message and extend the display time — never be silently ignored.
            handler.removeCallbacksAndMessages(null)
            view?.let { v -> runCatching { wm.removeView(v) } }
            view = null
            val root = buildView(title, message, buttonLabel, buttonGoesHome, opaque, secondaryLabel, onSecondary)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            )
            // The shield MUST appear for every block. addView can transiently
            // fail (window token churn right after a BACK/HOME global action),
            // so retry with backoff instead of failing silently.
            attachWithRetry(root, params, autoHideMs, attemptsLeft = 3)
        }
    }

    private fun attachWithRetry(
        root: View,
        params: WindowManager.LayoutParams,
        autoHideMs: Long,
        attemptsLeft: Int,
    ) {
        val added = runCatching { wm.addView(root, params) }.isSuccess
        if (added) {
            view = root
            handler.postDelayed({ hide() }, autoHideMs)
        } else if (attemptsLeft > 0) {
            handler.postDelayed(
                { attachWithRetry(root, params, autoHideMs, attemptsLeft - 1) },
                250L,
            )
        }
    }

    fun hide() {
        handler.post {
            view?.let { v -> runCatching { wm.removeView(v) } }
            view = null
        }
    }

    private fun buildView(
        title: String,
        message: String,
        buttonLabel: String,
        buttonGoesHome: Boolean,
        opaque: Boolean,
        secondaryLabel: String?,
        onSecondary: (() -> Unit)?,
    ): View {
        val density = service.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // Opaque = camouflage mode: fully hides the content behind.
            setBackgroundColor(Color.parseColor(if (opaque) "#FF0A121B" else "#F20A121B"))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setOnClickListener { /* swallow touches */ }
        }

        val shield = TextView(service).apply {
            text = "\uD83D\uDEE1\uFE0F"
            textSize = 64f
            gravity = Gravity.CENTER
        }
        container.addView(shield)

        val titleView = TextView(service).apply {
            text = title
            setTextColor(Color.parseColor("#5BC6EE"))
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(20), dp(24), dp(6))
        }
        container.addView(titleView)

        val messageView = TextView(service).apply {
            text = message
            setTextColor(Color.parseColor("#C7D5E0"))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(32), 0, dp(32), dp(28))
        }
        container.addView(messageView)

        val button = TextView(service).apply {
            text = buttonLabel
            setTextColor(Color.parseColor("#06222F"))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(40), dp(14), dp(40), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#5BC6EE"))
            }
            setOnClickListener {
                service.performGlobalAction(
                    if (buttonGoesHome) AccessibilityService.GLOBAL_ACTION_HOME
                    else AccessibilityService.GLOBAL_ACTION_BACK,
                )
                hide()
            }
        }
        container.addView(button)

        // Optional secondary "continue anyway" button (keyword shield option).
        if (secondaryLabel != null) {
            val secondary = TextView(service).apply {
                text = secondaryLabel
                setTextColor(Color.parseColor("#8FA3B5"))
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(dp(32), dp(18), dp(32), dp(6))
                setOnClickListener {
                    onSecondary?.invoke()
                    hide()
                }
            }
            container.addView(secondary)
        }

        return container
    }
}
