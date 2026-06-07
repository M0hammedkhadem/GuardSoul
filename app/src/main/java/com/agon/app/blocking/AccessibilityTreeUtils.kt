package com.agon.app.blocking

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Tree-walking helpers shared by the accessibility-service
 * engines ([ContentFilterEngine], [UninstallGuardEngine], …).
 *
 * Extracted from the legacy Guardian service so each engine
 * doesn't have to re-implement (and re-cycle)
 * `AccessibilityNodeInfo` traversal.
 */
object AccessibilityTreeUtils {

    /**
     * Cap on recursive depth when walking an
     * [AccessibilityNodeInfo] tree. Bumped from the original 20
     * to 50 (Issue #253) to handle deep layouts in apps like
     * TikTok / Instagram where the player surface sits 30+
     * nodes deep.
     */
    const val MAX_LAYOUT_DEPTH = 50

    /**
     * Concatenate every `text` and `contentDescription` under
     * [node] into a single space-separated string. Recycles
     * each child node as it visits to avoid leaking
     * [AccessibilityNodeInfo] handles (an unchecked leak here
     * will crash the accessibility service on Samsung One UI).
     */
    fun extractAllText(node: AccessibilityNodeInfo, depth: Int = 0): String {
        if (depth > MAX_LAYOUT_DEPTH) return ""

        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            sb.append(extractAllText(child, depth + 1))
            child.recycle()
        }
        return sb.toString()
    }
}
