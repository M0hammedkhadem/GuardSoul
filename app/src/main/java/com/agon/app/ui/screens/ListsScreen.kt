package com.agon.app.ui.screens

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.ListCategory
import com.agon.app.viewmodel.ListsViewModel

@Composable
fun ListsScreen(vm: ListsViewModel) {
    val context = LocalContext.current
    val isBlacklist by vm.isBlacklist.collectAsStateWithLifecycle()
    val category by vm.selectedCategory.collectAsStateWithLifecycle()
    val apps by vm.blockedApps.collectAsStateWithLifecycle()
    val websites by vm.blockedWebsites.collectAsStateWithLifecycle()
    val keywords by vm.blockedKeywords.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }

    val currentList = when (category) {
        ListCategory.APPS -> apps
        ListCategory.WEBSITES -> websites
        ListCategory.KEYWORDS -> keywords
    }.toList()

    val description = when (category) {
        ListCategory.APPS -> stringResource(if (isBlacklist) R.string.desc_blacklist_apps else R.string.desc_whitelist_apps)
        ListCategory.WEBSITES -> stringResource(if (isBlacklist) R.string.desc_blacklist_websites else R.string.desc_whitelist_websites)
        ListCategory.KEYWORDS -> stringResource(if (isBlacklist) R.string.desc_blacklist_keywords else R.string.desc_whitelist_keywords)
    }

    Box(Modifier.fillMaxSize().background(background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.lists_title),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = com.agon.app.ui.theme.text,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                )
            }

            item {
                ToggleHeader(
                    isBlacklist = isBlacklist,
                    onToggle = vm::setBlacklist
                )
                Spacer(Modifier.height(24.dp))
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CategoryCard(
                        title = stringResource(R.string.tab_apps),
                        count = apps.size,
                        icon = Icons.Default.GridView,
                        selected = category == ListCategory.APPS,
                        onClick = { vm.setCategory(ListCategory.APPS) },
                        modifier = Modifier.weight(1f)
                    )
                    CategoryCard(
                        title = stringResource(R.string.tab_websites),
                        count = websites.size,
                        icon = Icons.Default.Language,
                        selected = category == ListCategory.WEBSITES,
                        onClick = { vm.setCategory(ListCategory.WEBSITES) },
                        modifier = Modifier.weight(1f)
                    )
                    CategoryCard(
                        title = stringResource(R.string.tab_keywords),
                        count = keywords.size,
                        icon = Icons.Default.TextFields,
                        selected = category == ListCategory.KEYWORDS,
                        onClick = { vm.setCategory(ListCategory.KEYWORDS) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                InfoBanner(text = description)
                Spacer(Modifier.height(24.dp))
            }

            item {
                if (category == ListCategory.APPS) {
                    // App picker button for apps category
                    OutlinedButton(
                        onClick = { showAppPicker = true },
                        border = BorderStroke(1.dp, shieldRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = shieldRed)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.lists_add_to_blacklist), color = shieldRed)
                    }
                    Spacer(Modifier.height(16.dp))
                } else {
                    AddRow(
                        value = textInput,
                        onValueChange = { textInput = it },
                        onAdd = { vm.addItem(textInput); textInput = "" }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            items(currentList) { item ->
                ListItem(
                    itemText = item,
                    onRemove = { vm.removeItem(item) }
                )
            }
        }
    }

    // App Picker Bottom Sheet
    if (showAppPicker) {
        val installedApps = remember {
            context.packageManager.getInstalledApplications(0)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { context.packageManager.getApplicationLabel(it).toString() to it.packageName }
                .sortedBy { it.first }
        }
        ModalBottomSheet(onDismissRequest = { showAppPicker = false }, containerColor = surface) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(stringResource(R.string.lists_picker_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = text)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    installedApps.forEach { (label, pkg) ->
                        val isBlocked = apps.contains(pkg)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (isBlocked) vm.removeItem(pkg) else vm.addItem(pkg)
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isBlocked,
                                onCheckedChange = { checked ->
                                    if (checked) vm.addItem(pkg) else vm.removeItem(pkg)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = shieldRed)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(label, color = text, fontSize = 14.sp)
                                Text(pkg, color = textMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { showAppPicker = false }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.btn_done))
                }
            }
        }
    }
}

@Composable
private fun ToggleHeader(isBlacklist: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(card),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(if (!isBlacklist) surfaceLight.copy(alpha = 0.2f) else Color.Transparent)
                .clickable { onToggle(false) },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lists_whitelist), color = if (!isBlacklist) shieldGreen else textMuted, fontWeight = FontWeight.Bold)
                if (!isBlacklist) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.CheckCircle, null, tint = shieldGreen, modifier = Modifier.size(16.dp))
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(if (isBlacklist) shieldRed.copy(alpha = 0.15f) else Color.Transparent)
                .clickable { onToggle(true) },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lists_blacklist), color = if (isBlacklist) shieldRed else textMuted, fontWeight = FontWeight.Bold)
                if (isBlacklist) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Block, null, tint = shieldRed, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(title: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) shieldRed.copy(alpha = 0.5f) else cardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = if (selected) shieldRed else textMuted, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selected) com.agon.app.ui.theme.text else textMuted)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) shieldRed else surfaceLight.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(text = "$count", fontSize = 10.sp, color = if (selected) Color.White else com.agon.app.ui.theme.text, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InfoBanner(text: String) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = shieldRed.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, shieldRed.copy(alpha = 0.1f))
    ) {
        Text(
            text = text,
            color = shieldRed.copy(alpha = 0.8f),
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
    }
}

@Composable
private fun AddRow(value: String, onValueChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(shieldRed.copy(alpha = 0.8f))
                .clickable { onAdd() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, null, tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(stringResource(R.string.placeholder_add_new), color = textMuted, fontSize = 14.sp) },
            modifier = Modifier.weight(1f).height(48.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = card,
                unfocusedContainerColor = card,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = primary
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
    }
}

@Composable
private fun ListItem(itemText: String, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Close,
                null,
                tint = shieldRed,
                modifier = Modifier.size(20.dp).clickable { onRemove() }
            )
            Spacer(Modifier.weight(1f))
            Text(itemText, color = com.agon.app.ui.theme.text, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.TextFields, null, tint = textMuted, modifier = Modifier.size(16.dp))
        }
    }
}
