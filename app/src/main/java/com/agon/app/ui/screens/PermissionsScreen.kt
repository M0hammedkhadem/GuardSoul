package com.agon.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.PermissionChecker
import com.agon.app.data.TOTAL_PERMISSIONS
import com.agon.app.ui.components.rememberResumeKey
import com.agon.app.ui.theme.CyanPrimary
import com.agon.app.ui.theme.SurfaceNavy
import com.agon.app.ui.theme.TextSecondary

private data class PermissionItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val required: Boolean,
    val granted: Boolean,
    val onRequest: () -> Unit,
)

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resumeKey = rememberResumeKey()

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val accessibilityGranted = remember(resumeKey) { PermissionChecker.isAccessibilityEnabled(context) }
    val overlayGranted = remember(resumeKey) { PermissionChecker.canDrawOverlays(context) }
    val batteryGranted = remember(resumeKey) { PermissionChecker.ignoresBatteryOptimizations(context) }
    val notifGranted = remember(resumeKey) { PermissionChecker.notificationsEnabled(context) }

    val grantedCount = listOf(accessibilityGranted, overlayGranted, batteryGranted, notifGranted).count { it }
    val allGranted = grantedCount == TOTAL_PERMISSIONS

    val items = listOf(
        PermissionItem(
            title = "خدمة إمكانية الوصول",
            description = "الأهم — تتيح مراقبة التطبيق المفتوح والكلمات والمواقع وإخراجك من المحتوى المحظور.",
            icon = Icons.Default.Accessibility,
            required = true,
            granted = accessibilityGranted,
            onRequest = { PermissionChecker.openAccessibilitySettings(context) },
        ),
        PermissionItem(
            title = "العرض فوق التطبيقات",
            description = "ضروري لإخفاء الصور المثيرة فوراً وعرض شاشة الحجب فوق أي تطبيق.",
            icon = Icons.Default.Layers,
            required = true,
            granted = overlayGranted,
            onRequest = { PermissionChecker.openOverlaySettings(context) },
        ),
        PermissionItem(
            title = "تجاهل تحسين البطارية",
            description = "يضمن استمرار عمل الحماية في الخلفية دون إيقافها من النظام.",
            icon = Icons.Default.BatteryChargingFull,
            required = false,
            granted = batteryGranted,
            onRequest = { PermissionChecker.requestIgnoreBatteryOptimizations(context) },
        ),
        PermissionItem(
            title = "الإشعارات",
            description = "لتلقي التذكيرات التحفيزية وتنبيهات الحماية لحظة الخطر.",
            icon = Icons.Default.NotificationsActive,
            required = false,
            granted = notifGranted,
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    PermissionChecker.openNotificationSettings(context)
                }
            },
        ),
    )

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "الأذونات والحماية",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            // ---------- Gradient summary card ----------
            Surface(shape = RoundedCornerShape(26.dp), color = Color.Transparent) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF56C2EC), Color(0xFF2E93C4))),
                        )
                        .padding(22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.White.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                "تفعيل الحماية",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "مُنحت $grantedCount من $TOTAL_PERMISSIONS أذونات",
                                fontSize = 15.sp,
                                color = Color.White.copy(alpha = 0.9f),
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(TOTAL_PERMISSIONS) { i ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(9.dp)
                                    .background(
                                        if (i < grantedCount) Color.White else Color.White.copy(alpha = 0.3f),
                                        RoundedCornerShape(50),
                                    ),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------- Status banner ----------
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (allGranted) Color(0xFF11293D) else Color(0xFF3A2B14),
                border = BorderStroke(
                    1.dp,
                    if (allGranted) CyanPrimary.copy(alpha = 0.4f) else Color(0xFFE5B04C).copy(alpha = 0.4f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (allGranted) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = if (allGranted) CyanPrimary else Color(0xFFE5B04C),
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (allGranted) "الحماية مُفعّلة بالكامل! كل الميزات تعمل الآن."
                        else "الحماية غير مكتملة — امنح الأذونات المتبقية أدناه.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (allGranted) CyanPrimary else Color(0xFFE5B04C),
                        lineHeight = 24.sp,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "لكي تعمل ميزات حظر التطبيقات والمقاطع القصيرة والكلمات والمواقع وفلتر الصور المثيرة بالذكاء الصناعي، يحتاج التطبيق الأذونات التالية:",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 24.sp,
            )

            Spacer(Modifier.height(16.dp))

            items.forEach { item ->
                PermissionCard(item)
                Spacer(Modifier.height(14.dp))
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PermissionCard(item: PermissionItem) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = SurfaceNavy,
        border = BorderStroke(
            1.dp,
            if (item.granted) CyanPrimary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
        onClick = { if (!item.granted) item.onRequest() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color(0xFF17415B), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White,
                    )
                    if (item.required) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF46231F),
                        ) {
                            Text(
                                "مطلوب",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE58879),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    item.description,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 21.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (item.granted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "ممنوح",
                    tint = Color(0xFF2E9BD6),
                    modifier = Modifier.size(34.dp),
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CyanPrimary,
                    onClick = item.onRequest,
                ) {
                    Text(
                        "منح",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = Color(0xFF06222F),
                    )
                }
            }
        }
    }
}
