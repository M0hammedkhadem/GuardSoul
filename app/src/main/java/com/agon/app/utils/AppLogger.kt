package com.agon.app.utils

import com.agon.app.BuildConfig
import com.google.firebase.database.FirebaseDatabase
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val LOGS_PATH = "app_logs"
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun d(message: String, vararg args: Any) = Timber.d(message, *args)
    fun i(message: String, vararg args: Any) = Timber.i(message, *args)
    fun w(message: String, vararg args: Any) = Timber.w(message, *args)

    fun w(t: Throwable, message: String, vararg args: Any) {
        Timber.w(t, message, *args)
        logToFirebase("WARN", message, t)
    }

    fun e(message: String, vararg args: Any) {
        Timber.e(message, *args)
        logToFirebase("ERROR", message, null)
    }

    fun e(t: Throwable, message: String, vararg args: Any) {
        Timber.e(t, message, *args)
        logToFirebase("ERROR", message, t)
    }

    private fun logToFirebase(level: String, message: String, t: Throwable?) {
        if (BuildConfig.IS_DEBUG_BUILD) return
        try {
            val ref = FirebaseDatabase.getInstance().getReference(LOGS_PATH).push()
            val entry = mapOf(
                "level" to level,
                "message" to message,
                "error" to (t?.message ?: ""),
                "timestamp" to formatter.format(Date()),
                "ts" to System.currentTimeMillis()
            )
            ref.setValue(entry)
        } catch (_: Exception) {
        }
    }
}
