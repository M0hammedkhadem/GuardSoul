package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    val pornBlockerActive by vm.pornBlockerActive.collectAsStateWithLifecycle()
    val aiScannerActive by vm.aiScannerActive.collectAsStateWithLifecycle()
    val uninstallProtectionActive by vm.uninstallProtectionActive.collectAsStateWithLifecycle()
    val facebookMode by vm.facebookMode.collectAsStateWithLifecycle()
    val blockedLinksToday by vm.blockedLinksToday.collectAsStateWithLifecycle()
    val blockedAppsToday by vm.blockedAppsToday.collectAsStateWithLifecycle()
    val daysActive by vm.daysActive.collectAsStateWithLifecycle()
    val mostBlockedApp by vm.mostBlockedApp.collectAsStateWithLifecycle()

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
    var showServiceDetail by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            HomeHeader(isShieldActive = shieldActive)
        }

        item {
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
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            StatsRow(blocksToday = blocksToday, totalBlocks = totalBlocks)
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            StreakAndLevelRow(streakCount = streakCount, level = level, xpPoints = xpPoints, xpProgress = vm.xpProgress, xpForNextLevel = vm.xpForNextLevel)
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(
                icon = Icons.Default.Layers,
                title = stringResource(R.string.dashboard_services_section)
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            ServiceCard(
                icon = Icons.Default.Language,
                title = stringResource(R.string.service_web_blocker),
                description = stringResource(R.string.service_web_blocker_desc),
                isActive = pornBlockerActive,
                activeColor = neonGreen,
                onToggle = { vm.setPornBlocker(it) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            ServiceCard(
                icon = Icons.Default.Facebook,
                title = stringResource(R.string.service_facebook_blocker),
                description = stringResource(R.string.service_facebook_blocker_desc),
                isActive = facebookMode != "off",
                activeColor = neonGreen,
                onToggle = { vm.setFacebookMode(if (it) "full" else "off") }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            ServiceCard(
                icon = Icons.Default.Visibility,
                title = stringResource(R.string.service_ai_scanner),
                description = stringResource(R.string.service_ai_scanner_desc),
                isActive = aiScannerActive,
                activeColor = accent,
                onToggle = { vm.setAiScanner(it) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            ServiceCard(
                icon = Icons.Default.AdminPanelSettings,
                title = stringResource(R.string.service_uninstall_protection),
                description = stringResource(R.string.service_uninstall_protection_desc),
                isActive = uninstallProtectionActive,
                activeColor = neonGreen,
                onToggle = { vm.setUninstallProtection(it) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(
                icon = Icons.Default.FamilyRestroom,
                title = stringResource(R.string.dashboard_parental_section)
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            ParentalStatsGrid(
                blockedToday = blocksToday,
                blockedLinksToday = blockedLinksToday,
                blockedAppsToday = blockedAppsToday,
                daysActive = daysActive,
                streakCount = streakCount,
                mostBlockedApp = mostBlockedApp?.appLabel
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            QuickActionsRow(
                permissionsGranted = permissionsGranted,
                onNavigateToPermissions = onNavigateToPermissions,
                onNavigateToSettings = onNavigateToSettings
            )
        }

        if (!shieldActive) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                PermissionWarningBar(onClick = onNavigateToPermissions)
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            TrialModeCard(
                isTrialMode = trialMode,
                onToggle = { vm.setTrialMode(it) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            DeactivationDelayCard(
                currentDelayMinutes = deactivationDelay,
                onClick = { showDelayDialog = true }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            StrictModeCard(
                isStrictMode = strictMode,
                onToggle = { vm.setStrictMode(it) },
                hasPin = hasPin
            )
        }
    }

    if (countdownActive) {
        CountdownOverlay(
            remainingSeconds = remainingSeconds,
            deactivationDelay = deactivationDelay,
            onCancel = { vm.cancelDeactivation() }
        )
    }

    if (showPinDialog) {
        PinVerifyDialog(
            pinInput = pinInput,
            onPinInputChange = { if (it.length <= 6) pinInput = it },
            isError = pinError,
            onConfirm = { vm.verifyPin(pinInput); pinInput = "" },
            onDismiss = { vm.dismissPinDialog(); pinInput = "" }
        )
    }

    if (showDelayDialog) {
        DeactivationDelayDialog(
            currentDelay = deactivationDelay,
            onSelect = { vm.setDeactivationDelay(it); showDelayDialog = false },
            onDismiss = { showDelayDialog = false }
        )
    }
}

@Composable
fun HomeHeader(isShieldActive: Boolean) {
    val currentLang = LanguageManager.currentLanguageCode
    val langIcon = if (currentLang == "ar") Icons.Default.Translate else Icons.Default.Language

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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isShieldActive) neonGreen.copy(alpha = 0.1f) else rubyRed.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, if (isShieldActive) neonGreen.copy(alpha = 0.3f) else rubyRed.copy(alpha = 0.3f))
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isShieldActive) neonGreen else rubyRed)
                    )
                    Text(
                        if (isShieldActive) stringResource(R.string.status_protected) else stringResource(R.string.status_inactive),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isShieldActive) neonGreen else rubyRed
                    )
                }
            }
        }
    }
}

@Composable
fun ShieldOrb(isActive: Boolean, isCountingDown: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring"
    )
    val glowColor = if (isActive) neonGreen else textMuted

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .clickable(onClick = onClick)
    ) {
        if (isActive) {
            Box(
                Modifier
                    .size(220.dp)
                    .scale(pulseScale)
                    .border(1.5.dp, neonGreen.copy(alpha = ringAlpha), CircleShape)
            )
            Box(
                Modifier
                    .size(195.dp)
                    .scale(pulseScale * 0.95f)
                    .border(1.dp, neonGreen.copy(alpha = ringAlpha * 0.5f), CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(if (isActive) pulseScale else 1f)
                .clip(CircleShape)
                .then(
                    if (isActive) Modifier.background(
                        Brush.sweepGradient(
                            listOf(
                                neonGreen,
                                neonGreen.copy(alpha = 0.7f),
                                primary,
                                neonGreen.copy(alpha = 0.7f),
                                neonGreen
                            )
                        )
                    )
                    else Modifier.background(surfaceLight)
                )
                .border(3.dp, if (isActive) neonGreen else cardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isActive) Icons.Default.Shield else Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = if (isActive) background else textMuted,
                    modifier = Modifier.size(56.dp)
                )
                if (isCountingDown) {
                    Text(
                        stringResource(R.string.hint_counting_down),
                        fontSize = 10.sp,
                        color = background
                    )
                } else if (isActive) {
                    Text(
                        stringResource(R.string.label_active),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = background
                    )
                }
            }
        }
        if (!isActive) {
            Text(
                stringResource(R.string.hint_tap_activate),
                fontSize = 11.sp,
                color = textMuted,
                modifier = Modifier.offset(y = 100.dp)
            )
        }
    }
}

@Composable
fun StatsRow(blocksToday: Int, totalBlocks: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = stringResource(R.string.statistics_today),
            value = "$blocksToday",
            sub = stringResource(R.string.stat_blocks),
            color = neonGreen,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = stringResource(R.string.statistics_total),
            value = "$totalBlocks",
            sub = stringResource(R.string.stat_blocks),
            color = primary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StreakAndLevelRow(streakCount: Int, level: Int, xpPoints: Int, xpProgress: Float, xpForNextLevel: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = card),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(warning.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Whatshot,
                        null,
                        tint = warning,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "$streakCount",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = text
                    )
                    Text(
                        stringResource(R.string.stat_streak),
                        fontSize = 11.sp,
                        color = textSecondary
                    )
                }
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = card),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.weight(1f)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            stringResource(R.string.level_title, level),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = text
                        )
                        Text(
                            "$xpPoints XP",
                            fontSize = 11.sp,
                            color = textSecondary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { xpProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = accent,
                    trackColor = cardBorder
                )
            }
        }
    }
}

@Composable
fun SectionLabel(icon: ImageVector, title: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = primary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = text
        )
    }
}

@Composable
fun ServiceCard(
    icon: ImageVector,
    title: String,
    description: String,
    isActive: Boolean,
    activeColor: Color,
    onToggle: (Boolean) -> Unit
) {
    val animatedTrackColor by animateColorAsState(
        targetValue = if (isActive) activeColor else mutedSwitchTrack,
        animationSpec = tween(300),
        label = "track"
    )
    val animatedThumbColor by animateColorAsState(
        targetValue = if (isActive) activeColor else mutedSwitchThumb,
        animationSpec = tween(300),
        label = "thumb"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) activeColor.copy(alpha = 0.25f) else cardBorder,
        animationSpec = tween(300),
        label = "border"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isActive) activeColor.copy(alpha = 0.12f)
                        else surfaceLight
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isActive) activeColor else textMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = text
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    fontSize = 12.sp,
                    color = textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = isActive,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = animatedTrackColor,
                    checkedThumbColor = background,
                    uncheckedTrackColor = mutedSwitchTrack,
                    uncheckedThumbColor = mutedSwitchThumb
                )
            )
        }
    }
}

@Composable
fun ParentalStatsGrid(
    blockedToday: Int,
    blockedLinksToday: Int,
    blockedAppsToday: Int,
    daysActive: Int,
    streakCount: Int,
    mostBlockedApp: String?
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ParentalStatCard(
                icon = Icons.Default.Block,
                label = stringResource(R.string.stat_blocked_today),
                value = "$blockedToday",
                color = rubyRed,
                modifier = Modifier.weight(1f)
            )
            ParentalStatCard(
                icon = Icons.Default.LinkOff,
                label = stringResource(R.string.stat_blocked_links),
                value = "$blockedLinksToday",
                color = warning,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ParentalStatCard(
                icon = Icons.Default.AppBlocking,
                label = stringResource(R.string.stat_blocked_apps),
                value = "$blockedAppsToday",
                color = danger,
                modifier = Modifier.weight(1f)
            )
            ParentalStatCard(
                icon = Icons.Default.CalendarMonth,
                label = stringResource(R.string.stat_days_active),
                value = "$daysActive",
                color = primary,
                modifier = Modifier.weight(1f)
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = card),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(neonGreen.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.TrendingUp,
                        null,
                        tint = neonGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.stat_most_blocked),
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                    Text(
                        mostBlockedApp ?: stringResource(R.string.stat_none),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = text
                    )
                }
            }
        }
    }
}

@Composable
fun ParentalStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(14.dp)
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = text
            )
            Text(
                label,
                fontSize = 11.sp,
                color = textSecondary
            )
        }
    }
}

@Composable
fun QuickActionsRow(permissionsGranted: Boolean, onNavigateToPermissions: () -> Unit, onNavigateToSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = card),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onNavigateToPermissions)
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Shield,
                    null,
                    tint = if (permissionsGranted) neonGreen else warning,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        stringResource(R.string.row_permissions),
                        color = text,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                    Text(
                        if (permissionsGranted) stringResource(R.string.status_all_granted)
                        else stringResource(R.string.warning_permissions_required),
                        fontSize = 10.sp,
                        color = if (permissionsGranted) neonGreen else warning
                    )
                }
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = card),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onNavigateToSettings)
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Settings, null, tint = textMuted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        stringResource(R.string.row_settings),
                        color = text,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                    Text(
                        stringResource(R.string.section_quick_access),
                        fontSize = 10.sp,
                        color = textMuted
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionWarningBar(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = warning.copy(alpha = 0.08f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, warning.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, tint = warning, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.warning_permissions_required),
                color = warning,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ChevronRight, null, tint = warning)
        }
    }
}

@Composable
fun TrialModeCard(isTrialMode: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Science, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.card_trial_title), fontWeight = FontWeight.Bold, color = text, fontSize = 14.sp)
                Text(stringResource(R.string.card_trial_subtitle), fontSize = 12.sp, color = textMuted)
            }
            Switch(
                checked = isTrialMode,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = accent,
                    checkedThumbColor = background,
                    uncheckedTrackColor = mutedSwitchTrack,
                    uncheckedThumbColor = mutedSwitchThumb
                )
            )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(warning.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Timer, null, tint = warning, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.card_delay_title), fontWeight = FontWeight.Bold, color = text, fontSize = 14.sp)
                Text(stringResource(R.string.card_delay_subtitle), fontSize = 12.sp, color = textMuted)
            }
            if (delayText.isNotEmpty()) {
                Text(delayText, fontWeight = FontWeight.Bold, color = warning, fontSize = 13.sp)
            }
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
        border = BorderStroke(1.dp, if (isStrictMode) accent.copy(alpha = 0.3f) else cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.card_strict_title), fontWeight = FontWeight.Bold, color = text, fontSize = 14.sp)
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
                    checkedThumbColor = background,
                    uncheckedTrackColor = mutedSwitchTrack,
                    uncheckedThumbColor = mutedSwitchThumb
                )
            )
        }
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
                val minutes = remainingSeconds / 60
                val seconds = remainingSeconds % 60
                Text(
                    stringResource(R.string.countdown_minutes_seconds, minutes, seconds),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = warning
                )
                Spacer(Modifier.height(12.dp))
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
fun DeactivationDelayDialog(
    currentDelay: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val delayOptions = listOf(
        0 to stringResource(R.string.delay_none),
        2880 to stringResource(R.string.delay_2_days),
        10080 to stringResource(R.string.delay_7_days),
        21600 to stringResource(R.string.delay_15_days),
        43200 to stringResource(R.string.delay_1_month)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.card_delay_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                delayOptions.forEach { (minutes, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .then(
                                if (minutes == currentDelay) Modifier
                                    .background(primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                else Modifier
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = minutes == currentDelay,
                            onClick = { onSelect(minutes) },
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
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
        containerColor = surface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun StatCard(title: String, value: String, sub: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 12.sp, color = textSecondary)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 32.sp, fontWeight = FontWeight.Black, color = text)
            Text(sub, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
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
