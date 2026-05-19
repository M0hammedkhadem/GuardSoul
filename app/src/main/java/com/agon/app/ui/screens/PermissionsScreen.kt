package com.agon.app.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.agon.app.R
import com.agon.app.utils.PermissionUtils
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.GuardianViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    viewModel: GuardianViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.updatePermission("vpn", PermissionUtils.isVpnPrepared(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updatePermission("accessibility", PermissionUtils.isAccessibilityServiceEnabled(context))
                viewModel.updatePermission("vpn", PermissionUtils.isVpnPrepared(context))
                viewModel.updatePermission("device_admin", PermissionUtils.isDeviceAdminEnabled(context))
                viewModel.updatePermission("overlay", PermissionUtils.isOverlayPermissionGranted(context))
                viewModel.updatePermission("usage", PermissionUtils.isUsageAccessGranted(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val grantedCount = listOf(
        state.accessibilityGranted,
        state.vpnGranted,
        state.deviceAdminGranted,
        state.overlayGranted,
        state.usageAccessGranted
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
            // Progress Card
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
                        Text("$grantedCount/5", color = if (grantedCount == 5) success else warning, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { grantedCount / 5f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (grantedCount == 5) success else warning,
                        trackColor = surfaceLight
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (grantedCount == 5) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (grantedCount == 5) success else textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (grantedCount == 5) stringResource(R.string.status_all_granted) else stringResource(R.string.status_grant_permissions),
                            color = textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Permission Cards
            val context = LocalContext.current
            
            PermissionCard(
                title = stringResource(R.string.perm_accessibility),
                desc = stringResource(R.string.desc_accessibility),
                instruction = stringResource(R.string.instruction_accessibility),
                color = primary,
                icon = Icons.Default.Accessibility,
                isGranted = state.accessibilityGranted,
                onGrant = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            PermissionCard(
                title = stringResource(R.string.perm_vpn),
                desc = stringResource(R.string.desc_vpn),
                instruction = stringResource(R.string.instruction_vpn),
                color = success,
                icon = Icons.Default.VpnKey,
                isGranted = state.vpnGranted,
                onGrant = {
                    val intent = android.net.VpnService.prepare(context)
                    if (intent != null) {
                        vpnLauncher.launch(intent)
                    } else {
                        viewModel.updatePermission("vpn", true)
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
                isGranted = state.deviceAdminGranted,
                onGrant = {
                    val componentName = android.content.ComponentName(context, com.agon.app.receivers.GuardianDeviceAdminReceiver::class.java)
                    val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for Uninstall Protection.")
                    }
                    context.startActivity(intent)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                title = stringResource(R.string.perm_overlay),
                desc = stringResource(R.string.desc_overlay),
                instruction = stringResource(R.string.instruction_overlay),
                color = warning,
                icon = Icons.Default.Layers,
                isGranted = state.overlayGranted,
                onGrant = {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                title = stringResource(R.string.perm_usage),
                desc = stringResource(R.string.desc_usage),
                instruction = stringResource(R.string.instruction_usage),
                color = accent,
                icon = Icons.Default.DataUsage,
                isGranted = state.usageAccessGranted,
                onGrant = {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Privacy Note
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
    onGrant: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

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
                onClick = { if (!isGranted) showDialog = true },
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

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = surface,
            title = { Text("Grant $title", color = text) },
            text = {
                Column {
                    Text(desc, color = textSecondary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.label_how_to_grant), fontWeight = FontWeight.Bold, color = text)
                    Text(instruction, color = textMuted, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onGrant()
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = color)
                ) {
                    Text(stringResource(R.string.btn_confirm_granted), color = surface)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.btn_cancel), color = textSecondary)
                }
            }
        )
    }
}
