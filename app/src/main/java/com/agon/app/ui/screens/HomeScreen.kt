package com.agon.app.ui.screens

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.LanguageManager
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onNavigateToPermissions: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val shieldActive by vm.shieldActive.collectAsStateWithLifecycle()
    val totalBlocks by vm.totalBlocks.collectAsStateWithLifecycle()
    val daysActive by vm.daysActive.collectAsStateWithLifecycle()
    val countdownActive by vm.countdownActive.collectAsStateWithLifecycle()
    val remainingSeconds by vm.remainingSeconds.collectAsStateWithLifecycle()
    val deactivationDelay by vm.deactivationDelay.collectAsStateWithLifecycle()
    val showPinDialog by vm.showPinDialog.collectAsStateWithLifecycle()
    val pinError by vm.pinError.collectAsStateWithLifecycle()

    var pinInput by remember { mutableStateOf("") }
    var showDelayDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                HomeHeader(
                    isShieldActive = shieldActive,
                    onLanguageToggle = {
                        val newLang = if (LanguageManager.currentLanguageCode == "en") "ar" else "en"
                        LanguageManager.setLanguage(context, newLang)
                        (context as Activity).recreate()
                    }
                )
            }

            item {
                ShieldOrb(
                    isActive = shieldActive,
                    onClick = {
                        if (!shieldActive) vm.toggleShield()
                        else vm.startDeactivation()
                    }
                )
            }

            if (countdownActive) {
                item {
                    CountdownBanner(
                        remainingSeconds = remainingSeconds,
                        onCancel = { vm.cancelDeactivation() }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        value = "$daysActive",
                        label = stringResource(R.string.stat_days),
                        subLabel = stringResource(R.string.stat_streak),
                        icon = Icons.Default.CalendarToday,
                        color = Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        value = "$totalBlocks",
                        label = stringResource(R.string.stat_blocks),
                        subLabel = stringResource(R.string.stat_total),
                        icon = Icons.Default.Shield,
                        color = Color(0xFF7C3AED),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                ActionTile(
                    title = stringResource(R.string.card_delay_title),
                    subtitle = stringResource(R.string.card_delay_subtitle),
                    icon = Icons.Default.HourglassEmpty,
                    iconColor = Color(0xFF7C3AED),
                    trailingContent = {
                        Text(
                            deactivationDelayLabel(deactivationDelay),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7C3AED),
                            fontSize = 13.sp
                        )
                    },
                    onClick = { showDelayDialog = true }
                )
            }

            item {
                Spacer(Modifier.height(12.dp))
                ActionTile(
                    title = stringResource(R.string.screen_permissions_title),
                    subtitle = stringResource(R.string.perm_accessibility),
                    icon = Icons.Default.Accessibility,
                    iconColor = Color(0xFF3B82F6),
                    trailingContent = {},
                    onClick = onNavigateToPermissions
                )
            }

            item {
                Spacer(Modifier.height(12.dp))
                ActionTile(
                    title = stringResource(R.string.screen_settings_title),
                    subtitle = stringResource(R.string.shortstop_settings_subtitle),
                    icon = Icons.Default.Settings,
                    iconColor = Color(0xFF6B7280),
                    trailingContent = {},
                    onClick = onNavigateToSettings
                )
            }
        }
    }

    if (showPinDialog) {
        PinVerifyDialog(
            pinInput = pinInput,
            onPinInputChange = {
                if (it.length <= 6) {
                    pinInput = it
                    if (pinError) vm.dismissPinError()
                }
            },
            isError = pinError,
            onConfirm = { vm.verifyPin(pinInput); pinInput = "" },
            onDismiss = { vm.dismissPinDialog(); pinInput = "" }
        )
    }

    if (showDelayDialog) {
        AlertDialog(
            onDismissRequest = { showDelayDialog = false },
            title = { Text(stringResource(R.string.card_delay_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    val options = listOf(0, 2, 7, 15, 30)
                    options.forEach { days ->
                        val selected = deactivationDelay == days
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setDeactivationDelay(days)
                                    showDelayDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                                .then(
                                    if (selected) Modifier.background(
                                        Color(0xFF7C3AED).copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp)
                                    ) else Modifier
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    vm.setDeactivationDelay(days)
                                    showDelayDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF7C3AED))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                deactivationDelayLabel(days),
                                color = if (selected) Color(0xFF7C3AED) else text,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDelayDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
            containerColor = surface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun HomeHeader(
    isShieldActive: Boolean,
    onLanguageToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.screen_home_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = text
            )
            Text(
                text = stringResource(R.string.screen_home_subtitle),
                fontSize = 13.sp,
                color = textSecondary
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLanguageToggle) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = "Change Language",
                    tint = text
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = surfaceLight.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isShieldActive) Color(0xFF00D4AA) else Color(0xFFFF4757))
                    )
                    Text(
                        stringResource(if (isShieldActive) R.string.status_active else R.string.status_inactive),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isShieldActive) Color(0xFF00D4AA) else Color(0xFFFF4757)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, subLabel: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier
    ) {
        Column(Modifier.padding(20.dp)) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(value, fontSize = 32.sp, fontWeight = FontWeight.Black, color = text)
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = text)
            Text(subLabel, fontSize = 11.sp, color = textSecondary)
        }
    }
}

@Composable
private fun ActionTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    trailingContent: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = text, fontSize = 16.sp)
                Text(subtitle, color = textSecondary, fontSize = 12.sp)
            }
            trailingContent()
        }
    }
}

@Composable
fun ShieldOrb(isActive: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )

    val orbColor = if (isActive) shieldGreen else shieldRed
    val statusText = stringResource(if (isActive) R.string.status_active else R.string.status_inactive).uppercase()
    val activeDesc = stringResource(R.string.shield_active_desc)
    val inactiveDesc = stringResource(R.string.shield_inactive_desc)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (isActive) activeDesc else inactiveDesc
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
            Box(Modifier.size(255.dp).border(0.5.dp, orbColor.copy(alpha = 0.12f), CircleShape))
            Box(Modifier.size(220.dp).border(0.8.dp, orbColor.copy(alpha = 0.22f), CircleShape))
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(if (isActive) pulseScale else 1f)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(orbColor.copy(alpha = if (isActive) 0.15f else 0.05f), Color.Transparent)
                        )
                    )
                    .border(2.dp, orbColor.copy(alpha = if (isActive) glowAlpha else 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = orbColor,
                        modifier = Modifier.size(76.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = statusText,
                        color = orbColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
        Text(
            text = stringResource(if (isActive) R.string.tap_to_deactivate_shield else R.string.tap_to_activate_shield),
            fontSize = 14.sp,
            color = textSecondary.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 8.dp).clearAndSetSemantics { }
        )
    }
}

@Composable
fun PinVerifyDialog(
    pinInput: String,
    onPinInputChange: (String) -> Unit,
    isError: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_strict_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(stringResource(R.string.pin_strict_desc), color = textSecondary)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = onPinInputChange,
                    label = { Text(stringResource(R.string.pin_enter)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    ),
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text(stringResource(R.string.pin_strict_error), color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = pinInput.length >= 4,
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.pin_btn_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
        containerColor = surface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun CountdownBanner(remainingSeconds: Int, onCancel: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.shield_deactivation_title),
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEF4444),
                fontSize = 18.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.shield_deactivation_desc),
                color = textSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = formatCountdown(remainingSeconds),
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFEF4444)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.btn_cancel_deactivation))
            }
        }
    }
}

@Composable
private fun formatCountdown(totalSeconds: Int): String {
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (days > 0) {
        stringResource(R.string.countdown_days_hours_minutes_seconds, days, hours, minutes, seconds)
    } else if (hours > 0) {
        stringResource(R.string.countdown_hours_minutes_seconds, hours, minutes, seconds)
    } else {
        stringResource(R.string.countdown_minutes_seconds, minutes, seconds)
    }
}

@Composable
private fun deactivationDelayLabel(days: Int): String = when (days) {
    0 -> stringResource(R.string.delay_no_delay)
    2 -> stringResource(R.string.delay_2_days)
    7 -> stringResource(R.string.delay_7_days)
    15 -> stringResource(R.string.delay_15_days)
    30 -> stringResource(R.string.delay_1_month)
    else -> "$days " + stringResource(R.string.stat_days)
}
