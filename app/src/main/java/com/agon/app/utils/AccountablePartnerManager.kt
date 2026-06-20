package com.agon.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.agon.app.GuardianApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * AccountablePartnerManager - نظام الشريك المسؤول
 *
 * يسمح للمستخدم بإضافة شخص موثوق (أب، أم، صديق) يتلقى:
 * 1. إشعاراً عند محاولة فك الحظر
 * 2. تقريراً أسبوعياً بمحاولات الحظر
 * 3. تنبيهاً عند تفعيل وضع التجربة
 *
 * Note: يستخدم SMS/Email/WhatsApp لتسليم التقارير.
 * يمكن استبداله بـ Firebase Cloud Messaging لاحقاً.
 */
class AccountablePartnerManager private constructor(context: Context) {

    companion object {
        @Volatile private var instance: AccountablePartnerManager? = null

        fun getInstance(context: Context): AccountablePartnerManager {
            return instance ?: synchronized(this) {
                instance ?: AccountablePartnerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val app = context.applicationContext as GuardianApp
    private val settings = app.repository.getAppSettings()

    /**
     * إرسال تقرير فوري (عند محاولة فك الحظر أو دخول Safe Mode).
     */
    fun sendAlert(message: String) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val contact = settings.getPartnerContact() ?: return@launch
                val method = settings.getPartnerContactMethod() ?: "sms"

                when (method) {
                    "sms" -> sendSms(contact, message)
                    "email" -> sendEmail(contact, message)
                    "whatsapp" -> sendWhatsApp(contact, message)
                }
                Timber.i("AccountablePartner: alert sent to $contact via $method")
            } catch (e: Exception) {
                Timber.w(e, "AccountablePartner: failed to send alert")
            }
        }
    }

    /**
     * إرسال تقرير دوري (أسبوعي).
     */
    fun sendWeeklyReport(blockEvents: List<String>) {
        val message = buildString {
            appendLine("📊 تقرير GuardSoul الأسبوعي")
            appendLine("عدد محاولات الحظر: ${blockEvents.size}")
            appendLine("التطبيقات الأكثر محاولة:")
            blockEvents.groupingBy { it }.eachCount().toList()
                .sortedByDescending { it.second }
                .take(5)
                .forEach { (app, count) ->
                    appendLine("  - $app: $count محاولة")
                }
        }
        sendAlert(message)
    }

    // ─── Delivery Methods ───────────────────────────────────────────────

    private fun sendSms(phone: String, message: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phone")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
        } catch (e: Exception) {
            Timber.w(e, "AccountablePartner: SMS failed")
        }
    }

    private fun sendEmail(email: String, message: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
                putExtra(Intent.EXTRA_SUBJECT, "GuardSoul Alert")
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
        } catch (e: Exception) {
            Timber.w(e, "AccountablePartner: Email failed")
        }
    }

    private fun sendWhatsApp(phone: String, message: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(message)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
        } catch (e: Exception) {
            Timber.w(e, "AccountablePartner: WhatsApp failed")
        }
    }
}
