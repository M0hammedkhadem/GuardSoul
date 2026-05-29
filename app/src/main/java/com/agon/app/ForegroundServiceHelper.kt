package com.agon.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import timber.log.Timber

object ForegroundServiceHelper {

    private const val NOTIFICATION_CHANNEL_ID = "guardian_foreground_service"

    /**
     * إنشاء قناة إشعارات خاصة بالخدمات الخلفية بأقل مستوى أهمية
     * (صامتة، بدون صوت أو اهتزاز، غير قابلة للإخفاء يدوياً)
     */
    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager

            val channel = android.app.NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Guardian Protection Service",
                android.app.NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Silent notification to keep Guardian protection active"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * بناء إشعار دائم صامت (Silent Persistent Notification)
     * - IMPORTANCE_MIN: أقل مستوى أهمية، لا يظهر في شريط الحالة
     * - setOngoing(true): غير قابل للسحب/الإخفاء
     * - بدون صوت أو اهتزاز أو أضواء
     */
    fun buildSilentNotification(
        context: Context,
        title: String,
        text: String
    ): Notification {
        ensureNotificationChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setSilent(true)
            .setColor(0xFF1A1A2E.toInt())
            .build()
    }

    /**
     * startForegroundCompat - دالة متوافقة مع جميع إصدارات أندرويد
     *
     * Android 14+ (API 34): يتطلب تحديد foregroundServiceType في startForeground()
     * Android 12+ (API 31): يتطلب startForegroundService() لبدء الخدمة
     * Android 8+  (API 26): يتطلب إنشاء NotificationChannel
     * Android 6+  (API 23): يتطلب صلاحية POST_NOTIFICATIONS
     * الأقدم: يعمل بشكل طبيعي
     *
     * @param service كائن الخدمة (Service)
     * @param notificationId معرف الإشعار
     * @param notification كائن الإشعار
     * @param foregroundServiceType نوع الخدمة الأمامية (متوافق مع Android 14+)
     */
    fun startForegroundCompat(
        service: Service,
        notificationId: Int,
        notification: Notification,
        foregroundServiceType: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (API 34): يجب تحديد foregroundServiceType صراحةً
                service.startForeground(
                    notificationId,
                    notification,
                    foregroundServiceType
                )
                Timber.d("startForegroundCompat: Android 14+ with type=$foregroundServiceType")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12-13 (API 31-33): يدعم ForegroundServiceType لكن اختياري
                service.startForeground(notificationId, notification)
                Timber.d("startForegroundCompat: Android 12-13")
            } else {
                // Android 8-11 (API 26-30)
                service.startForeground(notificationId, notification)
                Timber.d("startForegroundCompat: Android 8-11")
            }
        } catch (e: SecurityException) {
            Timber.e(e, "startForegroundCompat: SecurityException - missing permission or wrong type")
            // محاولة أخيرة بدون تحديد النوع
            try {
                service.startForeground(notificationId, notification)
            } catch (e2: Exception) {
                Timber.e(e2, "startForegroundCompat: fallback also failed")
            }
        } catch (e: Exception) {
            Timber.e(e, "startForegroundCompat: unexpected error")
        }
    }

    /**
     * دالة مساعدة لبدء خدمة كـ Foreground Service بشكل متوافق
     * تجمع بين startForegroundService + startForegroundCompat
     */
    fun startServiceAsForeground(
        context: Context,
        serviceClass: Class<*>,
        extras: (Intent.() -> Unit)? = null
    ) {
        val intent = Intent(context, serviceClass).apply {
            extras?.invoke(this)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
