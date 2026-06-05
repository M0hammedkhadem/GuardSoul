package com.agon.app.blocking

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Modern replacement for the deprecated `AccessibilityNodeInfo.isChecked`
 * call in tab-selection detection.
 *
 * **Why this exists**
 * `isChecked` was marked deprecated in Android 14 (API 34) in favour of
 * a state-description-based API. The replacement is not a single flag —
 * it's a combination of:
 *
 *  1. `isSelected` — the *primary* signal for "currently selected" tabs.
 *     Not deprecated, and used by the AOSP Settings app for the same
 *     purpose. Returns true when the node is the selected item in a
 *     selection group (e.g. a bottom-nav tab).
 *
 *  2. `stateDescription` (API 30+) — explicit text describing the
 *     node's current state. Apps using Android 12+ accessibility
 *     semantics set this to "selected" / "on" / "off". Read with
 *     `getStateDescription()` on API 30+ and `contentDescription` as a
 *     fallback on older devices.
 *
 *  3. `contentDescription.contains("selected", true)` — the last
 *     resort. Many apps still embed the word "selected" in their
 *     contentDescription even when they don't bother calling
 *     `setSelected(true)`.
 *
 *  4. `isCheckable && isChecked` — kept for completeness; the
 *     `isChecked` half is deprecated but still functional and the
 *     signal is useful when none of the above fire. Wrap in
 *     `@Suppress("DEPRECATION")` at the call site.
 *
 * The result is `true` if **any** of the four signals indicate
 * "currently selected". This is the correct semantics for the
 * tab-context check in `PatternMatcher` and `FastDetector` —
 * we want to know whether the bottom-nav element is the one the user
 * is *currently* on, not whether it's selectable.
 */
internal fun AccessibilityNodeInfo.isCurrentlySelected(): Boolean {
    if (isSelected) return true

    val stateDesc: CharSequence? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { stateDescription }.getOrNull()
    } else {
        null
    }
    if (!stateDesc.isNullOrEmpty() &&
        stateDesc.toString().contains("selected", ignoreCase = true)
    ) return true

    val cd = contentDescription?.toString().orEmpty()
    if (cd.isNotEmpty() && cd.contains("selected", ignoreCase = true)) return true

    @Suppress("DEPRECATION")
    if (isCheckable && isChecked) return true

    return false
}
