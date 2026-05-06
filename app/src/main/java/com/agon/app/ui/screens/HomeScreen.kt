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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.data.GuardianState
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.GuardianViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GuardianViewModel = viewModel(),
    onNavigateToPermissions: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showDelaySheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Countdown logic
    var remainingMillis by remember { mutableStateOf(0L) }
    
    LaunchedEffect(state.countdownEndTime) {
        while (state.countdownEndTime != null) {
            val now = System.currentTimeMillis()
            remainingMillis = state.countdownEndTime!! - now
            if (remainingMillis <= 0) {
                viewModel.finalizeDeactivation()
                remainingMillis = 0
                break
            }
            delay(1000)
        }
    }

    Scaffold(
        containerColor = background,
        topBar = { HomeHeader(state.isShieldActive) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            CountdownBanner(
                isVisible = state.countdownEndTime != null && remainingMillis > 0,
                remainingMillis = remainingMillis,
                onCancel = { viewModel.cancelCountdown() }
            )

            Spacer(modifier = Modifier.height(32.dp))

            ShieldOrb(
                isActive = state.isShieldActive,
                isCountingDown = state.countdownEndTime != null,
                onClick = {
                    if (!state.isShieldActive) {
                        if (!state.permissionsGranted) {
                            onNavigateToPermissions()
                        } else {
                            viewModel.toggleShield()
                        }
                    } else {
                        viewModel.toggleShield()
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            StatsRow(
                activatedAt = state.shieldActivatedAt,
                blocksCount = state.blocksCount
            )

            Spacer(modifier = Modifier.height(16.dp))

            ActionButtonsRow(
                permissionsGranted = state.permissionsGranted,
                onNavigateToPermissions = onNavigateToPermissions,
                onNavigateToSettings = onNavigateToSettings
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!state.permissionsGranted) {
                PermissionWarningBar(onClick = onNavigateToPermissions)
                Spacer(modifier = Modifier.height(16.dp))
            }

            TrialModeCard(
                isTrialMode = state.isTrialModeActive,
                onToggle = { viewModel.toggleTrialMode() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            DeactivationDelayCard(
                currentDelayMinutes = state.deactivationDelayMinutes,
                onClick = { showDelaySheet = true }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showDelaySheet) {
            ModalBottomSheet(
                onDismissRequest = { showDelaySheet = false },
                containerColor = surface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = textMuted) }
            ) {
                DelaySelectionSheet(
                    currentDelayMinutes = state.deactivationDelayMinutes,
                    onSelect = { 
                        viewModel.setDeactivationDelay(it)
                        showDelaySheet = false
                    }
                )
            }
        }
    }
}

@Composable
fun HomeHeader(isShieldActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Guardian",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = text
            )
            Text(
                text = "Digital Wellness Shield",
                fontSize = 14.sp,
                color = textSecondary
            )
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isShieldActive) shieldGreen.copy(alpha = 0.1f) else shieldRed.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, if (isShieldActive) shieldGreen.copy(alpha = 0.3f) else shieldRed.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isShieldActive) shieldGreen else shieldRed)
                )
                Text(
                    text = if (isShieldActive) "Protected" else "Inactive",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isShieldActive) shieldGreen else shieldRed
                )
            }
        }
    }
}

@Composable
fun CountdownBanner(
    isVisible: Boolean,
    remainingMillis: Long,
    onCancel: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically() + fadeIn(),
        exit = fadeOut()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = warning.copy(alpha = 0.1f)),
            border = BorderStroke(1.dp, warning.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Timer",
                        tint = warning
                    )
                    Column {
                        Text(
                            text = "Deactivating in...",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                        Text(
                            text = formatMillis(remainingMillis),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = warning
                        )
                    }
                }
                
                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(contentColor = text)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val days = totalSeconds / (24 * 3600)
    val hours = (totalSeconds % (24 * 3600)) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        else -> "${minutes}m ${seconds}s"
    }
}

@Composable
fun ShieldOrb(
    isActive: Boolean,
    isCountingDown: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isActive && !isCountingDown) 1.06f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isActive && !isCountingDown) 0.9f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val color = if (isActive) shieldGreen else shieldRed

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() })
    ) {
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer ring
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale)
                    .border(1.dp, color.copy(alpha = if (isActive) alpha * 0.5f else 0.1f), CircleShape)
            )
            
            // Middle ring
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(scale * 0.98f)
                    .border(1.5.dp, color.copy(alpha = if (isActive) alpha else 0.2f), CircleShape)
            )
            
            // Inner filled circle
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = 0.2f),
                                color.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .border(2.dp, color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isActive) Icons.Filled.Shield else Icons.Outlined.Shield,
                        contentDescription = "Shield",
                        tint = color,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isActive) "ACTIVE" else "INACTIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp,
                        color = color
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = when {
                isCountingDown -> "Counting down..."
                isActive -> "Tap to deactivate"
                else -> "Tap to activate"
            },
            color = textMuted,
            fontSize = 14.sp
        )
    }
}

@Composable
fun StatsRow(
    activatedAt: Long?,
    blocksCount: Int
) {
    val days = if (activatedAt != null) {
        val diff = System.currentTimeMillis() - activatedAt
        (diff / (1000 * 60 * 60 * 24)).toInt()
    } else 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.CalendarToday,
            value = days.toString(),
            label = "Days",
            subLabel = "streak",
            color = primary
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Security,
            value = blocksCount.toString(),
            label = "Blocks",
            subLabel = "total",
            color = accent
        )
    }
}

@Composable
fun ActionButtonsRow(
    permissionsGranted: Boolean,
    onNavigateToPermissions: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onNavigateToPermissions),
            colors = CardDefaults.cardColors(containerColor = card),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (permissionsGranted) success.copy(alpha = 0.15f) else warning.copy(alpha = 0.15f))
                        .border(1.dp, if (permissionsGranted) success.copy(alpha = 0.3f) else warning.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = "Permissions",
                        tint = if (permissionsGranted) success else warning,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("Permissions", color = text, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                if (!permissionsGranted) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(warning)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("!", color = surface, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onNavigateToSettings),
            colors = CardDefaults.cardColors(containerColor = card),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(primary.copy(alpha = 0.15f))
                        .border(1.dp, primary.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("Settings", color = text, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun PermissionWarningBar(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = warning.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, warning.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(warning.copy(alpha = 0.15f))
                    .border(1.dp, warning.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = warning, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Permissions required for shield",
                color = warning,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = warning)
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    subLabel: String,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f))
                    .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = text
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    color = color,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = subLabel,
                    fontSize = 14.sp,
                    color = textMuted
                )
            }
        }
    }
}

@Composable
fun TrialModeCard(
    isTrialMode: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(warning.copy(alpha = 0.15f))
                        .border(1.dp, warning.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = "Trial Mode",
                        tint = warning,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Trial Mode",
                        fontWeight = FontWeight.Bold,
                        color = text
                    )
                    Text(
                        text = "Test features without delay restriction",
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                }
            }
            
            Switch(
                checked = isTrialMode,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = surface,
                    checkedTrackColor = warning,
                    uncheckedThumbColor = textSecondary,
                    uncheckedTrackColor = surfaceLight
                )
            )
        }
    }
}

@Composable
fun DeactivationDelayCard(
    currentDelayMinutes: Long,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.15f))
                        .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = "Deactivation Delay",
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Deactivation Delay",
                        fontWeight = FontWeight.Bold,
                        color = text
                    )
                    Text(
                        text = "Time required before turning off shield",
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDelay(currentDelayMinutes),
                    color = accent,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = textMuted
                )
            }
        }
    }
}

fun formatDelay(minutes: Long): String {
    val days = minutes / (24 * 60)
    return when {
        days == 30L -> "1 Month"
        days > 0 -> "$days Days"
        else -> "$minutes Mins"
    }
}

@Composable
fun DelaySelectionSheet(
    currentDelayMinutes: Long,
    onSelect: (Long) -> Unit
) {
    val options = listOf(
        2 * 24 * 60L to "2 Days",
        7 * 24 * 60L to "7 Days",
        15 * 24 * 60L to "15 Days",
        30 * 24 * 60L to "1 Month"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, top = 16.dp)
    ) {
        Text(
            text = "Deactivation Delay",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = text,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        Text(
            text = "Choose how long you must wait before the shield can be turned off.",
            fontSize = 14.sp,
            color = textSecondary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
        )
        
        options.forEach { (minutes, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(minutes) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    color = text
                )
                RadioButton(
                    selected = currentDelayMinutes == minutes,
                    onClick = { onSelect(minutes) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = accent,
                        unselectedColor = textMuted
                    )
                )
            }
        }
    }
}
