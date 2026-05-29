package com.agon.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.R
import com.agon.app.services.DeviceOwnerService
import com.agon.app.ui.theme.background
import com.agon.app.ui.theme.cardBorder
import com.agon.app.ui.theme.primary
import com.agon.app.ui.theme.success
import com.agon.app.ui.theme.text
import com.agon.app.ui.theme.textSecondary
import com.agon.app.ui.theme.textMuted
import com.agon.app.ui.theme.surfaceLight
import com.agon.app.ui.theme.warning

class DeviceOwnerSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isDeviceOwner = DeviceOwnerService.isDeviceOwner(this)
        setContent {
            DeviceOwnerSetupScreen(
                isDeviceOwner = isDeviceOwner,
                onCopyAdbCommand = { copyToClipboard(it) },
                onFinish = { finish() }
            )
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ADB command", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.adb_command_copied, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun DeviceOwnerSetupScreen(
    isDeviceOwner: Boolean,
    onCopyAdbCommand: (String) -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val adbCommand = "adb shell dpm set-device-owner com.agon.app/.GuardianDeviceAdminReceiver"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        Icon(
            Icons.Default.AdminPanelSettings,
            null,
            tint = if (isDeviceOwner) success else warning,
            modifier = Modifier.size(72.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            if (isDeviceOwner) stringResource(R.string.device_owner_already_active)
            else stringResource(R.string.device_owner_setup_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = text,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        if (!isDeviceOwner) {
            Surface(
                color = surfaceLight,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.device_owner_setup_instructions),
                        fontSize = 13.sp,
                        color = textSecondary,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                color = warning.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, warning.copy(alpha = 0.2f))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = warning, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Factory reset required if no account is set up yet",
                        fontSize = 11.sp,
                        color = warning
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Surface(
                color = surfaceLight,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "ADB Command",
                        fontSize = 11.sp,
                        color = textMuted,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        adbCommand,
                        fontSize = 13.sp,
                        color = primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { onCopyAdbCommand(adbCommand) },
                colors = ButtonDefaults.buttonColors(containerColor = primary),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy ADB Command", fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onFinish,
            colors = ButtonDefaults.buttonColors(containerColor = primary),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Done", fontSize = 14.sp)
        }

        Spacer(Modifier.height(16.dp))
    }
}
