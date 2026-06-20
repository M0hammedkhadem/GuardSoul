package com.agon.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.ContentViewModel

@Composable
fun ContentScreen(vm: ContentViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshUninstallProtectionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pornBlockerEnabled by vm.pornBlockerEnabled.collectAsStateWithLifecycle()
    val aiExplorerEnabled by vm.aiExplorerEnabled.collectAsStateWithLifecycle()
    val uninstallProtectionEnabled by vm.uninstallProtectionEnabled.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.screen_content_title),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = text,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                )
            }

            item {
                InfoCard(
                    text = stringResource(R.string.warning_content_filter),
                    icon = Icons.Default.VisibilityOff,
                    color = Color(0xFFEF4444)
                )
            }

            item {
                ContentToggleCard(
                    title = stringResource(R.string.card_porn_blocker_title),
                    subtitle = stringResource(R.string.card_porn_blocker_subtitle),
                    enabled = pornBlockerEnabled,
                    onToggle = vm::togglePornBlocker,
                    icon = Icons.Default.Security,
                    iconColor = Color(0xFF10B981)
                )
            }

            item {
                ContentToggleCard(
                    title = stringResource(R.string.card_ai_explorer_title),
                    subtitle = stringResource(R.string.card_ai_explorer_subtitle),
                    enabled = aiExplorerEnabled,
                    onToggle = vm::toggleAiExplorer,
                    icon = Icons.Default.Scanner,
                    iconColor = Color(0xFF8B5CF6)
                )
            }

            item {
                ContentToggleCard(
                    title = stringResource(R.string.card_uninstall_title),
                    subtitle = stringResource(R.string.card_uninstall_subtitle),
                    enabled = uninstallProtectionEnabled,
                    onToggle = vm::toggleUninstallProtection,
                    icon = Icons.Default.Lock,
                    iconColor = Color(0xFFEF4444)
                )
            }
        }
    }
}

@Composable
private fun InfoCard(text: String, icon: ImageVector, color: Color) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = color, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ContentToggleCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    icon: ImageVector,
    iconColor: Color
) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = primary,
                    uncheckedThumbColor = textMuted,
                    uncheckedTrackColor = surfaceLight
                )
            )
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = text, fontSize = 18.sp)
                Text(subtitle, color = textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
        }
    }
}
