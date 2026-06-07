package com.agon.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.R
import com.agon.app.data.BadgeWithState
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.ProfileViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    vm: ProfileViewModel,
    onBack: () -> Unit
) {
    val name by vm.profileName.collectAsStateWithLifecycle()
    val shieldActive by vm.shieldActive.collectAsStateWithLifecycle()
    val totalBlocks by vm.totalBlocks.collectAsStateWithLifecycle()
    val hasPin by vm.hasPin.collectAsStateWithLifecycle()
    val trialMode by vm.trialMode.collectAsStateWithLifecycle()
    val xpPoints by vm.xpPoints.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val streakCount by vm.streakCount.collectAsStateWithLifecycle()
    val xpProgress by vm.xpProgress.collectAsStateWithLifecycle()
    val xpForNextLevel by vm.xpForNextLevel.collectAsStateWithLifecycle()
    val heatmapData by vm.heatmapData.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    var editingName by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(name) }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.contentdesc_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier.size(100.dp).clip(CircleShape).background(primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("G", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = primary)
            }
            Spacer(Modifier.height(16.dp))

            if (editingName) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(stringResource(R.string.profile_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.saveName(nameInput); editingName = false },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.profile_save)) }
            } else {
                Text(name.ifBlank { stringResource(R.string.profile_guardian_user) }, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = text)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { editingName = true; nameInput = name }) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.profile_edit_name))
                }
            }

            Spacer(Modifier.height(24.dp))

            LevelXpCard(level = level, xpPoints = xpPoints, xpProgress = xpProgress, xpForNextLevel = xpForNextLevel)

            Spacer(Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = card),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.profile_account_summary), fontWeight = FontWeight.Bold, color = text)
                    Spacer(Modifier.height(12.dp))
                    ProfileRow(Icons.Default.Shield, stringResource(R.string.profile_shield_active), if (shieldActive) stringResource(R.string.profile_yes) else stringResource(R.string.profile_no), if (shieldActive) success else textMuted)
                    ProfileRow(Icons.Default.Block, stringResource(R.string.profile_total_blocks), totalBlocks.toString(), accent)
                    ProfileRow(Icons.Default.Science, stringResource(R.string.profile_trial_mode), if (trialMode) stringResource(R.string.profile_enabled) else stringResource(R.string.profile_disabled), textMuted)
                    ProfileRow(Icons.Default.Lock, stringResource(R.string.profile_pin_protection), if (hasPin) stringResource(R.string.profile_enabled) else stringResource(R.string.profile_disabled), if (hasPin) success else textMuted)
                }
            }

            Spacer(Modifier.height(16.dp))

            HeatmapCard(heatmapData = heatmapData)

            Spacer(Modifier.height(16.dp))

            BadgesCard(badges = badges)

            Spacer(Modifier.height(16.dp))

            XpHistoryCard(heatmapData = heatmapData)

            Spacer(Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = card),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.profile_app_info), fontWeight = FontWeight.Bold, color = text)
                    Spacer(Modifier.height(12.dp))
                    ProfileRow(Icons.Default.Info, stringResource(R.string.profile_version), stringResource(R.string.profile_version_value), textMuted)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LevelXpCard(level: Int, xpPoints: Int, xpProgress: Float, xpForNextLevel: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$level", fontSize = 20.sp, fontWeight = FontWeight.Black, color = accent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.level_title, level), fontWeight = FontWeight.Bold, color = text, fontSize = 16.sp)
                    Text(stringResource(R.string.xp_subtitle, xpPoints, xpForNextLevel), fontSize = 13.sp, color = textMuted)
                }
                Surface(
                    color = accent.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "Lv.$level",
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
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = accent,
                trackColor = cardBorder
            )
        }
    }
}

@Composable
private fun HeatmapCard(heatmapData: Map<Long, Int>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.profile_activity_log), fontWeight = FontWeight.Bold, color = text, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            val cellSize = 10.dp
            val cellSpacing = 2.dp
            val today = Calendar.getInstance()
            val totalDays = 364
            val weeks = totalDays / 7
            val daysOfWeek = listOf(
                stringResource(R.string.profile_heatmap_mon),
                stringResource(R.string.profile_heatmap_tue),
                stringResource(R.string.profile_heatmap_wed),
                stringResource(R.string.profile_heatmap_thu),
                stringResource(R.string.profile_heatmap_fri),
                stringResource(R.string.profile_heatmap_sat),
                stringResource(R.string.profile_heatmap_sun)
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (row in 0 until 7) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(cellSpacing)
                    ) {
                        Text(
                            daysOfWeek.getOrElse(row) { "" },
                            fontSize = 8.sp,
                            color = textMuted,
                            modifier = Modifier.width(12.dp),
                            textAlign = TextAlign.Center
                        )
                        for (col in 0 until weeks) {
                            val dayOffset = totalDays - (col * 7 + (6 - row))
                            if (dayOffset < 0) continue
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = today.timeInMillis
                                add(Calendar.DAY_OF_YEAR, -dayOffset)
                            }
                            val dayKey = cal.timeInMillis / 86400000L
                            val count = heatmapData[dayKey] ?: 0
                            val color = when {
                                count == 0 -> surfaceLight
                                count <= 3 -> success.copy(alpha = 0.3f)
                                count <= 10 -> success.copy(alpha = 0.5f)
                                count <= 25 -> success.copy(alpha = 0.7f)
                                else -> success.copy(alpha = 0.9f)
                            }
                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.profile_less), fontSize = 9.sp, color = textMuted)
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(surfaceLight))
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(success.copy(alpha = 0.3f)))
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(success.copy(alpha = 0.5f)))
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(success.copy(alpha = 0.7f)))
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(success.copy(alpha = 0.9f)))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.profile_more), fontSize = 9.sp, color = textMuted)
            }
        }
    }
}

@Composable
private fun BadgesCard(badges: List<BadgeWithState>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.profile_badges_title), fontWeight = FontWeight.Bold, color = text, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                items(badges, key = { it.badge.id }) { badgeWithState ->
                    BadgeItem(badgeWithState)
                }
            }
        }
    }
}

@Composable
private fun BadgeItem(badgeWithState: BadgeWithState) {
    val alpha = if (badgeWithState.isUnlocked) 1f else 0.3f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                .background(if (badgeWithState.isUnlocked) accent.copy(alpha = 0.15f) else surfaceLight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                badgeWithState.badge.icon,
                fontSize = 22.sp,
                modifier = Modifier.alpha(alpha)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            badgeWithState.badge.name,
            fontSize = 10.sp,
            fontWeight = if (badgeWithState.isUnlocked) FontWeight.Medium else FontWeight.Normal,
            color = if (badgeWithState.isUnlocked) text else textMuted,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun XpHistoryCard(heatmapData: Map<Long, Int>) {
    val context = LocalContext.current
    val recentDays = remember(heatmapData) {
        val today = Calendar.getInstance()
        (0 until 30).map { offset ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = today.timeInMillis
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            val dayKey = cal.timeInMillis / 86400000L
            val blocks = heatmapData[dayKey] ?: 0
            val xp = blocks * 10
            val label = when (offset) {
                0 -> context.getString(R.string.profile_day_today)
                1 -> context.getString(R.string.profile_day_yesterday)
                else -> context.getString(R.string.profile_day_offset, offset)
            }
            XpHistoryEntry(label, xp, blocks)
        }.reversed()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.profile_xp_history), fontWeight = FontWeight.Bold, color = text, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(recentDays, key = { it.label }) { entry ->
                    val maxXp = recentDays.maxOfOrNull { it.xp } ?: 1
                    val barHeight = if (maxXp > 0) (entry.xp.toFloat() / maxXp * 60).coerceAtLeast(4f) else 4f
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(20.dp)
                    ) {
                        Box(
                            Modifier
                                .width(12.dp)
                                .height(barHeight.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (entry.xp > 0) accent else surfaceLight)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "+${entry.xp}",
                            fontSize = 7.sp,
                            color = if (entry.xp > 0) textMuted else surfaceLight,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private data class XpHistoryEntry(val label: String, val xp: Int, val blocks: Int)

@Composable
private fun ProfileRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = textMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
