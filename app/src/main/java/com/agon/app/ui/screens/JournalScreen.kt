package com.agon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.JournalEntry
import com.agon.app.data.moods
import com.agon.app.data.triggerOptions
import com.agon.app.ui.components.ScreenHeader
import com.agon.app.ui.theme.CyanPrimary
import com.agon.app.ui.theme.GreenAccent
import com.agon.app.ui.theme.SurfaceNavy
import com.agon.app.ui.theme.SurfaceNavy2
import com.agon.app.ui.theme.TextSecondary
import com.agon.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("d MMMM yyyy • HH:mm", Locale("ar"))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JournalScreen(vm: MainViewModel, snackbarHostState: SnackbarHostState) {
    var showSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSheet = true },
                containerColor = CyanPrimary,
                contentColor = Color(0xFF06222F),
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة تدوينة")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ScreenHeader(title = "المفكرة") {
                Text(
                    "${vm.journal.size} تدوينة",
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
            }

            if (vm.journal.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "مفكرتك فارغة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "دوّن مشاعرك ومحفزاتك يومياً — الوعي بالمحفزات أول خطوة للتحرر منها",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(vm.journal, key = { it.id }) { entry ->
                        JournalCard(entry = entry, onDelete = {
                            vm.deleteJournalEntry(entry.id)
                            scope.launch { snackbarHostState.showSnackbar("تم حذف التدوينة") }
                        })
                    }
                }
            }
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var selectedMood by remember { mutableIntStateOf(2) }
        val selectedTriggers = remember { mutableStateListOf<String>() }
        var text by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    "كيف تشعر الآن؟",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    moods.forEachIndexed { i, (emoji, label) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { selectedMood = i },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(
                                        if (selectedMood == i) CyanPrimary.copy(alpha = 0.25f) else SurfaceNavy2,
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(emoji, fontSize = 26.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                label,
                                fontSize = 11.sp,
                                color = if (selectedMood == i) CyanPrimary else TextSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("ما المحفزات اليوم؟", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    triggerOptions.forEach { trigger ->
                        val selected = selectedTriggers.contains(trigger)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (selected) selectedTriggers.remove(trigger) else selectedTriggers.add(trigger)
                            },
                            label = { Text(trigger, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanPrimary.copy(alpha = 0.22f),
                                selectedLabelColor = CyanPrimary,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = { Text("اكتب ما يدور في خاطرك...", color = TextSecondary) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        vm.addJournalEntry(selectedMood, selectedTriggers.toList(), text)
                        showSheet = false
                        scope.launch { snackbarHostState.showSnackbar("تم حفظ التدوينة ✓") }
                    },
                    enabled = text.isNotBlank() || selectedTriggers.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = Color(0xFF06222F),
                    ),
                ) {
                    Text("حفظ التدوينة", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JournalCard(entry: JournalEntry, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceNavy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(SurfaceNavy2, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(moods.getOrElse(entry.mood) { moods[2] }.first, fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        moods.getOrElse(entry.mood) { moods[2] }.second,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                    Text(
                        dateFormat.format(Date(entry.timestamp)),
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = TextSecondary)
                }
            }
            if (entry.triggers.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    entry.triggers.forEach { t ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = GreenAccent.copy(alpha = 0.14f),
                        ) {
                            Text(
                                t,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                color = GreenAccent,
                            )
                        }
                    }
                }
            }
            if (entry.text.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(entry.text, fontSize = 14.sp, lineHeight = 22.sp)
            }
        }
    }
}
