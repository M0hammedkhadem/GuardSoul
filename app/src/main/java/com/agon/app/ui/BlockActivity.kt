package com.agon.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.agon.app.R
import timber.log.Timber

class BlockActivity : Activity() {

    companion object {
        const val EXTRA_APP_NAME = "app_name"
        private const val AUTO_DISMISS_MS = 10_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }

        setContentView(R.layout.activity_block)

        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "App"

        findViewById<TextView>(R.id.tv_overlay_app).text =
            "$appName is currently blocked by GuardSoul."

        findViewById<Button>(R.id.btn_go_back).setOnClickListener {
            Timber.d("Go Back pressed in BlockActivity, finishing")
            handler.removeCallbacksAndMessages(null)
            finish()
        }

        autoDismissRunnable = Runnable {
            Timber.d("BlockActivity auto-dismissed after 10s")
            finish()
        }
        handler.postDelayed(autoDismissRunnable!!, AUTO_DISMISS_MS)
    }

    override fun onBackPressed() {
        Timber.d("System back pressed in BlockActivity — ignored")
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        autoDismissRunnable = null
    }
}
