package com.agon.app.ui.screens

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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.delayOptions
import com.agon.app.ui.components.AppCard
import com.agon.app.ui.components.AppSwitch
import com.agon.app.ui.theme.CyanPrimary
import com.agon.app.ui.theme.DangerContainer
import com.agon.app.ui.theme.DangerRed
import com.agon.app.ui.theme.GreenAccent
import com.agon.app.ui.theme.TextSecondary
import com.agon.app.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    var showResetDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = CyanPrimary)
            }
            Text(
                "الإعدادات",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary,
            )
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            Text("عام", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            AppCard(innerPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("التذكير اليومي", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("رسالة تحفيزية كل صباح", fontSize = 13.sp, color = TextSecondary)
                    }
                    AppSwitch(checked = vm.dailyReminder, onCheckedChange = { vm.updateDailyReminder(it) })
                }
            }

            Spacer(Modifier.height(12.dp))

            AppCard(innerPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("زر المواصلة للكلمات المحظورة", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(
                            "يظهر في شاشة الحظر خيار المتابعة رغم التحذير",
                            fontSize = 13.sp,
                            color = TextSecondary,
                        )
                    }
                    AppSwitch(checked = vm.keywordContinue, onCheckedChange = {
                        if (!vm.updateKeywordContinue(it)) {
                            scope.launch {
                                snackbarHostState.showSnackbar("🛡️ الدرع مفعل — لا يمكن تفعيل خيار يضعف الحماية")
                            }
                        }
                    })
                }
            }

            Spacer(Modifier.height(12.dp))

            AppCard(innerPadding = 16.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("مدة التأخير عند إيقاف الدرع", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(
                            "يمنع القرارات الاندفاعية لحظة الضعف",
                            fontSize = 13.sp,
                            color = TextSecondary,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CyanPrimary.copy(alpha = 0.16f),
                        onClick = {
                            if (!vm.cycleDelay()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("🛡️ الدرع مفعل — يمكن زيادة مدة التأخير فقط ولا يمكن تقليلها")
                                }
                            }
                        },
                    ) {
                        Text(
                            delayOptions[vm.delayIndex],
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = CyanPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("البيانات", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            Button(
                onClick = { showResetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DangerContainer,
                    contentColor = DangerRed,
                ),
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("إعادة تعيين كل البيانات", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(24.dp))

            Text("حول التطبيق", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            AppCard(innerPadding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("طريق النقاء", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("الإصدار 1.0.0", fontSize = 13.sp, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "رفيقك في رحلة التحرر من إدمان وسائل التواصل والمحتوى الإباحي. تذكّر: كل يوم تصمد فيه هو انتصار.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    lineHeight = 22.sp,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("إعادة تعيين البيانات؟", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "سيتم حذف كل الإحصائيات والتدوينات والإعدادات نهائياً. لا يمكن التراجع عن هذا الإجراء.",
                    lineHeight = 24.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    if (!vm.resetAllData()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("🛡️ الدرع مفعل — أوقف الدرع أولاً قبل إعادة التعيين")
                        }
                    }
                }) { Text("نعم، احذف الكل", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("إلغاء") }
            },
        )
    }
}
