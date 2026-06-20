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
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import com.agon.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * SettingsBlockOverlay - حظر الوصول إلى إعدادات GuardSoul
 *
 * عند تفعيل Uninstall Protection:
 * 1. يراقب فتح صفحة "App Info" الخاصة بـ GuardSoul
 * 2. يعرض Overlay يحجب الشاشة برسالة "تم حظر الوصول"
 * 3. يسمح للمستخدم بالدخول إلى إعدادات التطبيقات الأخرى
 * 4. عند محاولة الوصول إلى Device Admin Apps → يحظر أيضاً
 */
class SettingsBlockOverlay(private val host: AccessibilityService) {

    companion object {
        private const val GUARDSOUL_PACKAGE = "com.agon.app"
        private const val SETTINGS_PACKAGE = "com.android.settings"
        private const val GOOGLE_SETTINGS_PACKAGE = "com.google.android.gms"
        private const val OVERLAY_DISMISS_MS = 4000L

        // View IDs that indicate we're in GuardSoul's app info page
        private val APP_INFO_VIEW_IDS = listOf(
            "com.android.settings:id/entity_header",
            "com.android.settings:id/entity_header_title",
            "com.android.settings:id/admin_details",
            "com.android.settings:id/device_admin_add_item"
        )

        // Content descriptions indicating Device Admin settings
        private val DEVICE_ADMIN_INDICATORS = listOf(
            "device admin", "device administrators", "admin apps",
            "administrators", "device_admin", "administrators"
        )
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var dismissRunnable: Runnable? = null

    @Volatile private var isEnabled = false

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Timber.d("SettingsBlockOverlay: enabled=$enabled")
    }

    /**
     * يُستدعى من onAccessibilityEvent عند تغيير النافذة.
     * يفحص إذا كان المستخدم في إعدادات GuardSoul أو Device Admin.
     */
    fun checkAndBlock(root: AccessibilityNodeInfo?, pkg: String) {
        if (!isEnabled) return
        if (pkg != SETTINGS_PACKAGE && pkg != GOOGLE_SETTINGS_PACKAGE) return

        val isGuardSoulSettings = isGuardSoulAppInfo(root)
        val isDeviceAdminPage = isDeviceAdminSettings(root)

        if (isGuardSoulSettings || isDeviceAdminPage) {
            Timber.w("SettingsBlockOverlay: BLOCKED access to ${if (isGuardSoulSettings) "GuardSoul App Info" else "Device Admin"}")
            showBlockOverlay(if (isGuardSoulSettings) "app_info" else "device_admin")
        } else {
            // User is in other settings — hide overlay if shown
            hideOverlay()
        }
    }

    // ─── Detection Logic ──────────────────────────────────────────────────

    private fun isGuardSoulAppInfo(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false

        // Check for app name "GuardSoul" in the title/header
        val title = findNodeText(root, "com.android.settings:id/entity_header_title")
            ?: findNodeText(root, "com.android.settings:id/app_name")
            ?: findNodeText(root, "com.android.settings:id/entity_header")
        if (title?.contains("GuardSoul", ignoreCase = true) == true ||
            title?.contains("Guard Soul", ignoreCase = true) == true) {
            return true
        }

        // Check for package name in app details
        val pkgText = findNodeText(root, "com.android.settings:id/pkg_name")
            ?: findNodeText(root, "com.android.settings:id/package_name")
        if (pkgText?.contains(GUARDSOUL_PACKAGE) == true) {
            return true
        }

        // Fallback: search for "GuardSoul" text anywhere in the tree
        return findTextInTree(root, "GuardSoul") || findTextInTree(root, GUARDSOUL_PACKAGE)
    }

    private fun isDeviceAdminSettings(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false

        // Check view IDs for device admin related elements
        for (viewId in APP_INFO_VIEW_IDS) {
            if (viewId.contains("admin")) {
                val matches = root.findAccessibilityNodeInfosByViewId(viewId)
                if (matches.isNotEmpty()) {
                    matches.forEach { it.recycle() }
                    return true
                }
            }
        }

        // Check content descriptions
        return findContentDescInTree(root, DEVICE_ADMIN_INDICATORS)
    }

    // ─── Tree Search Helpers ────────────────────────────────────────────────

    private fun findNodeText(root: AccessibilityNodeInfo, viewId: String): String? {
        val matches = root.findAccessibilityNodeInfosByViewId(viewId)
        if (matches.isNotEmpty()) {
            val text = matches.first().text?.toString()
            matches.forEach { it.recycle() }
            return text
        }
        return null
    }

    private fun findTextInTree(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) return true
        if (node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findTextInTree(child, text)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    private fun findContentDescInTree(node: AccessibilityNodeInfo?, indicators: List<String>): Boolean {
        if (node == null) return false
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        for (indicator in indicators) {
            if (desc.contains(indicator.lowercase())) return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findContentDescInTree(child, indicators)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    // ─── Overlay Display ────────────────────────────────────────────────────

    private fun showBlockOverlay(blockType: String) {
        if (overlayView != null) return // already showing

        mainHandler.post {
            try {
                val inflater = LayoutInflater.from(host.applicationContext)
                val view = inflater.inflate(R.layout.block_overlay, null)

                val titleText = if (blockType == "app_info") {
                    host.getString(R.string.settings_block_app_info_title)
                } else {
                    host.getString(R.string.settings_block_device_admin_title)
                }
                val descText = if (blockType == "app_info") {
                    host.getString(R.string.settings_block_app_info_desc)
                } else {
                    host.getString(R.string.settings_block_device_admin_desc)
                }

                view.findViewById<TextView>(R.id.tv_overlay_title).text = titleText
                view.findViewById<TextView>(R.id.tv_overlay_desc).text = descText
                view.findViewById<Button>(R.id.btn_overlay_dismiss).setOnClickListener {
                    hideOverlay()
                }

                val type = if (Build.VERSION.SDK_INT >= 26)
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply { gravity = Gravity.FILL }

                val wm = host.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
                wm.addView(view, params)
                overlayView = view

                // Auto dismiss after delay
                dismissRunnable = Runnable { hideOverlay() }
                mainHandler.postDelayed(dismissRunnable!!, OVERLAY_DISMISS_MS)

                Timber.d("SettingsBlockOverlay: shown for $blockType")
            } catch (e: Exception) {
                Timber.w(e, "SettingsBlockOverlay: failed to show")
            }
        }
    }

    fun hideOverlay() {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null

        overlayView?.let { view ->
            try {
                val wm = host.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(view)
            } catch (_: Exception) {}
            overlayView = null
        }
    }

    fun destroy() {
        hideOverlay()
        scope.launch { /* nothing special */ }
    }
}
