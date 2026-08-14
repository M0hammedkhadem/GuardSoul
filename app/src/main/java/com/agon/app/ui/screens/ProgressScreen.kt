package com.agon.app.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.dayLabel
import com.agon.app.ui.components.AppCard
import com.agon.app.ui.components.ProgressRing
import com.agon.app.ui.components.ScreenHeader
import com.agon.app.ui.components.StatCard
import com.agon.app.ui.theme.AmberAccent
import com.agon.app.ui.theme.CyanPrimary
import com.agon.app.ui.theme.GreenAccent
import com.agon.app.ui.theme.SurfaceNavy2
import com.agon.app.ui.theme.TextSecondary
import com.agon.app.viewmodel.MainViewModel

private data class Milestone(val days: Int, val title: String, val desc: String)

private val milestoneList = listOf(
    Milestone(1, "الخطوة الأولى", "يوم كامل من النقاء"),
    Milestone(3, "كسر العادة", "3 أيام متواصلة"),
    Milestone(7, "أسبوع الصمود", "أسبوع كامل بلا انتكاسة"),
    Milestone(14, "الإرادة الفولاذية", "أسبوعان من التحكم"),
    Milestone(30, "شهر التحول", "30 يوماً — عادة جديدة تتشكل"),
    Milestone(60, "العقل الصافي", "شهران من الوضوح"),
    Milestone(90, "إعادة الضبط", "90 يوماً — دماغ متجدد"),
    Milestone(180, "نصف عام", "6 أشهر من الحرية"),
    Milestone(365, "عام النقاء", "سنة كاملة — إنجاز أسطوري"),
)

private fun levelName(days: Long): String = when {
    days >= 90 -> "أسطورة"
    days >= 30 -> "قائد"
    days >= 14 -> "منتصر"
    days >= 7 -> "صامد"
    days >= 3 -> "مجاهد"
    days >= 1 -> "مبتدئ"
    else -> "بداية الطريق"
}

@Composable
fun ProgressScreen(vm: MainViewModel) {
    val now = System.currentTimeMillis()
    val totalSeconds = ((now - vm.streakStart) / 1000).coerceAtLeast(0)
    val days = totalSeconds / 86400
    val longestDays = maxOf(vm.longestSeconds, totalSeconds) / 86400
    val controlHours = vm.controlSeconds(now) / 3600

    val nextMilestone = milestoneList.firstOrNull { it.days > days }
    val levelProgress = if (nextMilestone != null) {
        totalSeconds.toFloat() / (nextMilestone.days * 86400f)
    } else 1f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "التقدم")

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            // ---------- Level card ----------
            AppCard(innerPadding = 22.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(
                        progress = levelProgress.coerceIn(0f, 1f),
                        size = 110.dp,
                        strokeWidth = 11.dp,
                        brush = Brush.sweepGradient(listOf(AmberAccent, GreenAccent, AmberAccent)),
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = AmberAccent,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text("رتبتك الحالية", color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            levelName(days),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = AmberAccent,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (nextMilestone != null)
                                "الإنجاز القادم: ${nextMilestone.title} (${nextMilestone.days} ${dayLabel(nextMilestone.days)})"
                            else "وصلت لأعلى رتبة! 🏆",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------- Stats grid ----------
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("$days ${dayLabel(days)}", "أيام النقاء", CyanPrimary, Modifier.weight(1f))
                StatCard("$longestDays ${dayLabel(longestDays)}", "أطول سلسلة", GreenAccent, Modifier.weight(1f))
                StatCard("${vm.relapses}", "الانتكاسات", AmberAccent, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("${vm.urgesResisted}", "رغبات قاومتها", GreenAccent, Modifier.weight(1f))
                StatCard("$controlHours", "ساعات التحكم", CyanPrimary, Modifier.weight(1f))
                StatCard("${vm.journal.size}", "تدوينات", AmberAccent, Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "الإنجازات",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))

            milestoneList.forEach { m ->
                val achieved = days >= m.days || (vm.longestSeconds / 86400) >= m.days
                val progress = (totalSeconds.toFloat() / (m.days * 86400f)).coerceIn(0f, 1f)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(
                                    if (achieved) GreenAccent.copy(alpha = 0.18f) else SurfaceNavy2,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (achieved) Icons.Default.CheckCircle else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (achieved) GreenAccent else TextSecondary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                m.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (achieved) GreenAccent else Color.White,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(m.desc, fontSize = 13.sp, color = TextSecondary)
                            if (!achieved) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    color = CyanPrimary,
                                    trackColor = SurfaceNavy2,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${m.days}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = if (achieved) GreenAccent else TextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}
