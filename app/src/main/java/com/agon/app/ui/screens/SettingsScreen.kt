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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.GuardianViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToSocial: () -> Unit,
    onNavigateToContent: () -> Unit,
    onNavigateToLists: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onBack: () -> Unit,
    viewModel: GuardianViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
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
            Text("Quick Access", fontWeight = FontWeight.Bold, color = textSecondary, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = card),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column {
                    SettingsRow(icon = Icons.Default.People, title = "Social Media Settings", onClick = onNavigateToSocial)
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.Security, title = "Content Blocker Settings", onClick = onNavigateToContent)
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.List, title = "Blacklist & Whitelist", onClick = onNavigateToLists)
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.VpnKey, title = "Permissions", onClick = onNavigateToPermissions)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("Status Overview", fontWeight = FontWeight.Bold, color = textSecondary, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusMiniCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Shield,
                    title = "Shield",
                    isActive = state.isShieldActive,
                    activeColor = success
                )
                StatusMiniCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.VpnLock,
                    title = "VPN Filter",
                    isActive = state.pornBlockerActive,
                    activeColor = success
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusMiniCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CenterFocusStrong,
                    title = "AI Scan",
                    isActive = state.aiExplorerActive,
                    activeColor = accent
                )
                StatusMiniCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Lock,
                    title = "Uninstall Prot.",
                    isActive = state.uninstallProtectionActive,
                    activeColor = danger
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("Blocked Apps Summary", fontWeight = FontWeight.Bold, color = textSecondary, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = card),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column {
                    BlockedAppRow("Instagram", state.instagramBlocked, "full")
                    HorizontalDivider(color = cardBorder)
                    BlockedAppRow("Snapchat", state.snapchatBlocked, "full")
                    HorizontalDivider(color = cardBorder)
                    BlockedAppRow("X (Twitter)", state.twitterBlocked, "full")
                    HorizontalDivider(color = cardBorder)
                    BlockedAppRow("TikTok", state.tiktokBlocked, "full")
                    HorizontalDivider(color = cardBorder)
                    BlockedAppRow("YouTube", state.youtubeMode != "off", state.youtubeMode)
                    HorizontalDivider(color = cardBorder)
                    BlockedAppRow("Facebook", state.facebookMode != "off", state.facebookMode)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("Data Management", fontWeight = FontWeight.Bold, color = textSecondary, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))

            var showResetStats by remember { mutableStateOf(false) }
            var showResetSettings by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = card),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column {
                    SettingsRow(icon = Icons.Default.DeleteOutline, title = "Reset Statistics") { showResetStats = true }
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.Restore, title = "Reset All Settings") { showResetSettings = true }
                }
            }

            if (showResetStats) {
                AlertDialog(
                    onDismissRequest = { showResetStats = false },
                    containerColor = surface,
                    title = { Text("Reset Statistics", color = text) },
                    text = { Text("This will reset your blocks count and streak to zero. This cannot be undone.", color = textSecondary) },
                    confirmButton = {
                        Button(onClick = { viewModel.resetStatistics(); showResetStats = false }, colors = ButtonDefaults.buttonColors(containerColor = danger)) {
                            Text("Reset", color = surface)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetStats = false }) { Text("Cancel", color = textSecondary) }
                    }
                )
            }

            if (showResetSettings) {
                AlertDialog(
                    onDismissRequest = { showResetSettings = false },
                    containerColor = surface,
                    title = { Text("Reset All Settings", color = text) },
                    text = { Text("This will disable the shield, clear all custom lists, and revert all settings to default. This cannot be undone.", color = textSecondary) },
                    confirmButton = {
                        Button(onClick = { viewModel.resetAllSettings(); showResetSettings = false }, colors = ButtonDefaults.buttonColors(containerColor = danger)) {
                            Text("Reset All", color = surface)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetSettings = false }) { Text("Cancel", color = textSecondary) }
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
                    Text("Guardian", color = text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Version 1.0.0", color = textMuted, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Guardian is a powerful digital wellness shield designed to help you regain focus and break free from addictive digital habits through robust content blocking and intelligent AI scanning.",
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
        val badgeText = if (!isBlocked) "OPEN" else if (mode == "full") "BLOCKED" else mode.uppercase()

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
            Text(text = if (isActive) "Active" else "Inactive", color = if (isActive) activeColor else textMuted, fontSize = 12.sp)
        }
    }
}
