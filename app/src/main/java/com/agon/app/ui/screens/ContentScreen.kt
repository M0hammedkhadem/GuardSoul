package com.agon.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.ContentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentScreen(vm: ContentViewModel) {
    val context = LocalContext.current
    val pornBlockerEnabled by vm.pornBlockerEnabled.collectAsStateWithLifecycle()
    val aiExplorerEnabled by vm.aiExplorerEnabled.collectAsStateWithLifecycle()
    val uninstallProtectionEnabled by vm.uninstallProtectionEnabled.collectAsStateWithLifecycle()
    val vpnActive by vm.vpnActive.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.startVpn()
        }
    }

    LaunchedEffect(pornBlockerEnabled) {
        vm.refreshVpnState()
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            WarningBanner()
            Spacer(Modifier.height(16.dp))

            ContentFilterCard(
                title = stringResource(R.string.card_porn_blocker_title),
                description = stringResource(R.string.card_content_porn_blocker_desc),
                icon = Icons.Default.Shield,
                iconTint = success,
                isEnabled = pornBlockerEnabled,
                isServiceActive = vpnActive,
                onToggle = { vm.togglePornBlocker(vpnPermissionLauncher) }
            )
            Spacer(Modifier.height(12.dp))

            ContentFilterCard(
                title = stringResource(R.string.card_ai_explorer_title),
                description = stringResource(R.string.card_content_ai_explorer_desc),
                icon = Icons.Default.Security,
                iconTint = accent,
                isEnabled = aiExplorerEnabled,
                isServiceActive = null,
                onToggle = { vm.toggleAiExplorer() }
            )
            Spacer(Modifier.height(12.dp))

            ContentFilterCard(
                title = stringResource(R.string.card_content_uninstall_protection_title),
                description = stringResource(R.string.card_content_uninstall_protection_desc),
                icon = Icons.Default.Block,
                iconTint = danger,
                isEnabled = uninstallProtectionEnabled,
                isServiceActive = null,
                onToggle = { vm.toggleUninstallProtection() }
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun WarningBanner() {
    Card(
        colors = CardDefaults.cardColors(containerColor = danger.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, danger.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.content_filter_warning),
                color = danger,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(danger.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = null,
                    tint = danger,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ContentFilterCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    isEnabled: Boolean,
    isServiceActive: Boolean? = null,
    onToggle: () -> Unit
) {
    val borderColor = when {
        isEnabled && isServiceActive == true -> success.copy(alpha = 0.5f)
        isEnabled && isServiceActive == false -> warning.copy(alpha = 0.5f)
        else -> cardBorder
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = text,
                    checkedTrackColor = primary,
                    uncheckedThumbColor = textMuted,
                    uncheckedTrackColor = surfaceLight
                ),
                modifier = Modifier.size(48.dp, 26.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        color = text,
                        fontSize = 15.sp
                    )
                    if (isEnabled && isServiceActive != null) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isServiceActive) success else warning)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    color = textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, title, tint = iconTint, modifier = Modifier.size(22.dp))
            }
        }
    }
}
