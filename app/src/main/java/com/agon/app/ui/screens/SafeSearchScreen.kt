package com.agon.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.TOTAL_SAFE_SEARCH
import com.agon.app.data.contentFilterList
import com.agon.app.data.engineDisplayName
import com.agon.app.data.searchEngineNames
import com.agon.app.ui.components.AppSwitch
import com.agon.app.ui.components.ProgressRing
import com.agon.app.ui.theme.CyanPrimary
import com.agon.app.ui.theme.SurfaceNavy
import com.agon.app.ui.theme.TextSecondary
import com.agon.app.viewmodel.MainViewModel

@Composable
fun SafeSearchScreen(
    vm: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val count = vm.safeSearchCount()
    val pct = count * 100 / TOTAL_SAFE_SEARCH
    val scope = rememberCoroutineScope()
    fun locked() = scope.launch {
        snackbarHostState.showSnackbar("🛡️ الدرع مفعل — يمكن تعزيز الحماية فقط ولا يمكن إضعافها")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ---------- Header ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = CyanPrimary)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "تصفية نتائج البحث",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary,
            )
            Spacer(Modifier.width(20.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            // ---------- Gradient hero card with ring ----------
            Surface(shape = RoundedCornerShape(26.dp), color = Color.Transparent) {
                Row(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF62C6EE), Color(0xFF1F7FC0))),
                        )
                        .padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProgressRing(
                        progress = count.toFloat() / TOTAL_SAFE_SEARCH,
                        size = 108.dp,
                        strokeWidth = 12.dp,
                        trackColor = Color.White.copy(alpha = 0.28f),
                        brush = Brush.sweepGradient(listOf(Color.White, Color.White)),
                    ) {
                        Text(
                            "$pct%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(
                            "البحث الآمن",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "يفرض تصفية المحتوى الإباحي والصور والفيديوهات الصريحة من نتائج محركات البحث.",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.92f),
                            lineHeight = 22.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------- Enable full protection button ----------
            Button(
                onClick = { vm.enableFullSafeSearch() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4FB8E7),
                    contentColor = Color(0xFF072A3B),
                ),
            ) {
                Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF0B1B26), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("تفعيل الحماية الكاملة", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))

            // ---------- Search engines section ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Public, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("محركات البحث", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(Modifier.height(14.dp))

            searchEngineNames.forEach { name ->
                val enabled = vm.searchEngines[name] == true
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = SurfaceNavy,
                    border = BorderStroke(
                        1.dp,
                        if (enabled) CyanPrimary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color(0xFF2E9BD6), RoundedCornerShape(15.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                engineDisplayName(name),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                if (enabled) "البحث الآمن مُفعل" else "غير مُفعل",
                                fontSize = 13.sp,
                                color = if (enabled) Color(0xFF4FB8E7) else TextSecondary,
                            )
                        }
                        AppSwitch(checked = enabled, onCheckedChange = { if (!vm.updateEngine(name, it)) locked() })
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(12.dp))

            // ---------- Content filters section ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FilterAlt, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("مرشّحات المحتوى", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SurfaceNavy,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    contentFilterList.forEachIndexed { index, filter ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                filterIcon(filter.key),
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(26.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    filter.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(filter.desc, fontSize = 13.sp, color = TextSecondary)
                            }
                            AppSwitch(
                                checked = vm.contentFilters[filter.key] == true,
                                onCheckedChange = { if (!vm.updateContentFilter(filter.key, it)) locked() },
                            )
                        }
                        if (index != contentFilterList.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 18.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ---------- Info banner ----------
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF3EA9DC),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF0B3550),
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "عند التفعيل، يعمل \"البحث الآمن\" على فرض وضع SafeSearch الصارم في Google وBing وYouTube وغيرها، لتحميك من الظهور المفاجئ للمحتوى المسيء أثناء البحث.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0B2B40),
                        lineHeight = 23.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun filterIcon(key: String): ImageVector = when (key) {
    "images" -> Icons.Default.Image
    "videos" -> Icons.Default.VideoLibrary
    "sites" -> Icons.Default.Block
    else -> Icons.Default.Tag
}
