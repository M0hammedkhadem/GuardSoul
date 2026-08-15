package com.agon.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.foundation.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.ListCategory
import com.agon.app.ui.theme.CyanPrimary
import com.agon.app.ui.theme.GreenAccent
import com.agon.app.ui.theme.GreenPill
import com.agon.app.ui.theme.SurfaceNavy
import com.agon.app.ui.theme.SurfaceNavy2
import com.agon.app.ui.theme.TextSecondary
import com.agon.app.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BlackAccent = Color(0xFFEF7460)
private val BlackPill = Color(0xFF46231F)

private const val SHIELD_LOCK_MSG = "🛡️ الدرع مفعل — يمكن تعزيز الحماية فقط ولا يمكن إضعافها"

private data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(vm: MainViewModel, snackbarHostState: SnackbarHostState) {
    var isBlack by remember { mutableStateOf(true) }
    var category by remember { mutableStateOf(ListCategory.WORDS) }
    var input by remember { mutableStateOf("") }
    var showAppsSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val accent = if (isBlack) BlackAccent else GreenAccent
    val list = vm.listFor(isBlack, category)

    fun addValue(value: String) {
        if (value.isBlank()) return
        if (vm.addToList(isBlack, category, value)) {
            scope.launch { snackbarHostState.showSnackbar("تمت الإضافة ✓") }
        } else {
            scope.launch { snackbarHostState.showSnackbar(SHIELD_LOCK_MSG) }
        }
    }

    // Single LazyColumn: the whole screen scrolls as one unit (no nested
    // scroll containers fighting over drag events).
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "القوائم",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(50),
                color = SurfaceNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Row(modifier = Modifier.padding(6.dp)) {
                    SegPill(
                        label = "القائمة البيضاء",
                        icon = Icons.Default.CheckCircle,
                        selected = !isBlack,
                        selectedBg = GreenPill,
                        selectedFg = GreenAccent,
                        modifier = Modifier.weight(1f),
                        onClick = { isBlack = false },
                    )
                    SegPill(
                        label = "القائمة السوداء",
                        icon = Icons.Default.Block,
                        selected = isBlack,
                        selectedBg = BlackPill,
                        selectedFg = BlackAccent,
                        modifier = Modifier.weight(1f),
                        onClick = { isBlack = true },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CategoryCard(
                    label = "الكلمات",
                    icon = Icons.Default.TextFields,
                    count = vm.listFor(isBlack, ListCategory.WORDS).size,
                    selected = category == ListCategory.WORDS,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                    onClick = { category = ListCategory.WORDS },
                )
                CategoryCard(
                    label = "المواقع",
                    icon = Icons.Default.Public,
                    count = vm.listFor(isBlack, ListCategory.SITES).size,
                    selected = category == ListCategory.SITES,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                    onClick = { category = ListCategory.SITES },
                )
                CategoryCard(
                    label = "التطبيقات",
                    icon = Icons.Default.Apps,
                    count = vm.listFor(isBlack, ListCategory.APPS).size,
                    selected = category == ListCategory.APPS,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                    onClick = { category = ListCategory.APPS },
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isBlack) BlackPill else Color(0xFF16382E),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    bannerText(isBlack, category),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 26.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                when (category) {
                                    ListCategory.WORDS -> "كلمة مفتاحية"
                                    ListCategory.SITES -> "example.com"
                                    ListCategory.APPS -> "اسم الحزمة أو التطبيق"
                                },
                                color = TextSecondary,
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (category == ListCategory.SITES) KeyboardType.Uri else KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            addValue(input); input = ""
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                    Spacer(Modifier.width(12.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = accent,
                        onClick = { addValue(input); input = "" },
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "إضافة",
                            tint = Color(0xFF0B1B26),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                // Quick-pick from the phone's installed apps.
                if (category == ListCategory.APPS) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = SurfaceNavy,
                        border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.5f)),
                        onClick = { showAppsSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "اختر من تطبيقات هاتفك",
                                color = CyanPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (list.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.GppGood,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(72.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "القائمة فارغة — أضف أول عنصر من الأعلى",
                        color = TextSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp,
                    )
                }
            }
        } else {
            items(list, key = { "$isBlack-$category-$it" }) { entry ->
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    ListItemCard(
                        entry = entry,
                        category = category,
                        accent = accent,
                        onDelete = {
                            if (vm.removeFromList(isBlack, category, entry)) {
                                scope.launch { snackbarHostState.showSnackbar("تم الحذف") }
                            } else {
                                scope.launch { snackbarHostState.showSnackbar(SHIELD_LOCK_MSG) }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showAppsSheet) {
        InstalledAppsSheet(
            existing = list,
            onDismiss = { showAppsSheet = false },
            onPick = { app ->
                addValue(app.packageName)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstalledAppsSheet(
    existing: List<String>,
    onDismiss: () -> Unit,
    onPick: (InstalledApp) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<InstalledApp?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
                .asSequence()
                .map { info ->
                    InstalledApp(
                        label = info.loadLabel(pm).toString(),
                        packageName = info.activityInfo.packageName,
                        icon = runCatching {
                            info.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                        }.getOrNull(),
                    )
                }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
                .toList()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "اختر تطبيقًا من هاتفك",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ابحث عن تطبيق...", color = TextSecondary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
            Spacer(Modifier.height(12.dp))

            val current = apps
            if (current == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = CyanPrimary)
                }
            } else {
                val filtered = current.filter {
                    query.isBlank() ||
                        it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val added = existing.contains(app.packageName)
                        val isSelected = selected?.packageName == app.packageName
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) CyanPrimary.copy(alpha = 0.12f) else SurfaceNavy,
                            border = when {
                                isSelected -> BorderStroke(1.5.dp, CyanPrimary)
                                added -> BorderStroke(1.dp, GreenAccent.copy(alpha = 0.5f))
                                else -> null
                            },
                            onClick = {
                                if (!added) selected = if (isSelected) null else app
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .background(SurfaceNavy2, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            app.label.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = CyanPrimary,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    app.label,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    color = Color.White,
                                )
                                when {
                                    added -> Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "مضاف",
                                        tint = GreenAccent,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    isSelected -> Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "محدد",
                                        tint = CyanPrimary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // "إضافة" button — appears once an app is selected, so the
                // user confirms calmly instead of the tap adding instantly.
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        selected?.let { onPick(it) }
                        selected = null
                    },
                    enabled = selected != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = Color(0xFF06222F),
                        disabledContainerColor = SurfaceNavy2,
                        disabledContentColor = TextSecondary,
                    ),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        selected?.let { "إضافة «${it.label}»" } ?: "اختر تطبيقًا للإضافة",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private fun bannerText(isBlack: Boolean, category: ListCategory): String = if (isBlack) {
    when (category) {
        ListCategory.WORDS -> "إذا ظهرت أي كلمة من هذه في صفحة أو بحث، سيتم إخراجك فوراً."
        ListCategory.SITES -> "هذه المواقع محظورة نهائياً — سيتم إغلاقها فور فتحها."
        ListCategory.APPS -> "هذه التطبيقات محظورة بالكامل أثناء تفعيل الدرع."
    }
} else {
    "عناصر القائمة البيضاء مسموح بها دائماً وتُستثنى من الحظر."
}

@Composable
private fun SegPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    selectedBg: Color,
    selectedFg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) selectedBg else Color.Transparent,
        onClick = onClick,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) selectedFg else TextSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = if (selected) selectedFg else TextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 15.sp,
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
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (selected) accent.copy(alpha = 0.10f) else SurfaceNavy,
        border = if (selected) BorderStroke(1.5.dp, accent) else null,
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) accent else TextSecondary,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                label,
                color = if (selected) accent else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(if (selected) accent else SurfaceNavy2, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$count",
                    color = if (selected) Color(0xFF0B1B26) else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun ListItemCard(
    entry: String,
    category: ListCategory,
    accent: Color,
    onDelete: () -> Unit,
) {
    // For app entries (stored as package names), resolve the human-readable
    // label + icon from PackageManager; fall back to the raw string.
    val context = LocalContext.current
    val appInfo: Pair<String, ImageBitmap?>? =
        if (category == ListCategory.APPS) {
            remember(entry) {
                runCatching {
                    val pm = context.packageManager
                    val ai = pm.getApplicationInfo(entry, 0)
                    Pair(
                        pm.getApplicationLabel(ai).toString(),
                        runCatching { ai.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                    )
                }.getOrNull()
            }
        } else null

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceNavy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Accent edge bar (start side = right in RTL).
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                appInfo?.first ?: entry,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 20.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White,
            )
            if (appInfo?.second != null) {
                Image(
                    bitmap = appInfo.second!!,
                    contentDescription = null,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(SurfaceNavy2, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        when (category) {
                            ListCategory.WORDS -> Icons.Default.TextFields
                            ListCategory.SITES -> Icons.Default.Public
                            ListCategory.APPS -> Icons.Default.Apps
                        },
                        contentDescription = null,
                        tint = Color(0xFF4FB8E7),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "حذف",
                    tint = BlackAccent,
                )
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}
