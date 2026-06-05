package com.agon.app.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.agon.app.GuardianDeviceAdminReceiver
import timber.log.Timber

object KnoxManager {

    private const val KNOX_SDK_PACKAGE = "com.samsung.android.knox.containercore"

    fun isKnoxDevice(): Boolean {
        return Build.MANUFACTURER.contains("samsung", ignoreCase = true) || isKnoxSdkAvailable()
    }

    fun isKnoxSdkAvailable(): Boolean {
        return try {
            Class.forName("com.samsung.android.knox.EnterpriseDeviceManager")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    private fun getKnoxDevicePolicyManager(context: Context): Any? {
        if (!isKnoxSdkAvailable()) return null
        return try {
            val edmClass = Class.forName("com.samsung.android.knox.EnterpriseDeviceManager")
            val getInstance = edmClass.getMethod("getInstance", Context::class.java)
            val edm = getInstance.invoke(null, context)
            edmClass.getMethod("getDevicePolicyManager").invoke(edm)
        } catch (e: Exception) {
            Timber.w(e, "KnoxManager: failed to get Knox DPM")
            null
        }
    }

    fun activateKnoxLicense(context: Context): Boolean {
        if (!isKnoxSdkAvailable()) return false
        return try {
            val edmClass = Class.forName("com.samsung.android.knox.EnterpriseDeviceManager")
            val getInstance = edmClass.getMethod("getInstance", Context::class.java)
            val edm = getInstance.invoke(null, context)
            val licenseMethod = edmClass.getMethod("activateLicense", String::class.java)
            licenseMethod.invoke(edm, context.packageName) as Boolean
        } catch (e: Exception) {
            Timber.w(e, "KnoxManager: failed to activate Knox license")
            false
        }
    }

    fun disableApplicationWithKnox(context: Context, packageName: String): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(component)) return false
        
        return try {
            if (isKnoxSdkAvailable()) {
                val knoxDpm = getKnoxDevicePolicyManager(context) ?: return false
                // Issue #181: Use int.class (javaPrimitiveType) instead of Integer.class
                val disableMethod = knoxDpm.javaClass.getMethod(
                    "disableApplication",
                    ComponentName::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType ?: Int::class.java
                )
                disableMethod.invoke(knoxDpm, component, packageName, 0)
                true
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    dpm.setApplicationHidden(component, packageName, true)
                    true
                } else false
            }
        } catch (e: Exception) {
            Timber.w(e, "KnoxManager: failed to disable app $packageName")
            false
        }
    }

    fun enableApplicationWithKnox(context: Context, packageName: String): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(component)) return false
        
        return try {
            if (isKnoxSdkAvailable()) {
                val knoxDpm = getKnoxDevicePolicyManager(context) ?: return false
                // Issue #181: Use int.class (javaPrimitiveType)
                val enableMethod = knoxDpm.javaClass.getMethod(
                    "enableApplication",
                    ComponentName::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType ?: Int::class.java
                )
                enableMethod.invoke(knoxDpm, component, packageName, 0)
                true
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    dpm.setApplicationHidden(component, packageName, false)
                    true
                } else false
            }
        } catch (e: Exception) {
            Timber.w(e, "KnoxManager: failed to enable app $packageName")
            false
        }
    }
}
