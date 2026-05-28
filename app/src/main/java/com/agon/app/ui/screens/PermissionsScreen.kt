package com.agon.app.ui.screens

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.agon.app.GuardianApp
import com.agon.app.GuardianDeviceAdminReceiver
import com.agon.app.R
import com.agon.app.utils.AccessibilityUtils
import com.agon.app.utils.PermissionUtils
import com.agon.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as GuardianApp
    val settings = app.repository.getAppSettings()

    val accessibilityGranted by settings.permAccessibilityFlow.collectAsState(initial = false)
    val vpnGranted by settings.permVpnFlow.collectAsState(initial = false)
    val deviceAdminGranted by settings.permAdminFlow.collectAsState(initial = false)
    val overlayGranted by settings.permOverlayFlow.collectAsState(initial = false)
    val usageAccessGranted by settings.permUsageFlow.collectAsState(initial = false)
    val notificationGranted by settings.permNotificationsFlow.collectAsState(initial = false)
    
    val scrollState = rememberScrollState()

    fun refreshAllPermissions() {
        PermissionUtils.syncPermissionsWithCache(context, settings)
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            refreshAllPermissions()
        }
    }

    val adminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        refreshAllPermissions()
    }

    LaunchedEffect(Unit) { refreshAllPermissions() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAllPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val totalPermissions = 6
    val grantedCount = listOf(
        accessibilityGranted,
        vpnGranted,
        deviceAdminGranted,
        overlayGranted,
        usageAccessGranted,
        notificationGranted
    ).count { it }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_permissions_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.contentdesc_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    titleContentColor = text,
                    navigationIconContentColor = text
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = card),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.card_permission_status), fontWeight = FontWeight.Bold, color = text, fontSize = 16.sp)
                        Text(stringResource(R.string.permissions_progress, grantedCount), color = if (grantedCount == totalPermissions) success else warning, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { grantedCount / totalPermissions.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (grantedCount == totalPermissions) success else warning,
                        trackColor = surfaceLight
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (grantedCount == totalPermissions) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (grantedCount == totalPermissions) success else textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (grantedCount == totalPermissions) stringResource(R.string.status_all_granted) else stringResource(R.string.status_grant_permissions),
                            color = textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            PermissionCard(
                title = stringResource(R.string.perm_accessibility),
                desc = stringResource(R.string.desc_accessibility),
                instruction = stringResource(R.string.instruction_accessibility),
                color = primary,
                icon = Icons.Default.Accessibility,
                isGranted = accessibilityGranted,
                onGrant = {
                    if (!accessibilityGranted) {
                        AccessibilityUtils.openAccessibilitySettings(context)
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                title = stringResource(R.string.perm_vpn),
                desc = stringResource(R.string.desc_vpn),
                instruction = stringResource(R.string.instruction_vpn),
                color = success,
                icon = Icons.Default.VpnKey,
                isGranted = vpnGranted,
                onGrant = {
                    if (!vpnGranted) {
                        val intent = VpnService.prepare(context)
                        if (intent != null) {
                            vpnLauncher.launch(intent)
                        } else {
                            refreshAllPermissions()
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                title = stringResource(R.string.perm_device_admin),
                desc = stringResource(R.string.desc_device_admin),
                instruction = stringResource(R.string.instruction_device_admin),
                color = danger,
                icon = Icons.Default.Security,
                isGranted = deviceAdminGranted,
                onGrant = {
                    if (!deviceAdminGranted) {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, GuardianDeviceAdminReceiver::class.java))
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.device_admin_explanation))
                        }
                        adminLauncher.launch(intent)
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                title = stringResource(R.string.perm_overlay),
                desc = stringResource(R.string.desc_overlay),
                instruction = stringResource(R.string.instruction_overlay),
                color = warning,
                icon = Icons.Default.Layers,
                isGranted = overlayGranted,
                onGrant = {
                    if (!overlayGranted) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                title = stringResource(R.string.perm_usage),
                desc = stringResource(R.string.desc_usage),
                instruction = stringResource(R.string.instruction_usage),
                color = accent,
                icon = Icons.Default.DataUsage,
                isGranted = usageAccessGranted,
                onGrant = {
                    if (!usageAccessGranted) {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            // On some Android versions, we can try to point to the specific package
                            // though it's not officially supported for this intent by all.
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback if the specific intent fails
                            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                title = stringResource(R.string.perm_notifications),
                desc = stringResource(R.string.desc_notifications),
                instruction = stringResource(R.string.instruction_notifications),
                color = accent,
                icon = Icons.Default.Notifications,
                isGranted = notificationGranted,
                onGrant = {
                    if (!notificationGranted) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(this)
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = textMuted, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.privacy_permissions_note), color = textMuted, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    desc: String,
    instruction: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isGranted: Boolean,
    onGrant: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = card),
        border = BorderStroke(1.dp, if (isGranted) color.copy(alpha = 0.5f) else cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = color)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = title, fontWeight = FontWeight.Bold, color = text, fontSize = 16.sp)
                        if (!isGranted) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = danger.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, danger.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = stringResource(R.string.badge_required),
                                    color = danger,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = desc, color = textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = surfaceLight,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = instruction,
                    color = textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) color.copy(alpha = 0.1f) else color,
                    contentColor = if (isGranted) color else surface
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isGranted) {
                    Text(stringResource(R.string.btn_granted), fontWeight = FontWeight.Bold)
                } else {
                    Text(stringResource(R.string.btn_grant_permission), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
