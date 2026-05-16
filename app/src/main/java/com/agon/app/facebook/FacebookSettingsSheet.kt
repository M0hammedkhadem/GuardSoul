package com.agon.app.facebook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.agon.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacebookSettingsSheet(
    settings: FacebookSettings,
    onToggleBlocker: () -> Unit,
    onSetThreshold: (Int) -> Unit,
    onSetScheduleEnabled: (Boolean) -> Unit,
    onSetScheduleStart: (Int) -> Unit,
    onSetScheduleEnd: (Int) -> Unit,
    onSetFriendProtection: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = textMuted) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Facebook Blocker Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = text,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Blocker Toggle
            SettingsRow(
                icon = Icons.Default.Shield,
                title = "Blocker",
                subtitle = if (settings.blockerEnabled) "Active" else "Disabled",
                trailing = {
                    Switch(
                        checked = settings.blockerEnabled,
                        onCheckedChange = { onToggleBlocker() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = surface,
                            checkedTrackColor = success,
                            uncheckedThumbColor = textSecondary,
                            uncheckedTrackColor = surfaceLight
                        )
                    )
                }
            )

            HorizontalDivider(color = cardBorder)

            // Sensitivity Slider
            Text(
                text = "Detection Sensitivity",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = text
            )
            Text(
                text = "Confidence threshold: ${settings.confidenceThreshold}%",
                fontSize = 12.sp,
                color = textMuted
            )
            Slider(
                value = settings.confidenceThreshold.toFloat(),
                onValueChange = { onSetThreshold(it.toInt()) },
                valueRange = 70f..95f,
                steps = 24,
                colors = SliderDefaults.colors(
                    thumbColor = primary,
                    activeTrackColor = primary,
                    inactiveTrackColor = surfaceLight
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("70% (More)", fontSize = 11.sp, color = textMuted)
                Text("95% (Fewer)", fontSize = 11.sp, color = textMuted)
            }

            HorizontalDivider(color = cardBorder)

            // Schedule
            SettingsRow(
                icon = Icons.Default.Schedule,
                title = "Scheduled Blocking",
                subtitle = if (settings.scheduleEnabled) {
                    "${settings.scheduleStartHour}:00 - ${settings.scheduleEndHour}:00"
                } else "Off",
                trailing = {
                    Switch(
                        checked = settings.scheduleEnabled,
                        onCheckedChange = { onSetScheduleEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = surface,
                            checkedTrackColor = primary,
                            uncheckedThumbColor = textSecondary,
                            uncheckedTrackColor = surfaceLight
                        )
                    )
                }
            )

            if (settings.scheduleEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TimePickerChip(
                        label = "Start",
                        hour = settings.scheduleStartHour,
                        onHourChange = onSetScheduleStart,
                        modifier = Modifier.weight(1f)
                    )
                    TimePickerChip(
                        label = "End",
                        hour = settings.scheduleEndHour,
                        onHourChange = onSetScheduleEnd,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = cardBorder)

            // Friend Protection
            SettingsRow(
                icon = Icons.Default.People,
                title = "Never block friends",
                subtitle = if (settings.friendProtection) "Friends' posts are safe" else "May block friends' reels",
                trailing = {
                    Switch(
                        checked = settings.friendProtection,
                        onCheckedChange = { onSetFriendProtection(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = surface,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = textSecondary,
                            uncheckedTrackColor = surfaceLight
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Stats summary
            Card(
                colors = CardDefaults.cardColors(containerColor = card),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Today's Stats", fontWeight = FontWeight.Bold, color = textSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("Blocked", "${settings.dailyBlockedCount}", danger)
                        StatItem("Saved", "${settings.timeSavedMinutes}m", success)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(surfaceLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, fontSize = 14.sp, color = text, fontWeight = FontWeight.Medium)
                Text(text = subtitle, fontSize = 11.sp, color = textMuted)
            }
        }
        trailing()
    }
}

@Composable
private fun TimePickerChip(
    label: String,
    hour: Int,
    onHourChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val hours = (0..23).toList()

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            color = surfaceLight,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = label, fontSize = 11.sp, color = textMuted)
                    Text(text = "${hour}:00", fontSize = 14.sp, color = text, fontWeight = FontWeight.Medium)
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = textMuted, modifier = Modifier.size(20.dp))
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            hours.forEach { h ->
                DropdownMenuItem(
                    text = { Text("${h}:00", color = if (h == hour) primary else text) },
                    onClick = {
                        onHourChange(h)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 12.sp, color = textMuted)
    }
}
