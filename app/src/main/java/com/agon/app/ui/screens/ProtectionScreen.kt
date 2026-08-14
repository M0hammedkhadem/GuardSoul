package com.agon.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.TOTAL_PROTECTIONS
import com.agon.app.data.blockableApps
import com.agon.app.ui.components.AppCard
import com.agon.app.ui.components.AppSwitch
import com.agon.app.ui.components.ProgressRing
import com.agon.app.ui.components.ScreenHeader
import com.agon.app.ui.theme.CyanDeep
import com.agon.app.ui.theme.CyanPrimary
import com.agon.app.ui.theme.GreenAccent
import com.agon.app.ui.theme.GreenDark
import com.agon.app.ui.theme.TextSecondary
import com.agon.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun ProtectionScreen(
    vm: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenSafeSearch: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val count = vm.protectionCount()
    val pct = (count * 100 / TOTAL_PROTECTIONS)
    val lockMsg = "🛡️ الدرع مفعل — يمكن تعزيز الحماية فقط ولا يمكن إضعافها"
    fun locked() = scope.launch { snackbarHostState.showSnackbar(lockMsg) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "درع الحماية")

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            // ---------- Shield lock notice ----------
            if (vm.shieldActive) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF11293D),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "الدرع مفعل: يمكنك تعزيز الحماية فقط — الإضعاف مقفل 🔒",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CyanPrimary,
                            lineHeight = 22.sp,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ---------- SafeSearch entry ----------
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = GreenDark,
                onClick = onOpenSafeSearch,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(GreenAccent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.ManageSearch,
                            contentDescription = null,
                            tint = Color(0xFF0B2E22),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "إعدادات البحث الآمن",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "SafeSearch لخمسة محركات + مرشحات المحتوى",
                            fontSize = 13.sp,
                            color = Color(0xFFA8CBBE),
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = Color(0xFFA8CBBE),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------- Overall score ----------
            AppCard(innerPadding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(
                        progress = count.toFloat() / TOTAL_PROTECTIONS,
                        size = 112.dp,
                        strokeWidth = 12.dp,
                        brush = Brush.sweepGradient(listOf(CyanDeep, CyanPrimary, CyanDeep)),
                    ) {
                        Text(
                            "$pct%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = CyanPrimary,
                        )
                    }
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(
                            "درجة الحماية الإجمالية",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "$count من $TOTAL_PROTECTIONS حماية مفعلة",
                            color = TextSecondary,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "⚡ ${vm.blocksCount} عملية حظر نفّذها العقل",
                            color = GreenAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------- Quick actions ----------
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        vm.blockAll()
                        scope.launch { snackbarHostState.showSnackbar("تم تفعيل كل الحمايات 🛡️") }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("حظر الكل", fontSize = 13.sp, color = Color.White) }
                OutlinedButton(
                    onClick = {
                        if (vm.blockShortsOnly()) {
                            scope.launch { snackbarHostState.showSnackbar("تم حظر المقاطع القصيرة فقط") }
                        } else locked()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("حظر المقاطع", fontSize = 13.sp, color = Color.White) }
                OutlinedButton(
                    onClick = {
                        if (vm.unblockAll()) {
                            scope.launch { snackbarHostState.showSnackbar("تم فتح الكل — انتبه لنفسك ⚠️") }
                        } else locked()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("فتح الكل", fontSize = 13.sp, color = Color.White) }
            }

            Spacer(Modifier.height(16.dp))

            // ---------- App cards ----------
            blockableApps.forEach { app ->
                val state = vm.appBlocks[app.id] ?: com.agon.app.data.AppBlockState()
                AppCard(innerPadding = 18.dp, modifier = Modifier.animateContentSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(CyanDeep, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Apps,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(app.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    ToggleRow(
                        icon = { Icon(Icons.Default.Block, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp)) },
                        label = "حظر التطبيق كاملاً",
                        checked = state.fullBlock,
                        onChange = { if (!vm.updateAppFull(app.id, it)) locked() },
                    )
                    if (app.hasShorts) {
                        Spacer(Modifier.height(6.dp))
                        ToggleRow(
                            icon = { Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp)) },
                            label = "حظر المقاطع القصيرة فقط",
                            checked = state.shortsBlock,
                            onChange = { if (!vm.updateAppShorts(app.id, it)) locked() },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    icon: @Composable () -> Unit,
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        AppSwitch(checked = checked, onCheckedChange = onChange)
    }
}
