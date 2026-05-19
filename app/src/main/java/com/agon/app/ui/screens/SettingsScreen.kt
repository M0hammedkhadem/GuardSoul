package com.agon.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.GuardianViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToSocial: () -> Unit,
    onNavigateToContent: () -> Unit,
    onNavigateToLists: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToPinSetup: () -> Unit = {},
    onNavigateToSchedule: () -> Unit = {},
    onNavigateToTimeLimits: () -> Unit = {},
    onNavigateToStatistics: () -> Unit = {},
    onNavigateToExportImport: () -> Unit = {},
    onBack: () -> Unit,
    viewModel: GuardianViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val needsPinUnlock = state.pinCode != null && !state.appUnlocked
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.syncDeviceAdminStatus() }

    if (needsPinUnlock) {
        PinGateScreen(
            onUnlock = { viewModel.unlockApp() },
            onLock = onBack,
            storedPinHash = state.pinCode
        )
        return
    }

    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_settings_title), fontWeight = FontWeight.Bold) },
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
            Text(stringResource(R.string.section_quick_access), fontWeight = FontWeight.Bold, color = textSecondary, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = card),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column {
                    SettingsRow(icon = Icons.Default.People, title = stringResource(R.string.row_social_media), onClick = onNavigateToSocial)
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.Security, title = stringResource(R.string.row_content_blocker), onClick = onNavigateToContent)
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.List, title = stringResource(R.string.row_blacklist), onClick = onNavigateToLists)
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.VpnKey, title = stringResource(R.string.row_permissions_settings), onClick = onNavigateToPermissions)
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.Person, title = "Profile", onClick = onNavigateToProfile)
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.Lock, title = "PIN Protection", onClick = onNavigateToPinSetup)
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.Schedule, title = "Schedule Blocking", onClick = onNavigateToSchedule)
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.Timer, title = "Daily Time Limits", onClick = onNavigateToTimeLimits)
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.BarChart, title = "Usage Statistics", onClick = onNavigateToStatistics)
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.FileUpload, title = "Export / Import", onClick = onNavigateToExportImport)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(stringResource(R.string.section_status_overview), fontWeight = FontWeight.Bold, color = textSecondary, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusMiniCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Shield,
                    title = stringResource(R.string.mini_shield),
                    isActive = state.isShieldActive,
                    activeColor = success
                )
                StatusMiniCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.VpnLock,
                    title = stringResource(R.string.mini_vpn_filter),
                    isActive = state.pornBlockerActive,
                    activeColor = success
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusMiniCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CenterFocusStrong,
                    title = stringResource(R.string.mini_ai_scan),
                    isActive = state.aiExplorerActive,
                    activeColor = accent
                )
                StatusMiniCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.mini_uninstall),
                    isActive = state.uninstallProtectionActive,
                    activeColor = danger
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(stringResource(R.string.section_blocked_apps), fontWeight = FontWeight.Bold, color = textSecondary, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = card),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column {
                    BlockedAppRow(stringResource(R.string.app_instagram), state.instagramBlocked, "full")
                    HorizontalDivider(color = cardBorder)
                    BlockedAppRow(stringResource(R.string.app_snapchat), state.snapchatBlocked, "full")
                    HorizontalDivider(color = cardBorder)
                    BlockedAppRow(stringResource(R.string.app_twitter), state.twitterBlocked, "full")
                    HorizontalDivider(color = cardBorder)
                    BlockedAppRow(stringResource(R.string.app_tiktok), state.tiktokBlocked, "full")
                    HorizontalDivider(color = cardBorder)
                    BlockedAppRow(stringResource(R.string.app_youtube), state.youtubeMode != "off", state.youtubeMode)
                    HorizontalDivider(color = cardBorder)
                    BlockedAppRow(stringResource(R.string.app_facebook), state.facebookMode != "off", state.facebookMode)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(stringResource(R.string.section_data_management), fontWeight = FontWeight.Bold, color = textSecondary, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))

            var showResetStats by remember { mutableStateOf(false) }
            var showResetSettings by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = card),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column {
                    SettingsRow(icon = Icons.Default.DeleteOutline, title = stringResource(R.string.btn_reset_statistics)) { showResetStats = true }
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.Restore, title = stringResource(R.string.btn_reset_all_settings)) { showResetSettings = true }
                }
            }

            if (showResetStats) {
                AlertDialog(
                    onDismissRequest = { showResetStats = false },
                    containerColor = surface,
                    title = { Text(stringResource(R.string.dialog_reset_stats_title), color = text) },
                    text = { Text(stringResource(R.string.dialog_reset_stats_message), color = textSecondary) },
                    confirmButton = {
                        Button(onClick = { viewModel.resetStatistics(); showResetStats = false }, colors = ButtonDefaults.buttonColors(containerColor = danger)) {
                            Text(stringResource(R.string.dialog_reset_stats_confirm), color = surface)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetStats = false }) { Text(stringResource(R.string.btn_cancel), color = textSecondary) }
                    }
                )
            }

            if (showResetSettings) {
                AlertDialog(
                    onDismissRequest = { showResetSettings = false },
                    containerColor = surface,
                    title = { Text(stringResource(R.string.dialog_reset_all_title), color = text) },
                    text = { Text(stringResource(R.string.dialog_reset_all_message), color = textSecondary) },
                    confirmButton = {
                        Button(onClick = { viewModel.resetAllSettings(); showResetSettings = false }, colors = ButtonDefaults.buttonColors(containerColor = danger)) {
                            Text(stringResource(R.string.dialog_reset_all_confirm), color = surface)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetSettings = false }) { Text(stringResource(R.string.btn_cancel), color = textSecondary) }
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // About Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = card),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = primary, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.about_title), color = text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.about_version), color = textMuted, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.about_description),
                        color = textSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun BlockedAppRow(name: String, isBlocked: Boolean, mode: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Apps, contentDescription = null, tint = textMuted, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(name, color = text, fontSize = 15.sp)
        }
        
        val badgeColor = if (!isBlocked) success else if (mode == "full") danger else warning
        val badgeText = if (!isBlocked) stringResource(R.string.badge_open_upper) else if (mode == "full") stringResource(R.string.badge_blocked) else mode.uppercase()

        Surface(
            color = badgeColor.copy(alpha = 0.1f),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
        ) {
            Text(
                text = badgeText,
                color = badgeColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, color = text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textMuted)
    }
}

@Composable
fun StatusMiniCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, isActive: Boolean, activeColor: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = card),
        border = BorderStroke(1.dp, if (isActive) activeColor.copy(alpha = 0.5f) else cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isActive) activeColor.copy(alpha = 0.15f) else surfaceLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = if (isActive) activeColor else textMuted)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, color = text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = if (isActive) stringResource(R.string.status_active) else stringResource(R.string.status_inactive), color = if (isActive) activeColor else textMuted, fontSize = 12.sp)
        }
    }
}
