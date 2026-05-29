package com.agon.app.ui.screens

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.GuardianDeviceAdminReceiver
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.utils.AccessibilityUtils
import com.agon.app.viewmodel.PermissionsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    viewModel: PermissionsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshPermissionStates()
            delay(2000)
        }
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refreshPermissionStates()
            viewModel.advanceGrantAll()
        } else {
            viewModel.refreshPermissionStates()
            viewModel.advanceGrantAll()
        }
    }

    val adminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshPermissionStates()
        viewModel.advanceGrantAll()
    }

    LaunchedEffect(uiState.currentGrantingPermission) {
        val permission = uiState.currentGrantingPermission ?: return@LaunchedEffect
        when (permission) {
            "overlay" -> {
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(this)
                }
            }
            "usage_access" -> {
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(this)
                }
            }
            "notifications" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(this)
                    }
                } else {
                    viewModel.advanceGrantAll()
                }
            }
            "accessibility" -> {
                AccessibilityUtils.openAccessibilitySettings(context)
            }
            "vpn" -> {
                val intent = VpnService.prepare(context)
                if (intent != null) {
                    vpnLauncher.launch(intent)
                } else {
                    viewModel.refreshPermissionStates()
                    viewModel.advanceGrantAll()
                }
            }
            "device_admin" -> {
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, GuardianDeviceAdminReceiver::class.java))
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.device_admin_explanation))
                    adminLauncher.launch(this)
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        snapshotFlow { lifecycleOwner.lifecycle.currentState }
            .collect { state ->
                if (state == Lifecycle.State.RESUMED) {
                    viewModel.refreshPermissionStates()
                    viewModel.advanceGrantAll()
                }
            }
    }

    val totalPermissions = 6
    val grantedCount = listOf(
        uiState.accessibilityGranted,
        uiState.vpnGranted,
        uiState.deviceAdminGranted,
        uiState.overlayGranted,
        uiState.usageAccessGranted,
        uiState.notificationGranted
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

            Spacer(modifier = Modifier.height(16.dp))

            if (grantedCount < totalPermissions) {
                GrantAllButton(
                    grantedCount = grantedCount,
                    totalCount = totalPermissions,
                    isGrantingAll = uiState.isGrantingAll,
                    currentGranting = uiState.currentGrantingPermission,
                    grantAllProgress = uiState.grantAllProgress,
                    grantAllTotal = uiState.grantAllTotal,
                    onStartGrantAll = { viewModel.startGrantAll() },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            PermissionCard(
                title = stringResource(R.string.perm_accessibility),
                desc = stringResource(R.string.desc_accessibility),
                instruction = stringResource(R.string.instruction_accessibility),
                color = primary,
                icon = Icons.Default.Accessibility,
                isGranted = uiState.accessibilityGranted,
                isCurrentInGrantAll = uiState.isGrantingAll && uiState.currentGrantingPermission == "accessibility",
                onGrant = {
                    if (!uiState.accessibilityGranted) {
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
                isGranted = uiState.vpnGranted,
                isCurrentInGrantAll = uiState.isGrantingAll && uiState.currentGrantingPermission == "vpn",
                onGrant = {
                    if (!uiState.vpnGranted) {
                        val intent = VpnService.prepare(context)
                        if (intent != null) {
                            vpnLauncher.launch(intent)
                        } else {
                            viewModel.refreshPermissionStates()
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
                isGranted = uiState.deviceAdminGranted,
                isCurrentInGrantAll = uiState.isGrantingAll && uiState.currentGrantingPermission == "device_admin",
                onGrant = {
                    if (!uiState.deviceAdminGranted) {
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
                isGranted = uiState.overlayGranted,
                isCurrentInGrantAll = uiState.isGrantingAll && uiState.currentGrantingPermission == "overlay",
                onGrant = {
                    if (!uiState.overlayGranted) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
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
                isGranted = uiState.usageAccessGranted,
                isCurrentInGrantAll = uiState.isGrantingAll && uiState.currentGrantingPermission == "usage_access",
                onGrant = {
                    if (!uiState.usageAccessGranted) {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
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
                isGranted = uiState.notificationGranted,
                isCurrentInGrantAll = uiState.isGrantingAll && uiState.currentGrantingPermission == "notifications",
                onGrant = {
                    if (!uiState.notificationGranted) {
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
private fun GrantAllButton(
    grantedCount: Int,
    totalCount: Int,
    isGrantingAll: Boolean,
    currentGranting: String?,
    grantAllProgress: Int,
    grantAllTotal: Int,
    onStartGrantAll: () -> Unit,
) {
    Button(
        onClick = onStartGrantAll,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = primary),
        enabled = !isGrantingAll
    ) {
        if (isGrantingAll) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = surface,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.grant_all_in_progress, grantAllProgress, grantAllTotal),
                fontWeight = FontWeight.Bold
            )
        } else {
            Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.btn_grant_all, grantedCount, totalCount),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    desc: String,
    instruction: String,
    color: Color,
    icon: ImageVector,
    isGranted: Boolean,
    isCurrentInGrantAll: Boolean = false,
    onGrant: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var shakeTrigger by remember { mutableIntStateOf(0) }
    val shakeOffset = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            shakeOffset.animateTo(8f)
            shakeOffset.animateTo(-8f)
            shakeOffset.animateTo(4f)
            shakeOffset.animateTo(-4f)
            shakeOffset.animateTo(0f)
        }
    }

    val checkScale by animateFloatAsState(
        targetValue = if (isGranted) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentInGrantAll) color.copy(alpha = 0.08f) else card
        ),
        border = BorderStroke(
            1.dp,
            when {
                isCurrentInGrantAll -> color
                isGranted -> color.copy(alpha = 0.5f)
                else -> cardBorder
            }
        )
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
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) color else color.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            color = text,
                            fontSize = 16.sp
                        )
                        AnimatedVisibility(
                            visible = isGranted,
                            enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .scale(checkScale)
                                        .clip(CircleShape)
                                        .background(success),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.contentdesc_check),
                                        tint = surface,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
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
                onClick = {
                    if (!isGranted) {
                        scope.launch { shakeTrigger++ }
                    }
                    onGrant()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isCurrentInGrantAll -> color
                        isGranted -> color.copy(alpha = 0.1f)
                        else -> color
                    },
                    contentColor = if (isGranted) color else surface
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isCurrentInGrantAll) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = surface,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (isGranted) {
                    Text(stringResource(R.string.btn_granted), fontWeight = FontWeight.Bold)
                } else {
                    Text(stringResource(R.string.btn_grant_permission), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
