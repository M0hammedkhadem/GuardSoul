package com.agon.app.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.R
import com.agon.app.data.local.entity.ScheduleRuleEntity
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.ScheduleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    vm: ScheduleViewModel,
    onBack: () -> Unit
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.schedule_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.contentdesc_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }, containerColor = primary) {
                Icon(Icons.Default.Add, stringResource(R.string.schedule_fab_description))
            }
        }
    ) { padding ->
        if (rules.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Schedule, null, tint = textMuted, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.schedule_empty_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = text)
                    Text(stringResource(R.string.schedule_empty_desc), fontSize = 14.sp, color = textSecondary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    ScheduleRuleCard(
                        rule = rule,
                        onToggle = { vm.toggleRule(rule.id, !rule.enabled) },
                        onDelete = { vm.deleteRule(rule) }
                    )
                }
            }
        }

        if (showAddSheet) {
            ModalBottomSheet(onDismissRequest = { showAddSheet = false }, containerColor = surface) {
                AddScheduleSheet(onAdd = { days, startH, startM, endH, endM ->
                    vm.addRule(days, startH, startM, endH, endM)
                    showAddSheet = false
                })
            }
        }
    }
}

@Composable
private fun ScheduleRuleCard(rule: ScheduleRuleEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    val dayNames = listOf(
        stringResource(R.string.day_mon), stringResource(R.string.day_tue), stringResource(R.string.day_wed),
        stringResource(R.string.day_thu), stringResource(R.string.day_fri), stringResource(R.string.day_sat), stringResource(R.string.day_sun)
    )
    val days = rule.daysOfWeek.split(",").filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
    val selectedDays = days.map { dayNames.getOrElse(it - 1) { "?" } }

    Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, cardBorder)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (rule.enabled) primary.copy(alpha = 0.15f) else surfaceLight), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Schedule, null, tint = if (rule.enabled) primary else textMuted, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${String.format("%02d", rule.startHour)}:${String.format("%02d", rule.startMinute)} — ${String.format("%02d", rule.endHour)}:${String.format("%02d", rule.endMinute)}", fontWeight = FontWeight.Bold, color = text)
                if (selectedDays.isNotEmpty()) Text(selectedDays.joinToString(", "), fontSize = 12.sp, color = textSecondary)
            }
            Switch(checked = rule.enabled, onCheckedChange = { onToggle() }, colors = SwitchDefaults.colors(checkedTrackColor = primary, checkedThumbColor = surface))
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.schedule_delete), tint = shieldRed) }
        }
    }
}

@Composable
private fun AddScheduleSheet(onAdd: (String, Int, Int, Int, Int) -> Unit) {
    var startHour by remember { mutableIntStateOf(22) }
    var startMinute by remember { mutableIntStateOf(0) }
    var endHour by remember { mutableIntStateOf(8) }
    var endMinute by remember { mutableIntStateOf(0) }
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    val dayNames = listOf(
        stringResource(R.string.day_mon), stringResource(R.string.day_tue), stringResource(R.string.day_wed),
        stringResource(R.string.day_thu), stringResource(R.string.day_fri), stringResource(R.string.day_sat), stringResource(R.string.day_sun)
    )

    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(stringResource(R.string.schedule_add_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = text)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.schedule_days), fontWeight = FontWeight.Medium, color = textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            dayNames.forEachIndexed { i, name ->
                val selected = i + 1 in selectedDays
                FilterChip(selected = selected, onClick = { selectedDays = if (selected) selectedDays - (i + 1) else selectedDays + (i + 1) }, label = { Text(name, fontSize = 12.sp) })
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.schedule_start), fontWeight = FontWeight.Medium, color = textSecondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimePickerField(startHour, 0, 23) { startHour = it }
                    Text(":", color = text)
                    TimePickerField(startMinute, 0, 59) { startMinute = it }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.schedule_end), fontWeight = FontWeight.Medium, color = textSecondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimePickerField(endHour, 0, 23) { endHour = it }
                    Text(":", color = text)
                    TimePickerField(endMinute, 0, 59) { endMinute = it }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onAdd(selectedDays.joinToString(","), startHour, startMinute, endHour, endMinute) },
            enabled = selectedDays.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        ) { Text(stringResource(R.string.schedule_btn_add)) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TimePickerField(value: Int, rangeStart: Int, rangeEnd: Int, onChanged: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(String.format("%02d", value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() }.take(2)
            text = filtered
            val num = filtered.toIntOrNull()
            if (num != null && num in rangeStart..rangeEnd) onChanged(num)
        },
        modifier = Modifier.width(56.dp), singleLine = true, shape = RoundedCornerShape(8.dp),
        textStyle = LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 18.sp)
    )
}
