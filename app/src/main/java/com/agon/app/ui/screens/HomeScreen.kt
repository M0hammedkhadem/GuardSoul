package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.PermissionChecker
import com.agon.app.data.TOTAL_PERMISSIONS
import com.agon.app.data.dayLabel
import com.agon.app.data.delayOptions
import com.agon.app.data.milestoneName
import com.agon.app.data.quotes
import com.agon.app.ui.components.AppCard
import com.agon.app.ui.components.ProgressRing
import com.agon.app.ui.components.ScreenHeader
import com.agon.app.ui.components.rememberResumeKey
import com.agon.app.ui.theme.CyanPrimary
import com.agon.app.ui.theme.DangerContainer
import com.agon.app.ui.theme.DangerRed
import com.agon.app.ui.theme.GreenAccent
import com.agon.app.ui.theme.SurfaceNavy
import com.agon.app.ui.theme.SurfaceNavy2
import com.agon.app.ui.theme.TextSecondary
import com.agon.app.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val milestones = listOf(1, 3, 7, 14, 30, 60, 90, 180, 365)

@Composable
fun HomeScreen(
    vm: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            vm.completePendingStopIfDue(now)
            delay(1000)
        }
    }

    val context = LocalContext.current
    val resumeKey = rememberResumeKey()
    val permissionsGranted = remember(resumeKey) { PermissionChecker.grantedCount(context) }
    val accessibilityOn = remember(resumeKey) { PermissionChecker.isAccessibilityEnabled(context) }
    val showPermissionBanner = permissionsGranted < TOTAL_PERMISSIONS

    val scope = rememberCoroutineScope()
    var showRelapseDialog by remember { mutableStateOf(false) }

    val totalSeconds = ((now - vm.streakStart) / 1000).coerceAtLeast(0)
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    val nextGoal = milestones.firstOrNull { it > days } ?: (days + 30).toInt()
    val remainingDays = nextGoal - days
    val ringProgress = (totalSeconds.toFloat() / (nextGoal * 86400f)).coerceIn(0f, 1f)

    val controlHours = vm.controlSeconds(now) / 3600
    val longestDays = maxOf(vm.longestSeconds, totalSeconds) / 86400

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "طريق النقاء") {
            IconButton(onClick = {
                scope.launch { snackbarHostState.showSnackbar("افتح تبويب القوائم لإدارة المواقع") }
            }) {
                Icon(Icons.Default.ManageSearch, contentDescription = null, tint = CyanPrimary)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "الإعدادات", tint = CyanPrimary)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            // ---------- Permissions warning banner ----------
            AnimatedVisibility(
                visible = showPermissionBanner,
                enter = fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF2B1D1E),
                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.35f)),
                        onClick = onOpenPermissions,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFEE8877),
                                modifier = Modifier.size(30.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (accessibilityOn) "الحماية غير مكتملة" else "الحماية غير مفعلة",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFFEE8877),
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    if (accessibilityOn) "امنح بقية الأذونات لتعمل كل الميزات"
                                    else "فعّل خدمة إمكانية الوصول لتعمل الحماية",
                                    fontSize = 13.sp,
                                    color = Color(0xFFD9C4C0),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "تفعيل",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = CyanPrimary,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // ---------- Main shield card ----------
            AppCard(innerPadding = 22.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val shieldBg by animateColorAsState(
                        if (vm.shieldActive) GreenAccent.copy(alpha = 0.15f) else SurfaceNavy2,
                        label = "shieldBg",
                    )
                    val shieldTint by animateColorAsState(
                        if (vm.shieldActive) GreenAccent else CyanPrimary,
                        label = "shieldTint",
                    )
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(shieldBg, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (vm.shieldActive) Icons.Default.GppGood else Icons.Default.GppMaybe,
                            contentDescription = null,
                            tint = shieldTint,
                            modifier = Modifier.size(52.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "الدرع الرئيسي",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (vm.shieldActive) "الحماية مفعلة — أنت في وضع الأمان الآن"
                        else "الحماية متوقفة — فعّل الدرع لتشغيل حمايتك",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        onClick = {
                            if (!vm.cycleDelay()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("🛡️ الدرع مفعل — يمكن زيادة مدة التأخير فقط ولا يمكن تقليلها")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "مدة التأخير عند الإيقاف: ${delayOptions[vm.delayIndex]}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))

                    // Scheduled-stop countdown banner (anti-impulse delay running).
                    if (vm.shieldActive && vm.pendingStopAt > 0L) {
                        val remainMs = (vm.pendingStopAt - now).coerceAtLeast(0)
                        val rh = remainMs / 3_600_000
                        val rm = (remainMs % 3_600_000) / 60_000
                        val rs = (remainMs % 60_000) / 1000
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF3A2B14),
                            border = BorderStroke(1.dp, Color(0xFFE5B04C).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "⏳ سيتوقف الدرع بعد " +
                                        "%02d:%02d:%02d".format(rh, rm, rs),
                                    color = Color(0xFFE5B04C),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "هذه فترة التأمل — لحظة الضعف تمر، وقرارك الصحيح يبقى",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    val pendingStop = vm.shieldActive && vm.pendingStopAt > 0L
                    Button(
                        onClick = {
                            if (pendingStop) {
                                vm.cancelPendingStop()
                                scope.launch { snackbarHostState.showSnackbar("أحسنت! تم إلغاء إيقاف الدرع 💪") }
                            } else {
                                val wasActive = vm.shieldActive
                                vm.toggleShield()
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        when {
                                            !wasActive -> "تم تفعيل الدرع 🛡️"
                                            vm.pendingStopAt > 0L -> "تمت جدولة الإيقاف — الدرع ما زال يحميك حتى انتهاء المدة"
                                            else -> "تم إيقاف الدرع"
                                        },
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                pendingStop -> GreenAccent
                                vm.shieldActive -> DangerContainer
                                else -> CyanPrimary
                            },
                            contentColor = when {
                                pendingStop -> Color(0xFF06231B)
                                vm.shieldActive -> DangerRed
                                else -> Color(0xFF06222F)
                            },
                        ),
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                pendingStop -> "إلغاء الإيقاف — أكمل الطريق"
                                vm.shieldActive -> "إيقاف الدرع"
                                else -> "تفعيل الدرع"
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------- Streak counter ----------
            AppCard(innerPadding = 22.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProgressRing(
                        progress = ringProgress,
                        size = 230.dp,
                        strokeWidth = 14.dp,
                        brush = Brush.sweepGradient(listOf(CyanPrimary, GreenAccent, CyanPrimary)),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$days",
                                fontSize = 62.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanPrimary,
                            )
                            Text("${dayLabel(days)} من النقاء", color = TextSecondary, fontSize = 15.sp)
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TimeBox(hours.toString().padStart(2, '0'), "ساعة", Modifier.weight(1f))
                        TimeBox(minutes.toString().padStart(2, '0'), "دقيقة", Modifier.weight(1f))
                        TimeBox(seconds.toString().padStart(2, '0'), "ثانية", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "الهدف القادم: ${milestoneName(nextGoal)} ($remainingDays ${dayLabel(remainingDays)} متبقِ)",
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------- Stats row ----------
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconStatCard(
                    icon = Icons.Default.EmojiEvents,
                    value = "$longestDays",
                    label = "أطول سلسلة",
                    modifier = Modifier.weight(1f),
                )
                IconStatCard(
                    icon = Icons.Default.LocalFireDepartment,
                    value = "${vm.relapses}",
                    label = "الانتكاسات",
                    modifier = Modifier.weight(1f),
                )
                IconStatCard(
                    icon = Icons.Default.SelfImprovement,
                    value = "$controlHours",
                    label = "ساعة تحكم",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))

            // ---------- View progress button ----------
            OutlinedButton(
                onClick = onOpenProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "عرض التقدم والإنجازات",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            Spacer(Modifier.height(16.dp))

            // ---------- Daily message card ----------
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SurfaceNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.nextQuote() },
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(CyanPrimary, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFF0B2430),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "رسالة اليوم",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Crossfade(targetState = vm.quoteIndex, label = "quote") { idx ->
                        val q = quotes[idx % quotes.size]
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "”${q.text}“",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 30.sp,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "— ${q.source}",
                                color = CyanPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------- Relapse button ----------
            Button(
                onClick = { showRelapseDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7A3B2E),
                    contentColor = Color(0xFFF3D5CD),
                ),
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("تسجيل انتكاسة وإعادة العدّاد", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "التعثر جزء من الطريق. صدق مع نفسك، وابدأ من جديد بقوة.",
                modifier = Modifier.fillMaxWidth(),
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showRelapseDialog) {
        AlertDialog(
            onDismissRequest = { showRelapseDialog = false },
            title = { Text("تسجيل انتكاسة؟", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "الانتكاسة ليست نهاية الطريق، بل درس جديد. سيتم تصفير العداد وحفظ أطول سلسلة وصلت لها. هل أنت متأكد؟",
                    lineHeight = 24.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.registerRelapse()
                    showRelapseDialog = false
                    scope.launch {
                        snackbarHostState.showSnackbar("لا بأس.. البداية من جديد أقوى 💪")
                    }
                }) { Text("نعم، سجّل", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { showRelapseDialog = false }) { Text("تراجع") }
            },
        )
    }
}

@Composable
private fun IconStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = SurfaceNavy, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 13.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun TimeBox(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = SurfaceNavy2, modifier = modifier) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = CyanPrimary)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 13.sp, color = TextSecondary)
        }
    }
}
