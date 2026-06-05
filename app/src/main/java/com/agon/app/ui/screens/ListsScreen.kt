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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.BlocklistItem
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.ListsViewModel

@Composable
fun ListsScreen(vm: ListsViewModel) {
    val selectedList by vm.selectedListType.collectAsStateWithLifecycle()
    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val keywordsCount by vm.keywordsCount.collectAsStateWithLifecycle()
    val websitesCount by vm.websitesCount.collectAsStateWithLifecycle()
    val appsCount by vm.appsCount.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }
    var urlInputError by remember { mutableStateOf(false) }

    val accentColor = if (selectedList == "blacklist") danger else success

    Scaffold(
        containerColor = background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header: Title "Lists" aligned to the end (right)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = stringResource(R.string.lists_title),
                    color = text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Segmented List Type Toggle
            Surface(
                color = Color(0xFF151D2E),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    ListToggleButton(
                        label = stringResource(R.string.lists_whitelist),
                        icon = Icons.Default.CheckCircle,
                        selected = selectedList == "whitelist",
                        activeColor = success,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setListType("whitelist") }
                    )
                    ListToggleButton(
                        label = stringResource(R.string.lists_blacklist),
                        icon = Icons.Default.Block,
                        selected = selectedList == "blacklist",
                        activeColor = danger,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setListType("blacklist") }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Category Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CategoryCard(
                    label = stringResource(R.string.lists_category_apps),
                    icon = Icons.Default.GridView,
                    count = appsCount,
                    selected = selectedCategory == "apps",
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.setCategory("apps") }
                )
                CategoryCard(
                    label = stringResource(R.string.lists_category_websites),
                    icon = Icons.Default.Language,
                    count = websitesCount,
                    selected = selectedCategory == "websites",
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.setCategory("websites") }
                )
                CategoryCard(
                    label = stringResource(R.string.lists_category_keywords),
                    icon = Icons.Default.TextFields,
                    count = keywordsCount,
                    selected = selectedCategory == "keywords",
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.setCategory("keywords") }
                )
            }

            Spacer(Modifier.height(24.dp))

            // Category Description Box
            val descriptionRes = when (selectedList) {
                "blacklist" -> when (selectedCategory) {
                    "apps" -> R.string.desc_blacklist_apps
                    "websites" -> R.string.desc_blacklist_websites
                    else -> R.string.desc_blacklist_keywords
                }
                else -> when (selectedCategory) {
                    "apps" -> R.string.desc_whitelist_apps
                    "websites" -> R.string.desc_whitelist_websites
                    else -> R.string.desc_whitelist_keywords
                }
            }

            Surface(
                color = if (selectedList == "blacklist") danger.copy(alpha = 0.12f) else success.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(descriptionRes),
                    color = accentColor,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(18.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Action Button
            if (selectedCategory == "apps") {
                Surface(
                    onClick = { showAppPicker = true },
                    color = accentColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (selectedList == "blacklist")
                                stringResource(R.string.lists_add_to_blacklist)
                            else
                                stringResource(R.string.lists_add_to_whitelist),
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Add, null, tint = accentColor, modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                // Input for Websites/Keywords
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = {
                            inputText = it
                            if (selectedCategory == "websites") urlInputError = false
                        },
                        placeholder = { Text(stringResource(R.string.lists_add_new, selectedCategory), color = textMuted) },
                        singleLine = true,
                        isError = urlInputError,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = text,
                            unfocusedTextColor = text
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val trimmed = inputText.trim().lowercase()
                                if (selectedCategory == "websites" && !isValidUrl(trimmed)) {
                                    urlInputError = true
                                } else {
                                    urlInputError = false
                                    vm.addItem(trimmed)
                                    inputText = ""
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = accentColor)
                    ) { Icon(Icons.Default.Add, null, tint = Color.White) }
                }
            }

            Spacer(Modifier.height(32.dp))

            // List or Empty State
            if (items.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = null,
                            tint = Color(0xFF1E293B),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.lists_empty_title),
                            color = text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.lists_empty_desc),
                            color = textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        ListItemCard(item, accentColor) { vm.removeItem(item.id) }
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onSelect = { pkg, label ->
                vm.addItem(BlocklistItem.create(listType = selectedList, category = "apps", value = pkg, label = label))
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun ListToggleButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    activeColor: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) activeColor.copy(alpha = 0.25f) else Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxHeight()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = label,
                color = if (selected) activeColor else textSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) activeColor else textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CategoryCard(
    label: String,
    icon: ImageVector,
    count: Int,
    selected: Boolean,
    accentColor: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF151D2E),
        shape = RoundedCornerShape(14.dp),
        border = if (selected) BorderStroke(1.5.dp, accentColor) else null,
        modifier = modifier.height(115.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else textSecondary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                color = if (selected) Color.White else textSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                color = if (selected) accentColor else Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "$count",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun ListItemCard(item: BlocklistItem, accentColor: Color, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151D2E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.category == "apps") {
                AppIconView(item.value, modifier = Modifier.size(36.dp))
            } else {
                Surface(
                    color = accentColor.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (item.category == "keywords") Icons.Default.TextFields else Icons.Default.Language,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayLabel, color = text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (item.label != null) {
                    Text(item.value, fontSize = 13.sp, color = textSecondary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.RemoveCircle, null, tint = shieldRed, modifier = Modifier.size(24.dp))
            }
        }
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
        Icon(painter, null, modifier = modifier, tint = Color.Unspecified)
    } else {
        Icon(Icons.Default.Apps, null, tint = textSecondary, modifier = modifier)
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
        title = { Text(stringResource(R.string.lists_picker_title), color = text) },
        containerColor = Color(0xFF111827),
        text = {
            Column {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text(stringResource(R.string.lists_picker_search_hint), color = textMuted) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = text, unfocusedTextColor = text)
                )
                Spacer(Modifier.height(12.dp))
                val filtered = if (search.isBlank()) installedApps else installedApps.filter { it.second.contains(search, true) || it.first.contains(search, true) }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(filtered) { (pkg, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(pkg, label) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconView(pkg, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(label, fontSize = 14.sp, color = text, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel), color = primary) } }
    )
}

private fun isValidUrl(url: String): Boolean {
    val candidate = url.trim()
    if (candidate.isEmpty()) return false

    // Strip optional scheme (http/https) before matching the host.
    val host = candidate
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .substringBefore(':')
    if (host.isEmpty()) return false

    // Accept IPv4 addresses and "localhost".
    if (host == "localhost") return true
    if (host.matches(Regex("^(\\d{1,3}\\.){3}\\d{1,3}$"))) {
        return host.split('.').all { it.toIntOrNull() in 0..255 }
    }

    // Accept hostnames that contain at least one dot, and where every label
    // is non-empty. We deliberately use \p{L} so IDN labels (Arabic, etc.)
    // are accepted.
    val labelRegex = Regex("^[\\p{L}\\p{N}]([\\p{L}\\p{N}-]*[\\p{L}\\p{N}])?$")
    val labels = host.split('.')
    if (labels.size < 2) return false
    return labels.all { labelRegex.matches(it) }
}
