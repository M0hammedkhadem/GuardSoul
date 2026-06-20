package com.agon.app.utils

import android.content.Context
import android.os.Build
import com.agon.app.GuardianApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * SafeModeDetector - كشف وضع Safe Mode
 *
 * في وضع Safe Mode، يتم تعطيل جميع التطبيقات التابعة لجهات خارجية.
 * هذا يعني أن GuardSoul لن يعمل. يجب:
 * 1. كشف دخول Safe Mode عند Boot
 * 2. تسجيل الحدث
 * 3. محاولة إعادة تشغيل الخدمات عند الخروج من Safe Mode
 */
object SafeModeDetector {

    fun isInSafeMode(context: Context): Boolean {
        val safeMode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                context.packageManager.isSafeMode
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.w(e, "SafeModeDetector: failed to check safe mode")
            false
        }
        Timber.d("SafeModeDetector: safeMode=$safeMode")
        return safeMode
    }

    fun handleSafeModeDetected(context: Context) {
        Timber.w("SafeModeDetector: 🔴 SAFE MODE DETECTED!")
        try {
            val app = context.applicationContext as GuardianApp
            CoroutineScope(Dispatchers.IO).launch {
                app.repository.recordBlock("system", "Safe Mode", "safe_mode_detected")
            }
        } catch (e: Exception) {
            Timber.w(e, "SafeModeDetector: failed to log event")
        }
    }

    fun checkAtBoot(context: Context) {
        if (isInSafeMode(context)) {
            handleSafeModeDetected(context)
        } else {
            Timber.d("SafeModeDetector: normal boot, safe mode not detected")
        }
    }
}
