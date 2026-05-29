package com.agon.app.ui.screens

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.VpnService
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
    val nextDnsProfileId by vm.nextDnsProfileId.collectAsStateWithLifecycle()
    val aiExplorerActive by vm.aiScanner.collectAsStateWithLifecycle()
    val uninstallProtectionActive by vm.uninstallProtection.collectAsStateWithLifecycle()
    val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val deviceAdminComponent = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
    val deviceAdminGranted = dpm.isAdminActive(deviceAdminComponent)
    val scrollState = rememberScrollState()
    var editProfileId by remember { mutableStateOf(false) }
    var profileIdText by remember(nextDnsProfileId) { mutableStateOf(nextDnsProfileId) }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.setPornBlocker(true)
        }
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            vm.startAiScannerWithProjection(result.data!!)
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
                activeBadge = stringResource(R.string.badge_vpn_active),
                icon = Icons.Default.Security,
                color = success,
                onToggle = { active ->
                    if (active) {
                        val intent = VpnService.prepare(context)
                        if (intent != null) {
                            vpnLauncher.launch(intent)
                        } else {
                            vm.setPornBlocker(true)
                        }
                    } else {
                        vm.setPornBlocker(false)
                    }
                }
            ) {
                ChecklistItem(stringResource(R.string.feature_google_safe_search))
                ChecklistItem(stringResource(R.string.feature_youtube_restricted))
                ChecklistItem(stringResource(R.string.feature_bing_safe_search))
                ChecklistItem(stringResource(R.string.feature_browser_filter))
                ChecklistItem(stringResource(R.string.feature_dns_blocking))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.label_dns_provider), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = text)
                Spacer(Modifier.height(4.dp))
                Surface(color = surfaceLight, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(primary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Text("N", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primary)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("NextDNS", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = text)
                            Text("45.90.28.0 / 45.90.29.0", fontSize = 11.sp, color = textMuted)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.label_nextdns_profile), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = text)
                        if (nextDnsProfileId.isNotBlank()) {
                            Text(nextDnsProfileId, fontSize = 12.sp, color = textMuted)
                        } else {
                            Text(stringResource(R.string.hint_nextdns_profile_empty), fontSize = 11.sp, color = textMuted)
                        }
                    }
                    TextButton(onClick = { editProfileId = true }) {
                        Text(if (nextDnsProfileId.isNotBlank()) stringResource(R.string.btn_change) else stringResource(R.string.btn_set))
                    }
                }
            }

            FeatureToggleCard(
                title = stringResource(R.string.card_ai_explorer_title),
                subtitle = stringResource(R.string.card_ai_explorer_subtitle),
                isActive = aiExplorerActive,
                activeBadge = stringResource(R.string.badge_scanning),
                icon = Icons.Default.Visibility,
                color = accent,
                onToggle = { active ->
                    if (active) {
                        val projectionIntent = vm.getMediaProjectionIntent()
                        if (projectionIntent != null) {
                            mediaProjectionLauncher.launch(projectionIntent)
                        }
                    } else {
                        vm.setAiScanner(false)
                    }
                }
            ) {
                Text(stringResource(R.string.privacy_ai_note), fontSize = 12.sp, color = textMuted)
                Spacer(Modifier.height(8.dp))
                Surface(color = surfaceLight, shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.card_how_ai_title), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = text)
                        Spacer(Modifier.height(8.dp))
                        StepRow(1, stringResource(R.string.step_ai_capture))
                        StepRow(2, stringResource(R.string.step_ai_analyze))
                        StepRow(3, stringResource(R.string.step_ai_detect))
                        StepRow(4, stringResource(R.string.step_ai_notify))
                        StepRow(5, stringResource(R.string.step_ai_ban))
                    }
                }
                Spacer(Modifier.height(12.dp))
                val aiThreshold by vm.aiThreshold.collectAsStateWithLifecycle()
                var thresholdSlider by remember(aiThreshold) { mutableStateOf(aiThreshold) }
                Text(
                    "Detection Threshold: ${(thresholdSlider * 100).toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = text
                )
                Slider(
                    value = thresholdSlider,
                    onValueChange = { thresholdSlider = it },
                    onValueChangeFinished = { vm.setAiThreshold(thresholdSlider) },
                    valueRange = 0.5f..0.9f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Surface(color = danger.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, danger.copy(alpha = 0.2f))) {
                    Text(stringResource(R.string.warning_auto_ban), fontSize = 11.sp, color = danger, modifier = Modifier.padding(8.dp))
                }
            }

            FeatureToggleCard(
                title = stringResource(R.string.card_uninstall_title),
                subtitle = stringResource(R.string.card_uninstall_subtitle),
                isActive = uninstallProtectionActive,
                activeBadge = if (deviceAdminGranted) stringResource(R.string.badge_protected) else stringResource(R.string.badge_not_protected),
                icon = Icons.Default.AdminPanelSettings,
                color = if (deviceAdminGranted) success else danger,
                onToggle = { vm.setUninstallProtection(it) }
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
            Spacer(Modifier.height(16.dp))
        }
    }

    if (editProfileId) {
        AlertDialog(
            onDismissRequest = { editProfileId = false },
            title = { Text(stringResource(R.string.title_nextdns_profile)) },
            text = {
                Column {
                    Text(stringResource(R.string.desc_nextdns_profile), fontSize = 13.sp, color = textSecondary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = profileIdText,
                        onValueChange = { profileIdText = it },
                        label = { Text(stringResource(R.string.hint_nextdns_profile)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setNextDnsProfileId(profileIdText.trim())
                    editProfileId = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    profileIdText = nextDnsProfileId
                    editProfileId = false
                }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
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
private fun StepRow(number: Int, text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Text("$number", fontSize = 10.sp, color = accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = textSecondary)
    }
}
