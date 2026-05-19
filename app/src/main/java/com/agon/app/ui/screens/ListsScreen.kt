package com.agon.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.GuardianViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(viewModel: GuardianViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var selectedList by remember { mutableStateOf("blacklist") }
    var selectedCategory by remember { mutableStateOf("keywords") }
    
    var inputText by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }

    val currentItems = when ("${selectedList}_$selectedCategory") {
        "blacklist_keywords" -> state.blacklistKeywords
        "blacklist_websites" -> state.blacklistWebsites
        "blacklist_apps" -> state.blacklistApps
        "whitelist_keywords" -> state.whitelistKeywords
        "whitelist_websites" -> state.whitelistWebsites
        "whitelist_apps" -> state.whitelistApps
        else -> emptyList()
    }

    val listColor = if (selectedList == "blacklist") danger else success

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_lists_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    titleContentColor = text
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // List Type Toggle
            Surface(
                color = card,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    ListTypeButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.btn_blacklist),
                        icon = Icons.Default.Block,
                        isSelected = selectedList == "blacklist",
                        color = danger,
                        onClick = { selectedList = "blacklist" }
                    )
                    ListTypeButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.btn_whitelist),
                        icon = Icons.Default.CheckCircle,
                        isSelected = selectedList == "whitelist",
                        color = success,
                        onClick = { selectedList = "whitelist" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryTab(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.tab_keywords),
                    icon = Icons.Default.TextFields,
                    isSelected = selectedCategory == "keywords",
                    count = if (selectedList == "blacklist") state.blacklistKeywords.size else state.whitelistKeywords.size,
                    color = listColor,
                    onClick = { selectedCategory = "keywords" }
                )
                CategoryTab(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.tab_websites),
                    icon = Icons.Default.Language,
                    isSelected = selectedCategory == "websites",
                    count = if (selectedList == "blacklist") state.blacklistWebsites.size else state.whitelistWebsites.size,
                    color = listColor,
                    onClick = { selectedCategory = "websites" }
                )
                CategoryTab(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.tab_apps),
                    icon = Icons.Default.Apps,
                    isSelected = selectedCategory == "apps",
                    count = if (selectedList == "blacklist") state.blacklistApps.size else state.whitelistApps.size,
                    color = listColor,
                    onClick = { selectedCategory = "apps" }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Description Box
            Surface(
                color = listColor.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, listColor.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                val desc = when ("${selectedList}_$selectedCategory") {
                    "blacklist_keywords" -> stringResource(R.string.desc_blacklist_keywords)
                    "blacklist_websites" -> stringResource(R.string.desc_blacklist_websites)
                    "blacklist_apps" -> stringResource(R.string.desc_blacklist_apps)
                    "whitelist_keywords" -> stringResource(R.string.desc_whitelist_keywords)
                    "whitelist_websites" -> stringResource(R.string.desc_whitelist_websites)
                    "whitelist_apps" -> stringResource(R.string.desc_whitelist_apps)
                    else -> ""
                }
                Text(
                    text = desc,
                    color = listColor,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Input Row
            if (selectedCategory == "apps") {
                Button(
                    onClick = { showAppPicker = true },
                    colors = ButtonDefaults.buttonColors(containerColor = listColor.copy(alpha = 0.1f), contentColor = listColor),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add App to $selectedList", fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Add new ${selectedCategory.dropLast(1)}...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = listColor,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = text,
                            unfocusedTextColor = text
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.addToList(selectedList, selectedCategory, inputText)
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(listColor)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.contentdesc_add), tint = surface)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // List Items
            if (currentItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.List, contentDescription = null, tint = textMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.empty_title), color = textSecondary, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.empty_subtitle), color = textMuted, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentItems) { item ->
                        ListItemCard(
                            text = item,
                            color = listColor,
                            icon = when (selectedCategory) {
                                "keywords" -> Icons.Default.TextFields
                                "websites" -> Icons.Default.Language
                                else -> Icons.Default.Apps
                            },
                            onRemove = { viewModel.removeFromList(selectedList, selectedCategory, item) }
                        )
                    }
                }
            }
        }

        if (showAppPicker) {
            AppPickerDialog(
                currentItems = currentItems,
                onDismiss = { showAppPicker = false },
                onAdd = { 
                    viewModel.addToList(selectedList, selectedCategory, it)
                    showAppPicker = false
                }
            )
        }
    }
}

@Composable
fun ListTypeButton(
    modifier: Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) color else textMuted, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = if (isSelected) color else textMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun CategoryTab(
    modifier: Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    count: Int,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (isSelected) color.copy(alpha = 0.1f) else card,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isSelected) color.copy(alpha = 0.5f) else cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) color else textMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = title, color = if (isSelected) text else textMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = if (isSelected) color else cardBorder,
                shape = RoundedCornerShape(99.dp)
            ) {
                Text(
                    text = count.toString(),
                    color = if (isSelected) surface else textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ListItemCard(
    text: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(56.dp)
                    .background(color)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = textMuted, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = getAppName(text), color = com.agon.app.ui.theme.text, fontSize = 15.sp)
                    if (text.contains(".")) {
                        Text(text = text, color = textMuted, fontSize = 10.sp)
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.contentdesc_remove), tint = danger)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerDialog(currentItems: List<String>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    // Fetch all installed apps dynamically
    val allInstalledApps = remember {
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.icon != 0 && packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .map { it.packageName to it.loadLabel(packageManager).toString() }
            .sortedBy { it.second.lowercase() }
    }
    
    val availableApps = allInstalledApps.filter { !currentItems.contains(it.first) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = availableApps.filter { it.second.contains(searchQuery, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surface,
        title = { Text(stringResource(R.string.dialog_select_app_title), color = text) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.placeholder_search)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary,
                        unfocusedBorderColor = cardBorder,
                        focusedTextColor = text
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps) { (pkg, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAdd(pkg) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(name.first().toString(), color = primary, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(name, color = text, fontSize = 16.sp)
                                    Text(pkg, color = textMuted, fontSize = 10.sp)
                                }
                            }
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.contentdesc_add), tint = primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = textSecondary)
            }
        }
    )
}

@Composable
fun getAppName(packageName: String): String {
    if (!packageName.contains(".")) return packageName
    val context = LocalContext.current
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        packageName.split(".").last().replaceFirstChar { it.uppercase() }
    }
}
