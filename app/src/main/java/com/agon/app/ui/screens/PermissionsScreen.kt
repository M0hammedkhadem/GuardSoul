package com.agon.app.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.utils.AccessibilityUtils
import com.agon.app.utils.PermissionUtils
import com.agon.app.viewmodel.PermissionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    viewModel: PermissionsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            val grantedCount = listOf(
                uiState.accessibilityGranted,
                uiState.overlayPermission,
                uiState.usageAccess,
                uiState.batteryOptimization,
                uiState.notificationsGranted
            ).count { it }
            val totalCount = 5
            val allGranted = grantedCount == totalCount

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
                        Text(
                            stringResource(R.string.permissions_progress, grantedCount, totalCount),
                            color = if (allGranted) success else warning,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (allGranted) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (allGranted) success else textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (allGranted) stringResource(R.string.status_all_granted) else stringResource(R.string.status_grant_permissions),
                            color = textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Accessibility Service Card
            PermissionCard(
                title = stringResource(R.string.perm_accessibility),
                description = stringResource(R.string.desc_accessibility),
                instruction = stringResource(R.string.instruction_accessibility),
                isGranted = uiState.accessibilityGranted,
                icon = Icons.Default.Accessibility,
                onGrantClick = { AccessibilityUtils.openAccessibilitySettings(context) }
            )

            // 2. Overlay Card
            PermissionCard(
                title = stringResource(R.string.perm_overlay),
                description = stringResource(R.string.desc_overlay),
                instruction = stringResource(R.string.instruction_overlay),
                isGranted = uiState.overlayPermission,
                icon = Icons.Default.Layers,
                onGrantClick = { PermissionUtils.openOverlaySettings(context) }
            )

            // 4. Usage Stats Card
            PermissionCard(
                title = stringResource(R.string.perm_usage),
                description = stringResource(R.string.desc_usage),
                instruction = stringResource(R.string.instruction_usage),
                isGranted = uiState.usageAccess,
                icon = Icons.Default.GridView,
                onGrantClick = { PermissionUtils.openUsageAccessSettings(context) }
            )

            // 5. Battery Optimization Card
            PermissionCard(
                title = stringResource(R.string.perm_battery),
                description = stringResource(R.string.desc_battery),
                instruction = stringResource(R.string.instruction_battery),
                isGranted = uiState.batteryOptimization,
                icon = Icons.Default.BatteryAlert,
                onGrantClick = { PermissionUtils.openBatteryOptimizationSettings(context) }
            )

            // 6. Notifications Card
            PermissionCard(
                title = stringResource(R.string.perm_notifications),
                description = stringResource(R.string.desc_notifications),
                instruction = stringResource(R.string.instruction_notifications),
                isGranted = uiState.notificationsGranted,
                icon = Icons.Default.Notifications,
                onGrantClick = {
                    val activity = context as? Activity
                        ?: (context.applicationContext as? com.agon.app.GuardianApp)?.currentActivity
                    if (activity != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        PermissionUtils.requestNotificationsPermission(activity)
                    } else {
                        PermissionUtils.openNotificationSettings(context)
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    instruction: String,
    isGranted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onGrantClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = card),
        border = BorderStroke(1.dp, if (isGranted) success.copy(alpha = 0.5f) else cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = primary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        color = text,
                        fontSize = 16.sp
                    )
                    if (isGranted) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(success),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = surface,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.status_granted), color = success, fontSize = 13.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = description,
                            color = textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            if (!isGranted) {
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
                    onClick = onGrantClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primary,
                        contentColor = surface
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        stringResource(R.string.btn_grant_permission),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
