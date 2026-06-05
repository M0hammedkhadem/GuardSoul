package com.agon.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Share-card generator. Mirrors the "share your progress" pattern in
 * Screen Stoic, I Am Sober and Nomo — generate a 1080×1080 PNG that
 * the user can drop into Instagram, X, WhatsApp etc. with a single
 * tap.
 *
 * The card is built entirely on a `Canvas` (no Compose) so it can
 * run from a foreground service or a coroutine without needing an
 * `Activity` in scope. The output bitmap is written to the app's
 * `cacheDir` and exposed via `FileProvider`.
 *
 * The aesthetic is intentionally minimal: deep purple background,
 * white text, a single accent — a touch of the Headspace /
 * Calm brand language.
 */
object ShareCardGenerator {

    private const val WIDTH = 1080
    private const val HEIGHT = 1080

    /**
     * Build a progress-card bitmap. [data] is a snapshot from the
     * `HomeViewModel` so the generator doesn't need to know about
     * Koin / Flow.
     */
    fun render(data: ShareCardData): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // --- Background gradient -------------------------------------
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, HEIGHT.toFloat(),
                intArrayOf(Color.parseColor("#1A0B2E"), Color.parseColor("#3B1E5C")),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bgPaint)

        val white = Color.WHITE
        val muted = Color.parseColor("#BBBBBB")
        val accent = Color.parseColor("#7C3AED")

        // --- Title ---------------------------------------------------
        drawText(
            canvas, "GUARDSOUL",
            x = WIDTH / 2f,
            y = 120f,
            color = accent,
            size = 36f,
            align = Paint.Align.CENTER,
            bold = true
        )

        drawText(
            canvas, "My Progress",
            x = WIDTH / 2f,
            y = 220f,
            color = white,
            size = 64f,
            align = Paint.Align.CENTER,
            bold = true
        )

        // --- Big day counter -----------------------------------------
        val tier = DisciplineTiers.tierFor(data.disciplineScore)
        drawText(
            canvas, "${data.daysActive}",
            x = WIDTH / 2f,
            y = 460f,
            color = white,
            size = 200f,
            align = Paint.Align.CENTER,
            bold = true
        )
        drawText(
            canvas, if (data.daysActive == 1) "day strong" else "days strong",
            x = WIDTH / 2f,
            y = 510f,
            color = muted,
            size = 36f,
            align = Paint.Align.CENTER
        )

        // --- Tier badge ----------------------------------------------
        val badgeTop = 580f
        val badgeRect = RectF(WIDTH / 2f - 200f, badgeTop, WIDTH / 2f + 200f, badgeTop + 100f)
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2D1A4F")
        }
        canvas.drawRoundRect(badgeRect, 50f, 50f, badgePaint)
        drawText(
            canvas, "${tier.emoji}  ${tier.titleRes.toTierTitle()}",
            x = WIDTH / 2f,
            y = badgeTop + 65f,
            color = white,
            size = 36f,
            align = Paint.Align.CENTER,
            bold = true
        )

        // --- Stats row -----------------------------------------------
        val statsY = 760f
        val cellW = WIDTH / 3f
        val stats = listOf(
            Triple("Milestones", "${data.milestonesAchieved}", "${data.disciplineScore} pts"),
            Triple("Pledge", if (data.todayPledgeTaken) "Taken" else "—", "today"),
            Triple("Blocks", "${data.weeklyBlockCount}", "this week")
        )
        for ((i, stat) in stats.withIndex()) {
            val cx = cellW * (i + 0.5f)
            drawText(canvas, stat.first, cx, statsY, muted, 28f, Paint.Align.CENTER, bold = true)
            drawText(canvas, stat.second, cx, statsY + 60f, white, 56f, Paint.Align.CENTER, bold = true)
            drawText(canvas, stat.third, cx, statsY + 100f, muted, 24f, Paint.Align.CENTER)
        }

        // --- Footer / date -------------------------------------------
        val df = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        drawText(
            canvas, "guardsoul.app  •  ${df.format(Date())}",
            x = WIDTH / 2f,
            y = HEIGHT - 60f,
            color = muted,
            size = 24f,
            align = Paint.Align.CENTER
        )
        return bmp
    }

    /**
     * Persist the bitmap to the app cache directory and fire an
     * `ACTION_SEND` chooser. The receiving app reads the file via
     * `FileProvider` (already declared in the manifest).
     */
    fun share(context: Context, bmp: Bitmap): Intent {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "guardsoul_progress_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { os ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
        }
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share your progress")
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        size: Float,
        align: Paint.Align,
        bold: Boolean = false
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = size
            this.textAlign = align
            this.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }
        canvas.drawText(text, x, y, paint)
    }
}

/**
 * Snapshot of the home-screen data needed to render the share card.
 * Built by `HomeViewModel.buildShareCardData()` so the renderer
 * itself stays pure.
 */
data class ShareCardData(
    val daysActive: Int,
    val milestonesAchieved: Int,
    val disciplineScore: Int,
    val todayPledgeTaken: Boolean,
    val weeklyBlockCount: Int
)

private fun Int.toTierTitle(): String = when (this) {
    com.agon.app.R.string.tier_mind_beginner_title -> "Mindful Beginner"
    com.agon.app.R.string.tier_steady_climber_title -> "Steady Climber"
    com.agon.app.R.string.tier_mind_master_title -> "Mind Master"
    com.agon.app.R.string.tier_focused_warrior_title -> "Focused Warrior"
    com.agon.app.R.string.tier_disciplined_sage_title -> "Disciplined Sage"
    com.agon.app.R.string.tier_enlightened_title -> "Enlightened"
    else -> "Mindful Beginner"
}
