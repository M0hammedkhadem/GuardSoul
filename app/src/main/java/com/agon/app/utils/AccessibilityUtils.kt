package com.agon.app.utils

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Utility class for managing and checking Android Accessibility Services.
 */
object AccessibilityUtils {

    fun isServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        val service = "${context.packageName}/${serviceClass.name}"
        val enabledServices = try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
        } catch (e: Exception) {
            ""
        }
        return enabledServices.split(':').any { it.equals(service, true) }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            // Fallback to main settings if accessibility settings cannot be opened directly
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
