package com.agon.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.agon.app.data.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlin.random.Random

/**
 * "Accountability Partner" — a flow inspired by Bulldog Blocker and
 * Canopy that requires the user to get a one-time approval code from
 * a trusted contact before they can disable protection.
 *
 * We don't ship a real-time backend, so the partner approval is
 * asynchronous: the user generates a 6-digit code, the app hands them
 * a pre-filled `mailto:` intent addressed to their partner, the
 * partner replies with the code, and the user types it back. The code
 * expires after [CODE_VALIDITY_MS] so a stale code can never be
 * replayed. The flow is fully opt-in: the partner feature must be
 * explicitly enabled in Settings.
 */
object AccountabilityPartner {

    private const val TAG = "AccountabilityPartner"
    const val CODE_LENGTH = 6
    const val CODE_VALIDITY_MS = 5L * 60L * 1_000L   // 5 min

    /** Generate a 6-digit numeric code (zero-padded). */
    fun generateCode(): String =
        (1..CODE_LENGTH)
            .fold(StringBuilder(CODE_LENGTH)) { sb, _ -> sb.append(Random.nextInt(0, 10)) }
            .toString()

    /**
     * Build a pre-filled `mailto:` intent the user can use to send the
     * unlock code to their partner. Returns the launch intent.
     */
    fun buildEmailIntent(partnerEmail: String, code: String): Intent {
        val subject = "GuardSoul: unlock request"
        val body = buildString {
            append("Hi,\n\n")
            append("I'm trying to disable GuardSoul's protection. Could you confirm the 6-digit code below is correct before I continue?\n\n")
            append("Unlock code: ").append(code).append('\n').append('\n')
            append("This code expires in 5 minutes. If you didn't expect this message, you can ignore it.\n")
        }
        val mailto = Uri.parse("mailto:${partnerEmail.trim()}")
            .buildUpon()
            .appendQueryParameter("subject", subject)
            .appendQueryParameter("body", body)
            .build()
        return Intent(Intent.ACTION_SENDTO, mailto).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Helper that combines [generateCode], [AppSettings.setPendingUnlockCode],
     * and a [startActivity] for the partner email. Safe to call from a
     * background coroutine — the email intent has FLAG_ACTIVITY_NEW_TASK
     * so we don't need an Activity context.
     */
    suspend fun requestUnlock(
        context: Context,
        settings: AppSettings,
        partnerEmail: String
    ): String {
        val code = generateCode()
        val expiresAt = System.currentTimeMillis() + CODE_VALIDITY_MS
        settings.setPendingUnlockCode(code, expiresAt)
        try {
            context.startActivity(buildEmailIntent(partnerEmail, code))
        } catch (t: Throwable) {
            AppLogger.w(TAG, "No email app installed; user will copy the code manually", t)
        }
        return code
    }

    /**
     * Verify a user-entered code against the pending one. Returns one of:
     *   - [Result.OK]                       : code matches and is still valid
     *   - [Result.EXPIRED]                  : the pending code was set but is older than [CODE_VALIDITY_MS]
     *   - [Result.MISMATCH]                 : code does not match
     *   - [Result.NO_PENDING_REQUEST]       : no pending request was generated
     *
     * On OK the pending code is cleared so it can't be reused.
     */
    suspend fun verify(
        settings: AppSettings,
        entered: String
    ): Result {
        val pending = settings.pendingUnlockCodeFlow
        val expiresAt = settings.pendingUnlockCodeExpiresAtFlow
        val code = pending.first()
        if (code.isEmpty()) return Result.NO_PENDING_REQUEST
        val exp = expiresAt.first()
        val now = System.currentTimeMillis()
        if (exp <= now) {
            settings.clearPendingUnlockCode()
            return Result.EXPIRED
        }
        if (entered.trim() != code) return Result.MISMATCH
        settings.clearPendingUnlockCode()
        return Result.OK
    }

    enum class Result { OK, EXPIRED, MISMATCH, NO_PENDING_REQUEST }
}
