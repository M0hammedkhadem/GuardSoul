package com.agon.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object AppNotificationChannels {
    const val FACEBOOK_VIDEO = "facebook_video_block"
    const val YOUTUBE_SHORTS = "youtube_shorts_block"
    const val APP_BLOCKER = "app_blocker"
    const val AI_SCANNER = "ai_scanner"
    const val TAMPER_ALERT = "tamper_alert"
    const val REMOTE_COMMANDS = "remote_commands"
    const val VPN_SECURITY_ALERT = "vpn_security_alert"

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

        val ytShorts = NotificationChannel(
            YOUTUBE_SHORTS,
            "YouTube Shorts Block",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "إشعارات عند حظر YouTube Shorts"
        }
        manager.createNotificationChannel(ytShorts)

        val appBlocker = NotificationChannel(
            APP_BLOCKER,
            context.getString(R.string.channel_app_blocker),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_app_blocker_desc)
        }
        manager.createNotificationChannel(appBlocker)

        val aiScanner = NotificationChannel(
            AI_SCANNER,
            "AI Scanner",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "تنبيهات ماسح الذكاء الاصطناعي عند اكتشاف محتوى غير لائق"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
        }
        manager.createNotificationChannel(aiScanner)

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

        val remoteCommands = NotificationChannel(
            REMOTE_COMMANDS,
            "Remote Commands",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for remote commands from parent dashboard"
            enableVibration(true)
        }
        manager.createNotificationChannel(remoteCommands)

        val vpnAlert = NotificationChannel(
            VPN_SECURITY_ALERT,
            "VPN Security Alert",
            NotificationManager.IMPORTANCE_MAX
        ).apply {
            description = "Alert when VPN connection drops unexpectedly"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
        }
        manager.createNotificationChannel(vpnAlert)
    }
}
