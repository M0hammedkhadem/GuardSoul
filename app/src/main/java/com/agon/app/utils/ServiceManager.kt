package com.agon.app.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.agon.app.GuardianApp
import com.agon.app.services.GuardSoulAccessibilityService
import timber.log.Timber

object ServiceManager {

    private const val ACCESSIBILITY_SERVICE = "com.agon.app/com.agon.app.services.GuardSoulAccessibilityService"

    fun setShieldActive(context: Context, enabled: Boolean) {
        val pm = context.packageManager
        val componentName = ComponentName(context, GuardSoulAccessibilityService::class.java)
        val newState = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        pm.setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP)

        Timber.i("Shield ${if (enabled) "enabled" else "disabled"}")
    }

    fun isShieldActive(context: Context): Boolean {
        val pm = context.packageManager
        val componentName = ComponentName(context, GuardSoulAccessibilityService::class.java)
        val componentEnabled = pm.getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        return try {
            val settingsActive = (context.applicationContext as GuardianApp).repository.getAppSettings().isShieldActiveSync()
            componentEnabled || settingsActive
        } catch (e: Exception) {
            componentEnabled
        }
    }
}
