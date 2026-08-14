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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.ui.components.AppSwitch
import com.agon.app.ui.theme.CyanPrimary
import com.agon.app.ui.theme.SurfaceNavy
import com.agon.app.ui.theme.TextSecondary
import com.agon.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun AdvancedScreen(vm: MainViewModel, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    fun locked() = scope.launch {
        snackbarHostState.showSnackbar("🛡️ الدرع مفعل — يمكن تعزيز الحماية فقط ولا يمكن إضعافها")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ---------- Centered title ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "الإعدادات المتقدمة",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            // ---------- Gradient hero card (centered) ----------
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF56C2EC), Color(0xFF2076B8))),
                        )
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .background(Color.White.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "الإعدادات المتقدمة",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "أدوات حماية قوية للحالات المتقدمة",
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            // ---------- AI blocking section ----------
            SectionTitle(icon = Icons.Default.AutoAwesome, title = "الحظر بالذكاء الصناعي")
            Spacer(Modifier.height(12.dp))

            AdvancedCard(
                iconBg = Color(0xFF2E9BD6),
                icon = Icons.Default.HideImage,
                title = "فلتر الصور المثيرة",
                titleBadge = Icons.Default.AutoAwesome,
                description = "نموذج ذكاء صناعي يعمل محلياً على جهازك — لا تخرج أي صورة من هاتفك",
                checked = vm.aiImageFilter,
                onChange = {
                    if (vm.updateAiImageFilter(it)) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (it) "تم تفعيل فلتر الصور بالذكاء الصناعي ✨" else "تم إيقاف فلتر الصور",
                            )
                        }
                    } else locked()
                },
            )

            Spacer(Modifier.height(26.dp))

            // ---------- App protection section ----------
            SectionTitle(icon = Icons.Default.WorkOutline, title = "حماية التطبيق")
            Spacer(Modifier.height(12.dp))

            AdvancedCard(
                iconBg = Color(0xFF4A2626),
                icon = Icons.Default.RemoveShoppingCart,
                iconTint = Color(0xFFE58879),
                title = "منع إلغاء التثبيت",
                description = "يمنع إزالة التطبيق أو إيقاف الخدمة أو مسح البيانات أثناء تفعيل الدرع",
                checked = vm.uninstallGuard,
                onChange = {
                    if (vm.updateUninstallGuard(it)) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (it) "تم تفعيل منع إلغاء التثبيت 🔒" else "تم إيقاف منع إلغاء التثبيت",
                            )
                        }
                    } else locked()
                },
            )

            Spacer(Modifier.height(30.dp))

            Text(
                "🔒 كل ميزات هذه الصفحة تعمل بالكامل داخل جهازك — لا تُجمع أو تُرسل أي بيانات خارجياً.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun AdvancedCard(
    iconBg: Color,
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    iconTint: Color = Color.White,
    titleBadge: ImageVector? = null,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = SurfaceNavy,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(iconBg, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                    if (titleBadge != null) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            titleBadge,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    description,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 21.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            AppSwitch(checked = checked, onCheckedChange = onChange)
        }
    }
}
