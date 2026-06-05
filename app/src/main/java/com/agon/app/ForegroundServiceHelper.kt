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
    
    // Issue #174: Use a stable request code for the notification PendingIntent
    private const val FOREGROUND_NOTIFICATION_REQUEST_CODE = 1001

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
        
        // Issue #174: Fixed request code to avoid resource exhaustion and allow reuse
        val pendingIntent = PendingIntent.getActivity(
            context,
            FOREGROUND_NOTIFICATION_REQUEST_CODE,
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
     */
    fun startForegroundCompat(
        service: Service,
        notificationId: Int,
        notification: Notification,
        foregroundServiceType: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                service.startForeground(
                    notificationId,
                    notification,
                    foregroundServiceType
                )
            } else {
                service.startForeground(notificationId, notification)
            }
        } catch (e: SecurityException) {
            Timber.e(e, "startForegroundCompat: SecurityException")
            try {
                service.startForeground(notificationId, notification)
            } catch (e2: Exception) {
                Timber.e(e2, "startForegroundCompat: fallback failed")
            }
        } catch (e: Exception) {
            // Issue #267: Broaden exception handling to catch IllegalStateException for FGS start requirements
            Timber.e(e, "startForegroundCompat: unexpected error starting foreground service")
        }
    }

    fun startServiceAsForeground(
        context: Context,
        serviceClass: Class<*>,
        intent: Intent? = null
    ) {
        val serviceIntent = intent ?: Intent(context, serviceClass)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start service ${serviceClass.simpleName} as foreground")
        }
    }
}
