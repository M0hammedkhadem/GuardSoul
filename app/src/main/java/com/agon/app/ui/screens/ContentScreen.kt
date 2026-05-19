package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
fun ContentScreen(viewModel: GuardianViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.syncDeviceAdminStatus() }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_content_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    titleContentColor = text
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
            // Warning Banner
            Surface(
                color = danger.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, danger.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = danger)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.warning_content_filter),
                        color = danger,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Porn Blocker
            FeatureToggleCard(
                icon = Icons.Default.Shield,
                iconColor = success,
                title = stringResource(R.string.card_porn_blocker_title),
                subtitle = stringResource(R.string.card_porn_blocker_subtitle),
                badgeText = stringResource(R.string.badge_vpn_active),
                badgeColor = success,
                isActive = state.pornBlockerActive,
                onToggle = { viewModel.togglePornBlocker() }
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(success))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.feature_safe_search_active), color = success, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.feature_list_header), color = textSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    ChecklistItem(stringResource(R.string.feature_google_safe_search))
                    ChecklistItem(stringResource(R.string.feature_youtube_restricted))
                    ChecklistItem(stringResource(R.string.feature_bing_safe_search))
                    ChecklistItem(stringResource(R.string.feature_browser_filter))
                    ChecklistItem(stringResource(R.string.feature_dns_blocking))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Explorer
            FeatureToggleCard(
                icon = Icons.Default.CenterFocusStrong,
                iconColor = accent,
                title = stringResource(R.string.card_ai_explorer_title),
                subtitle = stringResource(R.string.card_ai_explorer_subtitle),
                badgeText = stringResource(R.string.badge_scanning),
                badgeColor = accent,
                isActive = state.aiExplorerActive,
                onToggle = { viewModel.toggleAiExplorer() }
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.feature_ai_active), color = accent, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.privacy_ai_note), color = textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = warning.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, warning.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.warning_auto_ban),
                            color = warning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Uninstall Protection
            FeatureToggleCard(
                icon = Icons.Default.Lock,
                iconColor = danger,
                title = stringResource(R.string.card_uninstall_title),
                subtitle = stringResource(R.string.card_uninstall_subtitle),
                badgeText = if (state.deviceAdminGranted) stringResource(R.string.badge_protected) else stringResource(R.string.badge_not_protected),
                badgeColor = if (state.deviceAdminGranted) danger else warning,
                isActive = state.uninstallProtectionActive,
                onToggle = {
                    viewModel.syncDeviceAdminStatus()
                    viewModel.toggleUninstallProtection()
                }
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    if (state.deviceAdminGranted) {
                        ChecklistItem(stringResource(R.string.feature_app_settings_blocked))
                        ChecklistItem(stringResource(R.string.feature_device_admin_blocked))
                        ChecklistItem(stringResource(R.string.feature_dns_change_detected))
                        ChecklistItem(stringResource(R.string.feature_safe_mode_warning))
                        ChecklistItem(stringResource(R.string.feature_permission_removal_blocked))
                    } else {
                        Surface(
                            color = warning.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, warning.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.warning_admin_not_granted),
                                color = warning,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // How AI Works Steps
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = card),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.card_how_ai_title), fontWeight = FontWeight.Bold, color = text, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    StepRow(1, primary, stringResource(R.string.step_ai_capture))
                    StepRow(2, accent, stringResource(R.string.step_ai_analyze))
                    StepRow(3, danger, stringResource(R.string.step_ai_detect))
                    StepRow(4, warning, stringResource(R.string.step_ai_notify))
                    StepRow(5, danger, stringResource(R.string.step_ai_ban))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun FeatureToggleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    isActive: Boolean,
    onToggle: () -> Unit,
    expandedContent: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isActive) iconColor.copy(alpha = 0.5f) else cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconColor.copy(alpha = 0.15f))
                            .border(1.dp, iconColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = title, fontWeight = FontWeight.Bold, color = text, fontSize = 16.sp)
                            if (isActive) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = badgeColor.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = badgeColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = subtitle, color = textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = isActive,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = surface,
                        checkedTrackColor = iconColor,
                        uncheckedThumbColor = textSecondary,
                        uncheckedTrackColor = surfaceLight
                    )
                )
            }

            if (expandedContent != null) {
                AnimatedVisibility(
                    visible = isActive,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    expandedContent()
                }
            }
        }
    }
}

@Composable
fun ChecklistItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = success, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = textMuted, fontSize = 13.sp)
    }
}

@Composable
fun StepRow(number: Int, color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f))
                .border(1.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number.toString(), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, color = textSecondary, fontSize = 14.sp)
    }
}
