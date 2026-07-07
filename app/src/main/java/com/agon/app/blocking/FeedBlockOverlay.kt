package com.agon.app.blocking

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.agon.app.R
import timber.log.Timber

/**
 * FeedBlockOverlay — رسالة حظر عابرة (transient) تُعرض عند حظر Shorts/Reels.
 *
 * تستخدم TYPE_ACCESSIBILITY_OVERLAY (مثل HaasEngine.showOverlay) لإظهار رسالة
 * واضحة للمستخدم دون إطلاق Activity كامل — يبقى المستخدم داخل التطبيق ويُعاد
 * إلى صفحته الرئيسية فقط (BACK)، مع ظهور الرسالة لحظياً.
 *
 * تصميم حسب رؤية التطبيق: "ارجاعه الى الصفحة الرئيسية قصرا مع اظهار رسالة
 * تعلم الشخص بأنه تم الحظر من طرف تطبيقنا".
 *
 * ملاحظات السلامة:
 *  - كل الاستدعاءات تُنفّذ على main looper (آمن من AccessibilityService main thread).
 *  - overlay سابق يُزال قبل إظهار جديد (لا تراكم).
 *  - إخفاء تلقائي بعد [AUTO_DISMISS_MS].
 */
class FeedBlockOverlay(private val host: AccessibilityService) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var dismissRunnable: Runnable? = null

    fun show(messageResId: Int = R.string.feed_block_message) {
        mainHandler.post {
            try {
                removeInternal()
                val message = host.getString(messageResId)

                val view = LayoutInflater.from(host.applicationContext)
                    .inflate(R.layout.feed_block_toast, null) as TextView
                view.text = message

                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                    y = -200 // slightly above center
                }

                val wm = host.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
                wm.addView(view, params)
                overlayView = view

                dismissRunnable = Runnable { removeInternal() }
                mainHandler.postDelayed(dismissRunnable!!, AUTO_DISMISS_MS)
                Timber.d("FeedBlockOverlay shown")
            } catch (e: Exception) {
                Timber.w(e, "FeedBlockOverlay: failed to show")
            }
        }
    }

    fun dismiss() {
        mainHandler.post { removeInternal() }
    }

    private fun removeInternal() {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null
        overlayView?.let { view ->
            try {
                val wm = host.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(view)
            } catch (_: Exception) { /* already removed */ }
            overlayView = null
        }
    }

    companion object {
        private const val AUTO_DISMISS_MS = 2500L
    }
}
