package com.agon.app.utils

/**
 * Study Room — a temporary "focus lock" that blocks every app
 * outside a small allow-list. Inspired by Screen Stoic's "Focus
 * Room" and Forest's "Plant a tree" — the user commits to a
 * duration, the room auto-closes when the timer expires, and
 * total focused minutes accumulate in the persistent counter
 * (`studyRoomTotalMinutesFocused`) which feeds the discipline
 * score tier display.
 *
 * Categories always allowed inside the room:
 *  - Education
 *  - Productivity
 *  - Books
 *  - Notes
 *  - Reference
 *  - Cloud Storage
 *  - AI Assistants
 *  - Browsers (the user might need a web search)
 *
 * Every other category is blocked while the room is active.
 */
object StudyRoom {

    /** Categories that *remain* accessible while a Study Room is open. */
    val ALLOWED_CATEGORIES: Set<ContentCategory> = setOf(
        ContentCategory.EDUCATION,
        ContentCategory.PRODUCTIVITY,
        ContentCategory.BOOKS,
        ContentCategory.NOTES,
        ContentCategory.REFERENCE,
        ContentCategory.CLOUD_STORAGE,
        ContentCategory.AI_ASSISTANTS,
        ContentCategory.BROWSERS
    )

    /** Default focus duration in minutes. */
    const val DEFAULT_DURATION_MINUTES: Int = 60

    /** Allowed duration choices for the picker UI. */
    val DURATION_OPTIONS: List<Int> = listOf(15, 30, 60, 90, 120)

    /**
     * Returns `true` if the Study Room is currently active (i.e.
     * the user is inside a focus block).
     */
    fun isActive(activeUntil: Long, now: Long = System.currentTimeMillis()): Boolean =
        activeUntil > now

    /**
     * Returns the remaining focus time in milliseconds, or 0L if
     * the room has closed.
     */
    fun remainingMs(activeUntil: Long, now: Long = System.currentTimeMillis()): Long =
        (activeUntil - now).coerceAtLeast(0L)

    /**
     * Returns `true` if the given [pkg] is *not* in one of the
     * allowed categories and should therefore be blocked while the
     * Study Room is active. Unrecognised packages (category
     * [ContentCategory.OTHER]) are blocked — the user can whitelist
     * them via the regular whitelist flow.
     */
    fun shouldBlockPackage(pkg: String): Boolean {
        val cat = CategoryRegistry.detect(pkg)
        return cat !in ALLOWED_CATEGORIES
    }
}
