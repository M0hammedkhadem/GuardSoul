package com.agon.app.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.agon.app.GuardianDeviceAdminReceiver
import timber.log.Timber

object KnoxManager {

    private const val KNOX_SDK_PACKAGE = "com.samsung.android.knox.containercore"
    private const val KNOX_MDM_PERMISSION = "com.samsung.android.knox.permission.KNOX_MDM"

    fun isKnoxDevice(): Boolean {
        return Build.MODEL.contains("Knox", ignoreCase = true) ||
               Build.DEVICE.contains("Knox", ignoreCase = true) ||
               isKnoxSdkAvailable()
    }

    fun isKnoxSdkAvailable(): Boolean {
        return try {
            Class.forName("com.samsung.android.knox.EnterpriseDeviceManager")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    fun isKnoxPackageInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(KNOX_SDK_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getKnoxDevicePolicyManager(context: Context): Any? {
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
                val disableMethod = knoxDpm.javaClass.getMethod(
                    "disableApplication",
                    ComponentName::class.java,
                    String::class.java,
                    Int::class.java
                )
                disableMethod.invoke(knoxDpm, component, packageName, 0)
                true
            } else {
                dpm.setApplicationHidden(component, packageName, true)
            }
        } catch (e: Exception) {
            Timber.w(e, "KnoxManager: failed to disable app")
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
                val enableMethod = knoxDpm.javaClass.getMethod(
                    "enableApplication",
                    ComponentName::class.java,
                    String::class.java,
                    Int::class.java
                )
                enableMethod.invoke(knoxDpm, component, packageName, 0)
                true
            } else {
                dpm.setApplicationHidden(component, packageName, false)
            }
        } catch (e: Exception) {
            Timber.w(e, "KnoxManager: failed to enable app")
            false
        }
    }
}
