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
    ) {
        handler.post {
            // A repeat block while the shield is still visible must refresh the
            // message and extend the display time — never be silently ignored.
            handler.removeCallbacksAndMessages(null)
            view?.let { v -> runCatching { wm.removeView(v) } }
            view = null
            val root = buildView(title, message, buttonLabel, buttonGoesHome)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            )
            runCatching {
                wm.addView(root, params)
                view = root
                handler.postDelayed({ hide() }, autoHideMs)
            }
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
    ): View {
        val density = service.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F20A121B"))
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

        return container
    }
}
