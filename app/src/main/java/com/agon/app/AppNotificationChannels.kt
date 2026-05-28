package com.agon.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object AppNotificationChannels {
    const val FACEBOOK_VIDEO = "facebook_video_block"
    const val APP_BLOCKER = "app_blocker"
    const val TAMPER_ALERT = "tamper_alert"

    fun createAll(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val fbVideo = NotificationChannel(
            FACEBOOK_VIDEO,
            context.getString(R.string.channel_facebook_video),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_facebook_video_desc)
        }
        manager.createNotificationChannel(fbVideo)

        val appBlocker = NotificationChannel(
            APP_BLOCKER,
            context.getString(R.string.channel_app_blocker),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_app_blocker_desc)
        }
        manager.createNotificationChannel(appBlocker)

        val tamperAlert = NotificationChannel(
            TAMPER_ALERT,
            "تنبيهات الأمان",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "تنبيهات عند محاولة تعديل على حماية التطبيق"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
        }
        manager.createNotificationChannel(tamperAlert)
    }
}
