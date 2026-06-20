package com.agon.app.utils

import android.content.Context
import com.agon.app.GuardianApp
import com.agon.app.utils.ServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Validates that all required permissions for enabled features are granted
 * before allowing shield activation.
 */
object ShieldPermissionValidator {

    data class PermissionCheckResult(
        val allGranted: Boolean,
        val missingPermissions: List<MissingPermission>
    )

    data class MissingPermission(
        val feature: String,
        val permissionName: String,
        val description: String,
        val action: (Context) -> Unit
    )

    /**
     * Checks if all required permissions for currently enabled features are granted.
     * Returns list of missing permissions with actions to grant them.
     *
     * Only MANDATORY permissions block shield activation:
     *  - Accessibility Service (required for ALL blocking)
     *  - Overlay Permission (required to show BlockActivity)
     *
     * CONDITIONAL permissions only block if the feature is enabled:
     *  - VPN (only if Safe Search is enabled)
     *  - Device Admin (only if Uninstall Protection is enabled)
     *
     * RECOMMENDED permissions are shown as warnings but never block activation:
     *  - Battery Optimization
     *  - Usage Access
     */
    suspend fun checkAllPermissions(context: Context): PermissionCheckResult {
        return withContext(Dispatchers.IO) {
            val settings = (context.applicationContext as GuardianApp).repository.getAppSettings()

            // Get current feature states
            val safeSearchEnabled = settings.safeSearchEnabledFlow.first()
            val uninstallProtectionEnabled = settings.uninstallProtectionEnabledFlow.first()

            val missing = mutableListOf<MissingPermission>()

            // 1. Accessibility Service - MANDATORY for ALL features
            val accessibilityGranted = AccessibilityUtils.isServiceEnabled(context, com.agon.app.services.GuardSoulAccessibilityService::class.java)
            if (!accessibilityGranted) {
                missing.add(MissingPermission(
                    feature = "الدرع (الأساس)",
                    permissionName = "خدمة إمكانية الوصول",
                    description = "ضرورية لجميع ميزات الحظر والرصد",
                    action = { AccessibilityUtils.openAccessibilitySettings(it) }
                ))
            }

            // 2. Overlay Permission - MANDATORY for BlockActivity
            if (!PermissionUtils.isOverlayGranted(context)) {
                missing.add(MissingPermission(
                    feature = "شاشة الحظر",
                    permissionName = "العرض فوق التطبيقات الأخرى",
                    description = "لإظهار شاشة الحظر عند محاولة فتح تطبيق محظور",
                    action = { PermissionUtils.openOverlaySettings(it) }
                ))
            }

            // 3. VPN Permission - CONDITIONAL: only if Safe Search enabled
            if (safeSearchEnabled) {
                if (!ServiceManager.isVpnPrepared(context)) {
                    missing.add(MissingPermission(
                        feature = "البحث الآمن",
                        permissionName = "اتصال VPN",
                        description = "لتوجيه DNS عبر CleanBrowsing",
                        action = { ctx ->
                            val app = ctx.applicationContext as GuardianApp
                            val activity = app.currentActivity
                            if (activity != null) {
                                ServiceManager.prepareVpn(activity, ServiceManager.REQUEST_VPN_FROM_SHIELD)
                            }
                        }
                    ))
                }
            }

            // 4. Device Admin - CONDITIONAL: only if Uninstall Protection enabled
            if (uninstallProtectionEnabled) {
                val adminActive = ServiceManager.isDeviceAdminActive(context)
                if (!adminActive) {
                    missing.add(MissingPermission(
                        feature = "منع الحذف",
                        permissionName = "مدير الجهاز",
                        description = "لمنع إلغاء تثبيت التطبيق دون إذن",
                        action = { ctx ->
                            val app = ctx.applicationContext as GuardianApp
                            val activity = app.currentActivity
                            if (activity != null) {
                                ServiceManager.activateDeviceAdmin(activity)
                            }
                        }
                    ))
                }
            }

            // 5. Usage Access - RECOMMENDED (shown as warning, does NOT block shield activation)
            if (!PermissionUtils.isUsageAccessGranted(context)) {
                missing.add(MissingPermission(
                    feature = "كشف التطبيقات (موصى به)",
                    permissionName = "الوصول للاستخدام",
                    description = "لكشف التطبيق الأمامي وفرض الحدود الزمنية",
                    action = { PermissionUtils.openUsageAccessSettings(it) }
                ))
            }

            // 6. Battery Optimization - RECOMMENDED (shown as tip, does NOT block shield activation)
            if (!PermissionUtils.isBatteryOptimizationDisabled(context)) {
                missing.add(MissingPermission(
                    feature = "العمل في الخلفية (موصى به)",
                    permissionName = "استثناء تحسين البطارية",
                    description = "لضمان عمل الدرع بشكل موثوق في الخلفية",
                    action = { PermissionUtils.openBatteryOptimizationSettings(it) }
                ))
            }

            // allGranted = only MANDATORY permissions (accessibility + overlay + conditional features)
            val mandatoryMissing = missing.count { p ->
                !p.feature.contains("موصى به")
            }
            PermissionCheckResult(
                allGranted = mandatoryMissing == 0,
                missingPermissions = missing
            )
        }
    }

}