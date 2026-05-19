package com.agon.app.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.agon.app.data.GuardianRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling Guardian Device Admin will remove Uninstall Protection and may leave your device unprotected."
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        CoroutineScope(Dispatchers.IO).launch {
            val repo = GuardianRepository(context)
            repo.updatePermission("device_admin", true)
            repo.updateUninstallProtection(true)
        }
        Toast.makeText(context, "Guardian Device Admin Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        CoroutineScope(Dispatchers.IO).launch {
            val repo = GuardianRepository(context)
            repo.updatePermission("device_admin", false)
            repo.updateUninstallProtection(false)
        }
        Toast.makeText(context, "Guardian Device Admin Disabled", Toast.LENGTH_SHORT).show()
    }
}
