package com.agon.app.ui.screens

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.BlocklistItem
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.ListsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(vm: ListsViewModel) {
    val selectedList by vm.selectedListType.collectAsStateWithLifecycle()
    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val keywordsCount by vm.keywordsCount.collectAsStateWithLifecycle()
    val websitesCount by vm.websitesCount.collectAsStateWithLifecycle()
    val appsCount by vm.appsCount.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }
    var regexEnabled by remember { mutableStateOf(false) }
    var sensitivityLevel by remember { mutableStateOf("medium") }
    var urlInputError by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_lists_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background, titleContentColor = text)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ListTypeButton(stringResource(R.string.btn_blacklist), selected = selectedList == "blacklist", danger) { vm.setListType("blacklist") }
                ListTypeButton(stringResource(R.string.btn_whitelist), selected = selectedList == "whitelist", success) { vm.setListType("whitelist") }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryTab(stringResource(R.string.tab_keywords), selectedCategory == "keywords", keywordsCount) { vm.setCategory("keywords") }
                CategoryTab(stringResource(R.string.tab_websites), selectedCategory == "websites", websitesCount) { vm.setCategory("websites") }
                CategoryTab(stringResource(R.string.tab_apps), selectedCategory == "apps", appsCount) { vm.setCategory("apps") }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.setSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.placeholder_search)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = textMuted, modifier = Modifier.size(20.dp)) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    { IconButton(onClick = { vm.setSearchQuery("") }) { Icon(Icons.Default.Clear, stringResource(R.string.contentdesc_clear), tint = textMuted) } }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = cardBorder
                )
            )

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        if (selectedCategory == "websites") urlInputError = false
                    },
                    placeholder = { Text(stringResource(R.string.lists_add_new, selectedCategory)) },
                    singleLine = true,
                    isError = urlInputError,
                    supportingText = if (urlInputError) {{ Text(stringResource(R.string.error_invalid_url), color = shieldRed) }} else null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = cardBorder
                    )
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (selectedCategory == "apps") {
                            showAppPicker = true
                        } else if (inputText.isNotBlank()) {
                            val trimmed = inputText.trim().lowercase()
                            if (selectedCategory == "websites" && !isValidUrl(trimmed)) {
                                urlInputError = true
                            } else {
                                urlInputError = false
                                vm.addItem(trimmed, regexEnabled = regexEnabled, sensitivityLevel = sensitivityLevel)
                                inputText = ""
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = primary)
                ) { Icon(Icons.Default.Add, stringResource(R.string.contentdesc_add), tint = surface) }
            }

            if (selectedCategory == "keywords") {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = regexEnabled,
                            onCheckedChange = { regexEnabled = it },
                            colors = CheckboxDefaults.colors(checkedColor = primary, checkmarkColor = surface)
                        )
                        Text(stringResource(R.string.label_regex), fontSize = 13.sp, color = text)
                    }
                    SensitivitySelector(selected = sensitivityLevel) { sensitivityLevel = it }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        @Suppress("DEPRECATION") Icon(Icons.Default.ListAlt, null, tint = textMuted, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.empty_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = text)
                        Text(stringResource(R.string.empty_subtitle), fontSize = 14.sp, color = textSecondary)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { item ->
                        val accentColor = when (selectedList) { "blacklist" -> danger; else -> success }
                        Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(3.dp).height(32.dp).background(accentColor, RoundedCornerShape(2.dp)))
                                Spacer(Modifier.width(12.dp))
                                if (item.category == "apps") {
                                    AppIconView(item.value, modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(
                                        when (item.category) {
                                            "keywords" -> Icons.Default.TextFields
                                            else -> Icons.Default.Language
                                        },
                                        null, tint = textMuted, modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.displayLabel, color = text, fontSize = 14.sp, fontWeight = if (item.label != null) FontWeight.Medium else FontWeight.Normal)
                                    if (item.label != null) {
                                        Text(item.value, fontSize = 11.sp, color = textMuted)
                                    }
                                    if (item.category == "keywords" && item.regexEnabled) {
                                        Text(stringResource(R.string.label_regex_on), fontSize = 10.sp, color = accent)
                                    }
                                }
                                if (item.category == "websites" && item.urlCategory != null) {
                                    Surface(color = cardBorder, shape = RoundedCornerShape(4.dp)) {
                                        Text(item.urlCategory!!, fontSize = 10.sp, color = textMuted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                IconButton(onClick = { vm.removeItem(item.id) }) {
                                    Icon(Icons.Default.RemoveCircle, stringResource(R.string.contentdesc_remove), tint = shieldRed, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onSelect = { pkg, label ->
                vm.addItem(BlocklistItem(listType = selectedList, category = "apps", value = pkg, label = label))
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun AppIconView(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val painter = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = when (drawable) {
                is BitmapDrawable -> drawable.bitmap.asImageBitmap()
                else -> {
                    val bmp = Bitmap.createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp.asImageBitmap()
                }
            }
            BitmapPainter(bitmap) as Painter
        } catch (_: Exception) { null }
    }
    if (painter != null) {
        Icon(painter, null, modifier = modifier)
    } else {
        Icon(Icons.Default.Apps, null, tint = textMuted, modifier = modifier)
    }
}

@Composable
private fun AppPickerDialog(onDismiss: () -> Unit, onSelect: (String, String) -> Unit) {
    val context = LocalContext.current
    val installedApps = remember {
        context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != context.packageName }
            .map { ai ->
                val label = try { context.packageManager.getApplicationLabel(ai).toString() } catch (_: Exception) { ai.packageName }
                ai.packageName to label
            }
            .sortedBy { it.second }
    }
    var search by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_select_app_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text(stringResource(R.string.placeholder_search)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                val filtered = if (search.isBlank()) installedApps else installedApps.filter { it.second.contains(search, true) || it.first.contains(search, true) }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(filtered) { (pkg, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(pkg, label) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconView(pkg, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontSize = 14.sp, color = text, modifier = Modifier.weight(1f))
                            Text(pkg.substringAfterLast('.'), fontSize = 11.sp, color = textMuted)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun SensitivitySelector(selected: String, onChange: (String) -> Unit) {
    val options = listOf("low" to stringResource(R.string.sensitivity_low), "medium" to stringResource(R.string.sensitivity_medium), "high" to stringResource(R.string.sensitivity_high))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.label_sensitivity), fontSize = 13.sp, color = textMuted)
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onChange(value) },
                label = { Text(label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = primary.copy(alpha = 0.15f),
                    selectedLabelColor = primary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = cardBorder,
                    selectedBorderColor = primary.copy(alpha = 0.5f),
                    enabled = true,
                    selected = selected == value
                )
            )
        }
    }
}

@Composable
private fun ListTypeButton(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) color else surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) color else cardBorder)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (selected) surface else text, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
    }
}

@Composable
private fun CategoryTab(label: String, selected: Boolean, count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) primary.copy(alpha = 0.15f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (selected) primary else cardBorder)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) primary else textMuted)
            if (count > 0) {
                Spacer(Modifier.width(4.dp))
                Surface(color = if (selected) primary else textMuted, shape = CircleShape) {
                    Text("$count", fontSize = 10.sp, color = surface, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}

private fun isValidUrl(url: String): Boolean {
    return url.matches(Regex("^([a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}(/.*)?$"))
        || url.matches(Regex("^https?://([a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}(/.*)?$"))
}
