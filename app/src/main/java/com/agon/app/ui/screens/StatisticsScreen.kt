package com.agon.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.StatisticsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    vm: StatisticsViewModel,
    onBack: () -> Unit
) {
    val totalBlocks by vm.totalBlocks.collectAsStateWithLifecycle()
    val blocksToday by vm.blocksToday.collectAsStateWithLifecycle()
    val streak by vm.streakCount.collectAsStateWithLifecycle()
    val mostBlocked by vm.mostBlockedApp.collectAsStateWithLifecycle()
    val recentEvents by vm.recentEvents.collectAsStateWithLifecycle()
    val daysActive = 0

    LaunchedEffect(Unit) { vm.refreshStreak() }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.contentdesc_back)) } },
                actions = {
                    IconButton(onClick = { vm.resetStatistics() }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.contentdesc_reset), tint = textMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(stringResource(R.string.statistics_total), totalBlocks.toString(), Icons.Default.Block, danger, Modifier.weight(1f))
                    StatCard(stringResource(R.string.statistics_today), blocksToday.toString(), Icons.Default.Today, accent, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(stringResource(R.string.statistics_streak), "$streak ${stringResource(R.string.statistics_days)}", Icons.Default.LocalFireDepartment, warning, Modifier.weight(1f))
                    StatCard(stringResource(R.string.statistics_active), "$daysActive ${stringResource(R.string.statistics_days)}", Icons.Default.CalendarMonth, success, Modifier.weight(1f))
                }
            }

            if (mostBlocked != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = card),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dangerous, null, tint = danger, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.statistics_most_blocked), fontSize = 12.sp, color = textMuted)
                                Text(mostBlocked!!.appLabel.ifBlank { mostBlocked!!.packageName }, fontWeight = FontWeight.Bold, color = text)
                                Text("${mostBlocked!!.count} ${stringResource(R.string.statistics_blocks)}", fontSize = 12.sp, color = primary)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = card),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.statistics_today_breakdown), fontWeight = FontWeight.Bold, color = text)
                        Spacer(Modifier.height(8.dp))
                        val recentToday = recentEvents.filter { it.timestamp >= getTodayStart() }
                        if (recentToday.isEmpty()) {
                            Text(stringResource(R.string.statistics_empty), fontSize = 13.sp, color = textMuted)
                        } else {
                            recentToday.groupBy { it.appLabel.ifBlank { it.packageName } }.forEach { (app, events) ->
                                val pct = events.size.toFloat() / maxOf(1, recentToday.size)
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(app, fontSize = 13.sp, color = text, modifier = Modifier.weight(1f))
                                        Text("${events.size}", fontSize = 13.sp, color = primary, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { pct },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = primary, trackColor = surfaceLight
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = card),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.statistics_recent), fontWeight = FontWeight.Bold, color = text)
                        Spacer(Modifier.height(8.dp))
                        if (recentEvents.isEmpty()) {
                            Text(stringResource(R.string.statistics_empty), fontSize = 13.sp, color = textMuted)
                        } else {
                            recentEvents.take(20).forEach { event ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        when (event.blockType) { "ai" -> Icons.Default.VisibilityOff; "time" -> Icons.Default.Timer; else -> Icons.Default.Block },
                                        null, tint = textMuted, modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(event.appLabel.ifBlank { event.packageName.substringAfterLast('.') }, color = text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Text(formatTime(event.timestamp), color = textMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, cardBorder), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = text)
            Text(label, fontSize = 12.sp, color = textMuted)
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun getTodayStart(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
