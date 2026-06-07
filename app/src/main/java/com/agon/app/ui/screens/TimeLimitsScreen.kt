package com.agon.app.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.TimeLimitsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeLimitsScreen(
    vm: TimeLimitsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val limits by vm.appLimits.collectAsStateWithLifecycle()
    val usage by vm.appUsage.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedPackage by remember { mutableStateOf("") }
    var selectedLabel by remember { mutableStateOf("") }
    var selectedMinutes by remember { mutableIntStateOf(30) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.refreshUsage() }

    val installedApps = remember {
        context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != context.packageName }
            .map { ai ->
                val label = context.packageManager.getApplicationLabel(ai).toString()
                ai.packageName to label
            }
            .sortedBy { it.second }
    }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timelimits_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.contentdesc_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.timelimits_app_limits), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = text)
                FilledTonalButton(onClick = { showAddDialog = true }, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.timelimits_add))
                }
            }
            Spacer(Modifier.height(12.dp))

            if (limits.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Timer, null, tint = textMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.timelimits_empty_title), color = textMuted)
                        Text(stringResource(R.string.timelimits_empty_desc), fontSize = 12.sp, color = textMuted)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(limits, key = { it.packageName }) { limit ->
                        val usageMs = usage.find { it.packageName == limit.packageName }?.totalTimeInForeground ?: 0L
                        val usedMinutes = (usageMs / 60000).toInt()
                        val progress = (usedMinutes.toFloat() / limit.dailyMinutes).coerceIn(0f, 1f)

                        Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(limit.appLabel.ifBlank { limit.packageName.substringAfterLast('.') }, fontWeight = FontWeight.Medium, color = text)
                                        Text(stringResource(R.string.timelimits_usage_format, usedMinutes, limit.dailyMinutes), fontSize = 13.sp, color = if (usedMinutes >= limit.dailyMinutes) danger else primary)
                                    }
                                    IconButton(onClick = { vm.removeLimit(limit) }) {
                                        Icon(Icons.Default.RemoveCircle, stringResource(R.string.timelimits_remove), tint = shieldRed)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = if (usedMinutes >= limit.dailyMinutes) danger else primary,
                                    trackColor = surfaceLight
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            Dialog(onDismissRequest = { showAddDialog = false }) {
                Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.timelimits_dialog_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = text)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text(stringResource(R.string.timelimits_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.heightIn(max = 200.dp)) {
                            val filtered = if (searchQuery.isBlank()) installedApps else installedApps.filter { it.second.contains(searchQuery, ignoreCase = true) || it.first.contains(searchQuery, ignoreCase = true) }
                            items(filtered, key = { it.first }) { (pkg, label) ->
                                Row(Modifier.fillMaxWidth().clickable { selectedPackage = pkg; selectedLabel = label; searchQuery = "" }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, fontSize = 14.sp, color = text, modifier = Modifier.weight(1f))
                                    Text(pkg.substringAfterLast('.'), fontSize = 11.sp, color = textMuted)
                                }
                            }
                        }
                        if (selectedPackage.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.timelimits_minutes), color = textSecondary, fontSize = 13.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { selectedMinutes = maxOf(5, selectedMinutes - 5) }) { Icon(Icons.Default.Remove, null) }
                                Text(stringResource(R.string.timelimits_selected_minutes, selectedMinutes), fontWeight = FontWeight.Bold, color = text, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
                                IconButton(onClick = { selectedMinutes = minOf(480, selectedMinutes + 5) }) { Icon(Icons.Default.Add, null) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (selectedPackage.isNotBlank()) {
                                        vm.addLimit(selectedPackage, selectedLabel, selectedMinutes)
                                        selectedPackage = ""; selectedLabel = ""; selectedMinutes = 30; showAddDialog = false
                                    }
                                },
                                enabled = selectedPackage.isNotBlank(), shape = RoundedCornerShape(12.dp)
                            ) { Text(stringResource(R.string.timelimits_btn_add)) }
                        }
                    }
                }
            }
        }
    }
}
