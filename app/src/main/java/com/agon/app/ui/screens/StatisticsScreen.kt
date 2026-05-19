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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.BlockEvent
import com.agon.app.ui.theme.*

@Composable
fun StatisticsScreen(
    blocksCount: Int,
    shieldActivatedAt: Long?,
    blockEvents: List<BlockEvent>,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    val daysActive = if (shieldActivatedAt != null) {
        ((System.currentTimeMillis() - shieldActivatedAt) / (86400000)).toInt()
    } else 0

    val todayBlocks = blockEvents.count {
        java.util.Calendar.getInstance().apply { timeInMillis = it.timestamp }.let { cal ->
            val today = java.util.Calendar.getInstance()
            cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR) &&
            cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)
        }
    }

    val blocksByApp = blockEvents
        .groupBy { it.packageName }
        .mapValues { it.value.size }
        .entries
        .sortedByDescending { it.value }
        .take(5)

    val mostBlockedApp = blocksByApp.firstOrNull()

    Scaffold(
        containerColor = background,
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Usage Statistics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onReset) {
                        Icon(Icons.Default.Delete, "Reset")
                    }
                },
                colors = @OptIn(ExperimentalMaterial3Api::class)
                TopAppBarDefaults.topAppBarColors(containerColor = background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary cards
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CalendarToday,
                        value = daysActive.toString(),
                        label = "Days",
                        subLabel = "active",
                        color = primary
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Shield,
                        value = blocksCount.toString(),
                        label = "Total",
                        subLabel = "blocks",
                        color = accent
                    )
                }
            }

            item {
                StatCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Today,
                    value = todayBlocks.toString(),
                    label = "Today's",
                    subLabel = "blocks",
                    color = shieldGreen
                )
            }

            // Most blocked app badge
            if (mostBlockedApp != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = card),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                    .background(warning.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Block, null, tint = warning, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Most Blocked App", fontSize = 12.sp, color = textSecondary)
                                Text(
                                    mostBlockedApp.key.substringAfterLast('.'),
                                    fontWeight = FontWeight.Bold, color = text
                                )
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = warning.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "${mostBlockedApp.value}×",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = warning, fontWeight = FontWeight.Bold, fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Block history
            item {
                Text(
                    "Recent Block Events",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = text,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (blockEvents.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text("No block events recorded yet", color = textMuted)
                    }
                }
            } else {
                items(blockEvents.takeLast(50).reversed()) { event ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = card),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (event.blockType) {
                                    "ai_scan" -> Icons.Default.VisibilityOff
                                    "porn" -> Icons.Default.Block
                                    "time_limit" -> Icons.Default.Timer
                                    else -> Icons.Default.Block
                                },
                                null,
                                tint = shieldRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    event.packageName.substringAfterLast('.'),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = text
                                )
                                Text(
                                    java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date(event.timestamp)),
                                    fontSize = 11.sp,
                                    color = textMuted
                                )
                            }
                            Text(
                                when (event.blockType) {
                                    "ai_scan" -> "AI"
                                    "porn" -> "DNS"
                                    "time_limit" -> "Time"
                                    else -> "Manual"
                                },
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
