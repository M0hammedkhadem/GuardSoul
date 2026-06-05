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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.agon.app.LanguageManager
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.utils.DisciplineTier
import com.agon.app.utils.DisciplineTiers
import com.agon.app.utils.ShareCardGenerator
import com.agon.app.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
    val tier by vm.tier.collectAsStateWithLifecycle()
    val disciplineScore by vm.disciplineScore.collectAsStateWithLifecycle()
    val countdownActive by vm.countdownActive.collectAsStateWithLifecycle()
    val remainingSeconds by vm.remainingSeconds.collectAsStateWithLifecycle()
    val deactivationDelay by vm.deactivationDelay.collectAsStateWithLifecycle()
    val showPinDialog by vm.showPinDialog.collectAsStateWithLifecycle()
    val showPartnerDialog by vm.showPartnerDialog.collectAsStateWithLifecycle()
    val partnerError by vm.partnerError.collectAsStateWithLifecycle()
    val partnerRequestInFlight by vm.partnerRequestInFlight.collectAsStateWithLifecycle()
    val pinError by vm.pinError.collectAsStateWithLifecycle()
    val trialMode by vm.trialMode.collectAsStateWithLifecycle()

    var pinInput by remember { mutableStateOf("") }
    var partnerCodeInput by remember { mutableStateOf("") }
    var showDelaySheet by remember { mutableStateOf(false) }
    val shareScope = rememberCoroutineScope()

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
                Spacer(Modifier.height(20.dp))
                ActionTile(
                    title = stringResource(R.string.card_trial_title),
                    subtitle = stringResource(R.string.card_trial_subtitle),
                    icon = Icons.Default.Science,
                    iconColor = warning,
                    trailingContent = {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (trialMode) warning.copy(alpha = 0.2f) else surfaceLight,
                            border = BorderStroke(1.dp, if (trialMode) warning else cardBorder)
                        ) {
                            Text(
                                if (trialMode) stringResource(R.string.trial_mode_on) else stringResource(R.string.trial_mode_off),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = if (trialMode) warning else text
                            )
                        }
                    },
                    onClick = { vm.toggleTrialMode() }
                )
            }

            item {
                Spacer(Modifier.height(12.dp))
                TierCard(
                    tier = tier,
                    score = disciplineScore,
                    progressToNext = DisciplineTiers.progressToNext(disciplineScore),
                    nextTier = DisciplineTiers.nextTier(tier)
                )
            }

            item {
                Spacer(Modifier.height(12.dp))
                ActionTile(
                    title = stringResource(R.string.card_delay_title),
                    subtitle = stringResource(R.string.card_delay_subtitle),
                    icon = Icons.Default.HourglassEmpty,
                    iconColor = Color(0xFF7C3AED),
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                deactivationDelayLabel(deactivationDelay),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C3AED),
                                fontSize = 13.sp
                            )
                            Icon(Icons.Default.ChevronRight, null, tint = textMuted, modifier = Modifier.size(20.dp))
                        }
                    },
                    onClick = { showDelaySheet = true }
                )
            }

            item {
                Spacer(Modifier.height(12.dp))
                ActionTile(
                    title = stringResource(R.string.share_card_title),
                    subtitle = stringResource(R.string.share_card_subtitle),
                    icon = Icons.Default.Share,
                    iconColor = Color(0xFF22C55E),
                    trailingContent = {},
                    onClick = {
                        val totalBlocks = totalBlocks
                        shareScope.launch {
                            val data = vm.buildShareCardData(weeklyBlockCount = totalBlocks)
                            val bmp = ShareCardGenerator.render(data)
                            val chooser = ShareCardGenerator.share(context, bmp)
                            context.startActivity(chooser)
                        }
                    }
                )
            }

            item {
                Spacer(Modifier.height(12.dp))
                val studyRoomActive by vm.studyRoomRemainingMs.collectAsStateWithLifecycle()
                ActionTile(
                    title = if (studyRoomActive > 0L)
                        stringResource(R.string.study_room_active_title, (studyRoomActive / 60_000L).coerceAtLeast(1))
                    else
                        stringResource(R.string.study_room_title),
                    subtitle = if (studyRoomActive > 0L)
                        stringResource(R.string.study_room_active_subtitle)
                    else
                        stringResource(R.string.study_room_subtitle),
                    icon = Icons.Default.School,
                    iconColor = Color(0xFF22C55E),
                    trailingContent = {},
                    onClick = {
                        if (studyRoomActive > 0L) vm.stopStudyRoom()
                        else vm.startStudyRoom(60)
                    }
                )
            }
        }

        if (showDelaySheet) {
            DelaySelectorSheet(
                current = deactivationDelay,
                onSelect = {
                    vm.setDeactivationDelay(it)
                    showDelaySheet = false
                },
                onDismiss = { showDelaySheet = false }
            )
        }

        if (countdownActive) {
            CountdownOverlay(
                remainingSeconds = remainingSeconds,
                deactivationDelay = deactivationDelay,
                onCancel = { vm.cancelDeactivation() }
            )
        }
    }

    if (showPinDialog) {
        PinVerifyDialog(
            pinInput = pinInput,
            onPinInputChange = {
                if (it.length <= 6) {
                    pinInput = it
                    // Clear the error highlight as soon as the user starts
                    // re-typing — otherwise the field stays red even after
                    // a correct entry on the next attempt.
                    if (pinError) vm.dismissPinError()
                }
            },
            isError = pinError,
            onConfirm = { vm.verifyPin(pinInput); pinInput = "" },
            onDismiss = { vm.dismissPinDialog(); pinInput = "" }
        )
    }

    if (showPartnerDialog) {
        PartnerApprovalDialog(
            codeInput = partnerCodeInput,
            onCodeInputChange = {
                if (it.length <= 8) {
                    partnerCodeInput = it.filter { ch -> ch.isDigit() }
                }
            },
            errorMessage = partnerError,
            inFlight = partnerRequestInFlight,
            onConfirm = { vm.verifyPartnerCode(partnerCodeInput); partnerCodeInput = "" },
            onResend = { vm.resendPartnerRequest(); partnerCodeInput = "" },
            onDismiss = { vm.dismissPartnerDialog(); partnerCodeInput = "" }
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
                        if (isShieldActive) "Active" else "Inactive",
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
            Text(
                value,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = text
            )
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = text
            )
            Text(
                subLabel,
                fontSize = 11.sp,
                color = textSecondary
            )
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

    val orbColor = if (isActive) Color(0xFF00D4AA) else Color(0xFFFF4757)
    val statusText = if (isActive) "ACTIVE" else "INACTIVE"

    Column(
        modifier = Modifier.fillMaxWidth().clickable(
            onClick = onClick,
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
            // Outer Rings
            Box(Modifier.size(255.dp).border(0.5.dp, orbColor.copy(alpha = 0.12f), CircleShape))
            Box(Modifier.size(220.dp).border(0.8.dp, orbColor.copy(alpha = 0.22f), CircleShape))

            // Main Core
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
            text = if (isActive) "Tap to deactivate shield" else "Tap to activate shield",
            fontSize = 14.sp,
            color = textSecondary.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun CountdownOverlay(
    remainingSeconds: Int,
    deactivationDelay: Int,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { },
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = surface),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
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
                Spacer(Modifier.height(20.dp))
                val totalSeconds = remainingSeconds
                val days = totalSeconds / 86_400
                val hours = (totalSeconds % 86_400) / 3_600
                val minutes = (totalSeconds % 3_600) / 60
                val seconds = totalSeconds % 60
                val display = when {
                    days > 0 -> stringResource(R.string.countdown_days_hours_minutes_seconds, days, hours, minutes, seconds)
                    hours > 0 -> stringResource(R.string.countdown_hours_minutes_seconds, hours, minutes, seconds)
                    else -> stringResource(R.string.countdown_minutes_seconds, minutes, seconds)
                }
                Text(
                    display,
                    fontSize = if (days > 0) 36.sp else 48.sp,
                    fontWeight = FontWeight.Black,
                    color = warning
                )
                Spacer(Modifier.height(12.dp))
                val totalDelaySeconds = (deactivationDelay * 86_400L).coerceAtLeast(1L)
                LinearProgressIndicator(
                    progress = { (totalSeconds.toFloat() / totalDelaySeconds).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = warning,
                    trackColor = cardBorder
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = surfaceLight)
                ) {
                    Text(stringResource(R.string.btn_cancel_deactivation), color = text)
                }
            }
        }
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
        title = {
            Text(stringResource(R.string.pin_strict_title), fontWeight = FontWeight.Bold)
        },
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
private fun deactivationDelayLabel(days: Int): String = when (days) {
    0 -> stringResource(R.string.delay_no_delay)
    2 -> stringResource(R.string.delay_2_days)
    7 -> stringResource(R.string.delay_7_days)
    15 -> stringResource(R.string.delay_15_days)
    30 -> stringResource(R.string.delay_1_month)
    else -> "$days days"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DelaySelectorSheet(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(0, 2, 7, 15, 30)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = surface) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text(
                stringResource(R.string.card_delay_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = text
            )
            Spacer(Modifier.height(16.dp))
            options.forEach { days ->
                val selected = days == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(days) }
                        .background(if (selected) Color(0xFF7C3AED).copy(alpha = 0.15f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onSelect(days) },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF7C3AED))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(deactivationDelayLabel(days), color = text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * "Accountability Partner" approval dialog (Bulldog Blocker + Canopy
 * style). The user has just emailed a 6-digit unlock code to their
 * partner; once the partner confirms, the user types the code back
 * here. The code expires after 5 minutes — the "Resend" button
 * requests a fresh one.
 */
@Composable
fun PartnerApprovalDialog(
    codeInput: String,
    onCodeInputChange: (String) -> Unit,
    errorMessage: String?,
    inFlight: Boolean,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.partner_dialog_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.partner_dialog_desc),
                    color = textSecondary,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = onCodeInputChange,
                    label = { Text(stringResource(R.string.partner_code_label)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    ),
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = if (errorMessage != null) {
                        { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = codeInput.length >= 4 && !inFlight,
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.partner_btn_confirm)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onResend, enabled = !inFlight) {
                    Text(stringResource(R.string.partner_btn_resend))
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        },
        containerColor = surface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun TierCard(
    tier: DisciplineTier,
    score: Int,
    progressToNext: Float,
    nextTier: DisciplineTier?
) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = tier.emoji, fontSize = 36.sp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tier_section_title),
                        color = textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(tier.titleRes),
                        color = text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(tier.subtitleRes),
                        color = textMuted,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = "$score",
                    color = Color(0xFF7C3AED),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progressToNext.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF7C3AED),
                trackColor = Color(0xFF7C3AED).copy(alpha = 0.18f)
            )
            Spacer(Modifier.height(6.dp))
            val progressLabel = if (nextTier == null) {
                stringResource(R.string.tier_progress_at_top)
            } else {
                val remaining = (nextTier.minScore - score).coerceAtLeast(0)
                stringResource(R.string.tier_progress_to_next, remaining)
            }
            Text(text = progressLabel, color = textMuted, fontSize = 11.sp)
        }
    }
}
