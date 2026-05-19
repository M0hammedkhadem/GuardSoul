package com.agon.app.ui.screens

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.agon.app.data.DailyTimeLimit
import com.agon.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeLimitsScreen(
    limits: List<DailyTimeLimit>,
    onAddLimit: (DailyTimeLimit) -> Unit,
    onRemoveLimit: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedPackage by remember { mutableStateOf("") }
    var selectedLabel by remember { mutableStateOf("") }
    var selectedMinutes by remember { mutableIntStateOf(30) }
    var searchQuery by remember { mutableStateOf("") }

    val installedApps = remember {
        context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != context.packageName }
            .map { ai ->
                val label = context.packageManager.getApplicationLabel(ai).toString()
                ai.packageName to label
            }
            .sortedBy { it.second }
    }

    val hasUsageAccess = remember {
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text("Daily Time Limits", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (!hasUsageAccess) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = warning.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, warning.copy(alpha = 0.3f))
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = warning)
                        Spacer(Modifier.width(8.dp))
                        Text("Usage access required to track app time", fontSize = 13.sp, color = text, modifier = Modifier.weight(1f))
                        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                            Text("Grant", color = warning)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("App Limits", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = text)
                FilledTonalButton(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(12.dp)
                ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add") }
            }
            Spacer(Modifier.height(12.dp))

            if (limits.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Timer, null, tint = textMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No limits set", color = textMuted)
                        Text("Add time limits for apps", fontSize = 12.sp, color = textMuted)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(limits, key = { it.packageName }) { limit ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = card),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(limit.appLabel.ifBlank { limit.packageName.substringAfterLast('.') }, fontWeight = FontWeight.Medium, color = text)
                                    Text("${limit.dailyMinutes} min/day", fontSize = 13.sp, color = primary)
                                }
                                IconButton(onClick = { onRemoveLimit(limit.packageName) }) {
                                    Icon(Icons.Default.RemoveCircle, "Remove", tint = shieldRed)
                                }
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
                        Text("Add Time Limit", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = text)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search app") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.heightIn(max = 200.dp)) {
                            val filtered = if (searchQuery.isBlank()) installedApps else installedApps.filter { it.second.contains(searchQuery, ignoreCase = true) || it.first.contains(searchQuery, ignoreCase = true) }
                            items(filtered) { (pkg, label) ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        selectedPackage = pkg; selectedLabel = label; searchQuery = ""
                                    }.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(label, fontSize = 14.sp, color = text, modifier = Modifier.weight(1f))
                                    Text(pkg.substringAfterLast('.'), fontSize = 11.sp, color = textMuted)
                                }
                            }
                        }
                        if (selectedPackage.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Minutes per day:", color = textSecondary, fontSize = 13.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { selectedMinutes = maxOf(5, selectedMinutes - 5) }) { Icon(Icons.Default.Remove, null) }
                                Text("$selectedMinutes min", fontWeight = FontWeight.Bold, color = text, modifier = Modifier.width(80.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                IconButton(onClick = { selectedMinutes = minOf(480, selectedMinutes + 5) }) { Icon(Icons.Default.Add, null) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (selectedPackage.isNotBlank()) {
                                        onAddLimit(DailyTimeLimit(packageName = selectedPackage, appLabel = selectedLabel, dailyMinutes = selectedMinutes))
                                        selectedPackage = ""; selectedLabel = ""; selectedMinutes = 30; showAddDialog = false
                                    }
                                },
                                enabled = selectedPackage.isNotBlank(),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Add") }
                        }
                    }
                }
            }
        }
    }
}
