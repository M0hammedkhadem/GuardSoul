package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.GuardianApp
import com.agon.app.LanguageManager
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onNavigateToPermissions: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val shieldActive by vm.shieldActive.collectAsStateWithLifecycle()
    val trialMode by vm.trialMode.collectAsStateWithLifecycle()
    val deactivationDelay by vm.deactivationDelay.collectAsStateWithLifecycle()
    val strictMode by vm.strictMode.collectAsStateWithLifecycle()
    val totalBlocks by vm.totalBlocks.collectAsStateWithLifecycle()
    val blocksToday by vm.blocksToday.collectAsStateWithLifecycle()
    val streakCount by vm.streakCount.collectAsStateWithLifecycle()
    val profileName by vm.profileName.collectAsStateWithLifecycle()
    val hasPin by vm.hasPin.collectAsStateWithLifecycle()
    val countdownActive by vm.countdownActive.collectAsStateWithLifecycle()
    val remainingSeconds by vm.remainingSeconds.collectAsStateWithLifecycle()
    val showPinDialog by vm.showPinDialog.collectAsStateWithLifecycle()
    val pinError by vm.pinError.collectAsStateWithLifecycle()
    val xpPoints by vm.xpPoints.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()

    val app = context.applicationContext as GuardianApp
    val appSettings = app.repository.getAppSettings()
    val permAccessibility by appSettings.permAccessibilityFlow.collectAsState(initial = false)
    val permVpn by appSettings.permVpnFlow.collectAsState(initial = false)
    val permAdmin by appSettings.permAdminFlow.collectAsState(initial = false)
    val permOverlay by appSettings.permOverlayFlow.collectAsState(initial = false)
    val permUsage by appSettings.permUsageFlow.collectAsState(initial = false)
    val permNotifications by appSettings.permNotificationsFlow.collectAsState(initial = false)
    val permissionsGranted = permAccessibility && permVpn && permAdmin && permOverlay && permUsage && permNotifications

    var pinInput by remember { mutableStateOf("") }
    var showDelayDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = background,
            topBar = { HomeHeader(isShieldActive = shieldActive) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                ShieldOrb(
                    isActive = shieldActive,
                    isCountingDown = countdownActive,
                    onClick = {
                        if (!shieldActive) {
                            vm.toggleShield()
                        } else {
                            vm.startDeactivation()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))

                StatsRow(
                    activatedAt = if (streakCount > 0) System.currentTimeMillis() - (streakCount * 86400000L) else null,
                    blocksCount = totalBlocks
                )

                Spacer(modifier = Modifier.height(12.dp))

                GamificationCard(
                    level = level,
                    xpPoints = xpPoints,
                    xpProgress = vm.xpProgress,
                    xpForNextLevel = vm.xpForNextLevel
                )

                Spacer(modifier = Modifier.height(16.dp))

                ActionButtonsRow(
                    permissionsGranted = permissionsGranted,
                    onNavigateToPermissions = onNavigateToPermissions,
                    onNavigateToSettings = onNavigateToSettings
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (!shieldActive) {
                    PermissionWarningBar(onClick = onNavigateToPermissions)
                }

                Spacer(modifier = Modifier.height(16.dp))

                TrialModeCard(
                    isTrialMode = trialMode,
                    onToggle = { vm.setTrialMode(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                DeactivationDelayCard(
                    currentDelayMinutes = deactivationDelay,
                    onClick = { showDelayDialog = true }
                )

                Spacer(modifier = Modifier.height(8.dp))

                StrictModeCard(
                    isStrictMode = strictMode,
                    onToggle = { vm.setStrictMode(it) },
                    hasPin = hasPin
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (countdownActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = card),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            null,
                            tint = warning,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.shield_deactivation_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = text
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.shield_deactivation_desc),
                            fontSize = 14.sp,
                            color = textSecondary
                        )
                        Spacer(Modifier.height(16.dp))
                        val minutes = remainingSeconds / 60
                        val seconds = remainingSeconds % 60
                        Text(
                            stringResource(R.string.countdown_minutes_seconds, minutes, seconds),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            color = warning
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { remainingSeconds.toFloat() / (deactivationDelay * 60).coerceAtLeast(1) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = warning,
                            trackColor = cardBorder
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { vm.cancelDeactivation() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = surfaceLight)
                        ) {
                            Text(stringResource(R.string.btn_cancel_deactivation), color = text)
                        }
                    }
                }
            }
        }

        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = { vm.dismissPinDialog() },
                title = {
                    Text(stringResource(R.string.pin_strict_title), fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(stringResource(R.string.pin_strict_desc), color = textSecondary)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 6) pinInput = it },
                            label = { Text(stringResource(R.string.pin_enter)) },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                            ),
                            singleLine = true,
                            isError = pinError,
                            supportingText = if (pinError) {
                                { Text(stringResource(R.string.pin_strict_error), color = MaterialTheme.colorScheme.error) }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            vm.verifyPin(pinInput)
                            pinInput = ""
                        },
                        enabled = pinInput.length >= 4,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.pin_btn_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { vm.dismissPinDialog(); pinInput = "" }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                },
                containerColor = card,
                shape = RoundedCornerShape(24.dp)
            )
        }

        if (showDelayDialog) {
            val delayOptions = listOf(
                0 to stringResource(R.string.delay_none),
                2880 to stringResource(R.string.delay_2_days),
                10080 to stringResource(R.string.delay_7_days),
                21600 to stringResource(R.string.delay_15_days),
                43200 to stringResource(R.string.delay_1_month)
            )
            AlertDialog(
                onDismissRequest = { showDelayDialog = false },
                title = { Text(stringResource(R.string.card_delay_title), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        delayOptions.forEach { (minutes, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.setDeactivationDelay(minutes)
                                        showDelayDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                                    .then(if (minutes == deactivationDelay) Modifier.background(primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)) else Modifier),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = minutes == deactivationDelay,
                                    onClick = {
                                        vm.setDeactivationDelay(minutes)
                                        showDelayDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = primary)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(label, fontWeight = FontWeight.Medium, color = text)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showDelayDialog = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                },
                containerColor = card,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
fun HomeHeader(isShieldActive: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentLang = LanguageManager.currentLanguageCode
    val langIcon = if (currentLang == "ar") Icons.Default.Translate else Icons.Default.Language

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(stringResource(R.string.screen_home_title), fontSize = 28.sp, fontWeight = FontWeight.Black, color = text)
            Text(stringResource(R.string.screen_home_subtitle), fontSize = 14.sp, color = textSecondary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isShieldActive) shieldGreen.copy(alpha = 0.1f) else shieldRed.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, if (isShieldActive) shieldGreen.copy(alpha = 0.3f) else shieldRed.copy(alpha = 0.3f))
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (isShieldActive) shieldGreen else shieldRed))
                    Text(if (isShieldActive) stringResource(R.string.status_protected) else stringResource(R.string.status_inactive), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isShieldActive) shieldGreen else shieldRed)
                }
            }
        }
    }
}

@Composable
fun ShieldOrb(isActive: Boolean, isCountingDown: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.clickable(onClick = onClick)) {
        if (isActive) {
            Box(Modifier.size(220.dp).scale(pulseScale).border(1.dp, primary.copy(alpha = ringAlpha), CircleShape))
            Box(Modifier.size(190.dp).scale(pulseScale * 0.95f).border(1.dp, primary.copy(alpha = ringAlpha * 0.7f), CircleShape))
        }
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(if (isActive) pulseScale else 1f)
                .clip(CircleShape)
                .then(
                    if (isActive) Modifier.background(Brush.sweepGradient(listOf(primary, accent, primary)))
                    else Modifier.background(surfaceLight)
                )
                .border(3.dp, if (isActive) primary else cardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isActive) Icons.Default.Shield else Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = if (isActive) surface else textMuted,
                    modifier = Modifier.size(56.dp)
                )
                if (isCountingDown) {
                    Text(stringResource(R.string.hint_counting_down), fontSize = 10.sp, color = surface)
                } else if (isActive) {
                    Text(stringResource(R.string.label_active), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = surface)
                }
            }
        }
        if (!isActive) {
            Text(stringResource(R.string.hint_tap_activate), fontSize = 11.sp, color = textMuted, modifier = Modifier.offset(y = 100.dp))
        }
    }
}

@Composable
fun StatsRow(activatedAt: Long?, blocksCount: Int) {
    val daysActive = if (activatedAt != null) ((System.currentTimeMillis() - activatedAt) / 86400000).toInt() else 0

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(title = stringResource(R.string.stat_days), value = "$daysActive", sub = stringResource(R.string.stat_streak), color = primary, modifier = Modifier.weight(1f))
        StatCard(title = stringResource(R.string.stat_blocks), value = "$blocksCount", sub = stringResource(R.string.stat_total), color = accent, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(title: String, value: String, sub: String, color: Color, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, cardBorder), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 12.sp, color = textSecondary)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 32.sp, fontWeight = FontWeight.Black, color = text)
            Text(sub, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ActionButtonsRow(permissionsGranted: Boolean, onNavigateToPermissions: () -> Unit, onNavigateToSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = card),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.weight(1f).clickable(onClick = onNavigateToPermissions)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = if (permissionsGranted) success else warning, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.row_permissions), color = text, fontWeight = FontWeight.Medium)
                    Text(if (permissionsGranted) stringResource(R.string.status_all_granted) else stringResource(R.string.warning_permissions_required), fontSize = 11.sp, color = if (permissionsGranted) success else warning)
                }
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = card),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.weight(1f).clickable(onClick = onNavigateToSettings)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = textMuted, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.row_settings), color = text, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.section_quick_access), fontSize = 11.sp, color = textMuted)
                }
            }
        }
    }
}

@Composable
fun PermissionWarningBar(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = warning.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, warning.copy(alpha = 0.3f))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = warning, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.warning_permissions_required), color = warning, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = warning)
        }
    }
}

@Composable
fun TrialModeCard(isTrialMode: Boolean, onToggle: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, cardBorder)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Science, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.card_trial_title), fontWeight = FontWeight.Bold, color = text)
                Text(stringResource(R.string.card_trial_subtitle), fontSize = 12.sp, color = textMuted)
            }
            Switch(checked = isTrialMode, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = surface))
        }
    }
}

@Composable
fun DeactivationDelayCard(currentDelayMinutes: Int, onClick: () -> Unit) {
    val delayText = formatDelay(currentDelayMinutes)

    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(warning.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Timer, null, tint = warning, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.card_delay_title), fontWeight = FontWeight.Bold, color = text)
                Text(stringResource(R.string.card_delay_subtitle), fontSize = 12.sp, color = textMuted)
            }
            Text(delayText, fontWeight = FontWeight.Bold, color = warning)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, null, tint = textMuted)
        }
    }
}

@Composable
fun StrictModeCard(isStrictMode: Boolean, onToggle: (Boolean) -> Unit, hasPin: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isStrictMode) accent.copy(alpha = 0.3f) else cardBorder)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.card_strict_title),
                    fontWeight = FontWeight.Bold,
                    color = text
                )
                Text(
                    if (!hasPin) stringResource(R.string.card_strict_no_pin)
                    else if (isStrictMode) stringResource(R.string.card_strict_on)
                    else stringResource(R.string.card_strict_off),
                    fontSize = 12.sp,
                    color = textMuted
                )
            }
            Switch(
                checked = isStrictMode,
                onCheckedChange = { if (hasPin) onToggle(it) },
                enabled = hasPin,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = accent,
                    checkedThumbColor = surface
                )
            )
        }
    }
}

@Composable
fun GamificationCard(level: Int, xpPoints: Int, xpProgress: Float, xpForNextLevel: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.EmojiEvents, null, tint = accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.level_title, level), fontWeight = FontWeight.Bold, color = text)
                    Text(stringResource(R.string.xp_subtitle, xpPoints, xpForNextLevel), fontSize = 12.sp, color = textMuted)
                }
                Surface(
                    color = accent.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = stringResource(R.string.level_badge, level),
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { xpProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = cardBorder
            )
        }
    }
}

private fun formatDelay(minutes: Int): String {
    return when {
        minutes <= 0 -> ""
        minutes == 43200 -> "1 Month"
        minutes % 1440 == 0 -> "${minutes / 1440} Days"
        else -> "$minutes Mins"
    }
}
