package com.agon.app.ui.screens

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.GuardianDeviceAdminReceiver
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.ContentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentScreen(vm: ContentViewModel) {
    val context = LocalContext.current
    val pornBlockerActive by vm.pornBlocker.collectAsStateWithLifecycle()
    val aiScannerActive by vm.aiScanner.collectAsStateWithLifecycle()
    val isDeviceOwner by vm.isDeviceOwner.collectAsStateWithLifecycle()
    val uninstallProtectionActive by vm.uninstallProtection.collectAsStateWithLifecycle()
    val strongProtectionActive by vm.strongProtection.collectAsStateWithLifecycle()
    val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val deviceAdminComponent = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
    val deviceAdminGranted = dpm.isAdminActive(deviceAdminComponent)
    val scrollState = rememberScrollState()

    var showStrongProtectionDialog by remember { mutableStateOf(false) }
    var strongPinInput by remember { mutableStateOf("") }
    var strongPinError by remember { mutableStateOf(false) }
    var showStrongWarningDialog by remember { mutableStateOf(false) }

    val adminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.setUninstallProtection(true)
        }
    }

    // MediaProjection consent: required for AI Explorer to actually capture
    // frames. Without this intent the service starts but the scan loop stays
    // dormant (the user has to re-grant to make the feature useful).
    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            vm.startAiScannerWithProjection(result.data!!)
        } else {
            // User cancelled — roll back the toggle.
            vm.setAiScanner(false)
        }
    }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_content_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background, titleContentColor = text)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                color = warning.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, warning.copy(alpha = 0.2f))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = warning, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.warning_content_filter), fontSize = 12.sp, color = textSecondary)
                }
            }

            FeatureToggleCard(
                title = stringResource(R.string.card_porn_blocker_title),
                subtitle = stringResource(R.string.card_porn_blocker_subtitle),
                isActive = pornBlockerActive,
                activeBadge = when {
                    !pornBlockerActive -> "OFF"
                    isDeviceOwner -> stringResource(R.string.badge_dns_active)
                    else -> stringResource(R.string.badge_keyword_active)
                },
                icon = Icons.Default.Security,
                color = success,
                onToggle = { active -> vm.setPornBlocker(active) }
            ) {
                Text(stringResource(R.string.dns_blocking_desc), fontSize = 12.sp, color = textSecondary)
                if (pornBlockerActive && !isDeviceOwner) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.porn_block_requires_device_owner),
                        fontSize = 11.sp,
                        color = warning,
                    )
                }
            }

            FeatureToggleCard(
                title = stringResource(R.string.card_ai_explorer_title),
                subtitle = stringResource(R.string.card_ai_explorer_subtitle),
                isActive = aiScannerActive,
                activeBadge = if (aiScannerActive) stringResource(R.string.badge_on) else stringResource(R.string.badge_off),
                icon = Icons.Default.Visibility,
                color = accent,
                onToggle = { active ->
                    if (active) {
                        // Enable the flag first, then ask for screen-capture
                        // consent. If the user denies, the onCancel handler
                        // flips it back to false.
                        vm.setAiScanner(true)
                        val mpManager = context.getSystemService(
                            android.content.Context.MEDIA_PROJECTION_SERVICE
                        ) as android.media.projection.MediaProjectionManager
                        projectionLauncher.launch(mpManager.createScreenCaptureIntent())
                    } else {
                        vm.setAiScanner(false)
                    }
                }
            ) {
                Text(stringResource(R.string.privacy_ai_note), fontSize = 12.sp, color = textSecondary)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.step_ai_capture), fontSize = 11.sp, color = textMuted)
                Text(stringResource(R.string.step_ai_analyze), fontSize = 11.sp, color = textMuted)
                Text(stringResource(R.string.step_ai_detect), fontSize = 11.sp, color = textMuted)
                Text(stringResource(R.string.step_ai_ban), fontSize = 11.sp, color = textMuted)
            }

            FeatureToggleCard(
                title = stringResource(R.string.card_uninstall_title),
                subtitle = stringResource(R.string.card_uninstall_subtitle),
                isActive = uninstallProtectionActive,
                activeBadge = if (deviceAdminGranted) stringResource(R.string.badge_protected) else stringResource(R.string.badge_not_protected),
                icon = Icons.Default.AdminPanelSettings,
                color = if (deviceAdminGranted) success else danger,
                onToggle = { active ->
                    if (active && !deviceAdminGranted) {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent)
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.device_admin_explanation))
                        }
                        adminLauncher.launch(intent)
                    } else {
                        vm.setUninstallProtection(active)
                    }
                }
            ) {
                if (!deviceAdminGranted) {
                    Surface(color = danger.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, danger.copy(alpha = 0.2f))) {
                        Text(stringResource(R.string.warning_admin_not_granted), fontSize = 11.sp, color = danger, modifier = Modifier.padding(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                ChecklistItem(stringResource(R.string.feature_app_settings_blocked))
                ChecklistItem(stringResource(R.string.feature_device_admin_blocked))
                ChecklistItem(stringResource(R.string.feature_dns_change_detected))
                ChecklistItem(stringResource(R.string.feature_safe_mode_warning))
                ChecklistItem(stringResource(R.string.feature_permission_removal_blocked))
            }

            FeatureToggleCard(
                title = stringResource(R.string.card_strong_protection_title),
                subtitle = stringResource(R.string.card_strong_protection_subtitle),
                isActive = strongProtectionActive,
                activeBadge = if (strongProtectionActive) stringResource(R.string.badge_strong) else stringResource(R.string.badge_off),
                icon = Icons.Default.Shield,
                color = if (strongProtectionActive) warning else textMuted,
                onToggle = { active ->
                    if (active) {
                        showStrongWarningDialog = true
                    } else {
                        strongPinInput = ""
                        strongPinError = false
                        showStrongProtectionDialog = true
                    }
                }
            ) {
                Surface(color = warning.copy(alpha = 0.08f), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, warning.copy(alpha = 0.2f))) {
                    Text(stringResource(R.string.warning_strong_protection), fontSize = 11.sp, color = warning, modifier = Modifier.padding(8.dp))
                }
                Spacer(Modifier.height(8.dp))
                if (vm.isKnoxDevice) {
                    Surface(color = accent.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                        Text(stringResource(R.string.badge_knox_detected), fontSize = 11.sp, color = accent, modifier = Modifier.padding(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                ChecklistItem(stringResource(R.string.feature_pin_required_disable))
                ChecklistItem(stringResource(R.string.feature_tamper_detection))
                ChecklistItem(stringResource(R.string.feature_remote_alert))
                ChecklistItem(stringResource(R.string.feature_os_suspension))
                ChecklistItem(stringResource(R.string.feature_safe_mode_block))
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable {
                        val intent = Intent(context, DeviceOwnerSetupActivity::class.java)
                        context.startActivity(intent)
                    }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AdminPanelSettings, null, tint = primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (vm.isDeviceOwner()) "Device Owner: Active (tap for setup)"
                            else "Set up Device Owner (ADB)",
                            fontSize = 12.sp,
                            color = primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    PinEntryDialog(
        show = showStrongWarningDialog,
        title = stringResource(R.string.strong_warning_title),
        message = stringResource(R.string.strong_warning_text),
        confirmLabel = stringResource(R.string.btn_confirm),
        input = strongPinInput,
        onInputChange = { strongPinInput = it; strongPinError = false },
        isError = strongPinError,
        isPassword = true,
        onDismiss = {
            showStrongWarningDialog = false
            strongPinInput = ""
            strongPinError = false
        },
        onConfirm = {
            val valid = vm.isDeviceAdminGranted() && GuardianDeviceAdminReceiver.verifyPinBeforeDisable(context, strongPinInput)
            if (valid) {
                vm.setStrongProtection(true)
                showStrongWarningDialog = false
                strongPinInput = ""
            } else {
                strongPinError = true
            }
        }
    )

    PinEntryDialog(
        show = showStrongProtectionDialog,
        title = stringResource(R.string.strong_disable_title),
        message = stringResource(R.string.strong_disable_text),
        confirmLabel = stringResource(R.string.btn_disable),
        input = strongPinInput,
        onInputChange = { strongPinInput = it; strongPinError = false },
        isError = strongPinError,
        isPassword = true,
        onDismiss = {
            showStrongProtectionDialog = false
            strongPinInput = ""
            strongPinError = false
        },
        onConfirm = {
            val valid = GuardianDeviceAdminReceiver.verifyPinBeforeDisable(context, strongPinInput)
            if (valid) {
                vm.setStrongProtection(false)
                showStrongProtectionDialog = false
                strongPinInput = ""
            } else {
                strongPinError = true
            }
        }
    )

}

@Composable
private fun FeatureToggleCard(
    title: String,
    subtitle: String,
    isActive: Boolean,
    activeBadge: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onToggle: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isActive) color.copy(alpha = 0.5f) else cardBorder)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = text)
                    Text(subtitle, fontSize = 12.sp, color = textSecondary)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = if (isActive) color.copy(alpha = 0.1f) else surfaceLight, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, if (isActive) color.copy(alpha = 0.3f) else cardBorder)) {
                    Text(activeBadge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isActive) color else textMuted, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                Spacer(Modifier.weight(1f))
                Switch(checked = isActive, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = color, checkedThumbColor = surface))
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ChecklistItem(text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = success, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = textSecondary)
    }
}

@Composable
private fun PinEntryDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    input: String,
    onInputChange: (String) -> Unit,
    isError: Boolean,
    isPassword: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(message, fontSize = 13.sp, color = textSecondary)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInputChange,
                        label = { Text(stringResource(R.string.pin_enter)) },
                        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (isPassword) KeyboardType.NumberPassword else KeyboardType.Ascii
                        ),
                        singleLine = true,
                        isError = isError,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text(confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}
