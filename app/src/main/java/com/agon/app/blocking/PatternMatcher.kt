package com.agon.app.blocking

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber
import java.util.ArrayDeque

class PatternMatcher {

    data class Signature(
        val surfaceViewIdTokens: List<String>,
        val contentDescriptions: List<String> = emptyList(),
        val classNames: List<String> = emptyList(),
        val textLabels: List<String> = emptyList(),
        val windowTitlePatterns: List<String> = emptyList(),
    )

    enum class Surface(val label: String) {
        FACEBOOK_REELS("Facebook Reels"),
        YOUTUBE_SHORTS("YouTube Shorts"),
        INSTAGRAM_REELS("Instagram Reels"),
        TIKTOK_FYP("TikTok For You"),
        SNAPCHAT_SPOTLIGHT("Snapchat Spotlight"),
        TWITTER_VIDEO("Twitter / X video"),
        UNKNOWN("Unknown"),
    }

    data class DetectionResult(
        val token: String,
        val method: DetectionMethod,
        val confidence: Float,
    ) {
        val isReliable: Boolean get() = confidence >= 0.7f
    }

    enum class DetectionMethod(val label: String, val baseConfidence: Float) {
        EXACT_VIEW_ID("exactViewId", 0.98f),
        BFS_VIEW_ID("bfsViewId", 0.92f),
        WINDOW_TITLE("windowTitle", 0.85f),
        CONTENT_DESCRIPTION_EXACT("contentDescExact", 0.65f),
        TEXT_LABEL("textLabel", 0.50f),
        CONTENT_DESCRIPTION_FUZZY("contentDescFuzzy", 0.35f),
        CLASS_NAME("className", 0.25f),
    }

    private val signatures: Map<String, Signature> = mapOf(
        "com.facebook.katana" to Signature(
            surfaceViewIdTokens = listOf(
                "reels_viewer_fragment_container",
                "reels_video_container",
                "reels_inner_video_container",
                "reel_viewer_container",
                "reels_tray_container",
                "video_list_view",
                "video_view",
                "player_view",
                "media_container",
                "inline_video_player",
                "video_channel_root_container",
                "feed_reels_container",
                "see_more_reels",
                "reshare_video_view",
                "videoautoplay_view",
                "feed_video_player",
                "fullscreen_reels_player",
                "reels_grid_container",
                "reels_tray_recycler",
                "stories_tray_reels_item",
                "reels_creation_container",
            ),
            contentDescriptions = listOf(
                "Reels", "Reel",
            ),
            classNames = listOf(
                "com.facebook.feed.video.ui.InlineVideoPlayerView",
                "com.facebook.reels.surface.ReelsSurfaceView",
                "com.facebook.reels.common.ui.ReelsViewerView",
                "com.facebook.video.player.BrowserVideoPlayer",
            ),
            textLabels = listOf("Reels", "Reel"),
            windowTitlePatterns = listOf(
                "Reels", "reel",
            ),
        ),
        "com.facebook.lite" to Signature(
            surfaceViewIdTokens = listOf(
                "reels_video_view", "reel_container", "video_view",
                "reels_viewer", "video_player_view", "reel_player_view",
                "reels_feed_container", "reels_tray",
            ),
            contentDescriptions = listOf("Reels", "Reel"),
            classNames = listOf(
                "com.facebook.lite.videoplayer.VideoPlayerView",
                "com.facebook.lite.reels.ReelsView",
            ),
            textLabels = listOf("Reels", "Reel"),
            windowTitlePatterns = listOf("Reels", "reel"),
        ),
        "com.google.android.youtube" to Signature(
            surfaceViewIdTokens = listOf(
                "reel_watch_fragment_root", "reel_recycler", "reel_player_page_controller",
                "shorts_player", "reel_player", "shorts_video_player_view", "reels_player",
                "reels_watch_next_animated_header",
            ),
        ),
        "com.instagram.android" to Signature(
            surfaceViewIdTokens = listOf(
                "reels_video_container", "reel_viewer_container",
                "reels_clips_viewer_container", "clips_viewer_container",
            ),
        ),
        "com.instagram.lite" to Signature(
            surfaceViewIdTokens = listOf("reel_viewer_container", "reels_video_container"),
        ),
        "com.zhiliaoapp.musically" to Signature(
            surfaceViewIdTokens = listOf(
                "video_view", "feed_video_container", "fyp_video_container",
                "for_you_video", "main_fragment_video",
            ),
        ),
        "com.ss.android.ugc.trill" to Signature(
            surfaceViewIdTokens = listOf("video_view", "feed_video_container", "fyp_video_container"),
        ),
        "com.ss.android.ugc.aweme" to Signature(
            surfaceViewIdTokens = listOf("video_view", "feed_video_container", "fyp_video_container"),
        ),
        "video.like" to Signature(
            surfaceViewIdTokens = listOf("video_view", "feed_video_container"),
        ),
        "com.snapchat.android" to Signature(
            surfaceViewIdTokens = listOf(
                "spotlight_feed", "spotlight_player", "discover_feed",
                "discover_player", "snap_player",
            ),
        ),
        "com.twitter.android" to Signature(
            surfaceViewIdTokens = listOf("immersive_video_player_view", "video_player_view"),
        ),
        "com.x.android" to Signature(
            surfaceViewIdTokens = listOf("immersive_video_player_view", "video_player_view"),
        ),
        "com.kwai.video" to Signature(
            surfaceViewIdTokens = listOf("video_view", "feed_video_container"),
        ),
        "com.kwai.kuaishou.nebula" to Signature(
            surfaceViewIdTokens = listOf("video_view", "feed_video_container"),
        ),
        "com.google.android.apps.youtube.music" to Signature(
            surfaceViewIdTokens = listOf("reel_watch_fragment_root", "shorts_player"),
        ),
        "com.pinterest" to Signature(
            surfaceViewIdTokens = listOf("video_view", "pin_video_container"),
        ),
        "com.reddit.frontpage" to Signature(
            surfaceViewIdTokens = listOf("video_player", "shorts_player"),
        ),
    )

    private val knownShortFormPackages = setOf(
        "com.facebook.katana", "com.facebook.lite", "com.google.android.youtube",
        "com.instagram.android", "com.instagram.lite",
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.ss.android.ugc.aweme",
        "video.like", "com.snapchat.android", "com.twitter.android", "com.x.android",
        "com.kwai.video", "com.kwai.kuaishou.nebula",
        "com.google.android.apps.youtube.music", "com.pinterest", "com.reddit.frontpage"
    )

    fun signatureFor(packageName: String): Signature? = signatures[packageName]

    fun hasCuratedSignature(packageName: String): Boolean = packageName in signatures

    fun surfaceFor(packageName: String): Surface = when {
        packageName.startsWith("com.facebook") -> Surface.FACEBOOK_REELS
        packageName.startsWith("com.google.android.youtube") -> Surface.YOUTUBE_SHORTS
        packageName.startsWith("com.instagram") -> Surface.INSTAGRAM_REELS
        packageName.startsWith("com.zhiliaoapp") || packageName.startsWith("com.ss.android") || packageName == "video.like" -> Surface.TIKTOK_FYP
        packageName.startsWith("com.snapchat") -> Surface.SNAPCHAT_SPOTLIGHT
        packageName.startsWith("com.twitter") || packageName.startsWith("com.x.android") -> Surface.TWITTER_VIDEO
        packageName == "com.kwai.video" || packageName == "com.kwai.kuaishou.nebula" -> Surface.TIKTOK_FYP
        packageName == "com.pinterest" -> Surface.UNKNOWN
        packageName == "com.reddit.frontpage" -> Surface.UNKNOWN
        else -> Surface.UNKNOWN
    }

    fun isKnownShortFormPackage(packageName: String): Boolean = packageName in knownShortFormPackages

    fun findFeedViewId(root: AccessibilityNodeInfo, pkg: String, sig: Signature): String? {
        return findFeedViewIdWithConfidence(root, pkg, sig)?.token
    }

    fun findFeedViewIdWithConfidence(root: AccessibilityNodeInfo, pkg: String, sig: Signature): DetectionResult? {
        val candidates = mutableListOf<DetectionResult>()

        // Strategy 1: Exact package:id/name lookup (highest confidence).
        for (token in sig.surfaceViewIdTokens) {
            try {
                val matches = root.findAccessibilityNodeInfosByViewId("$pkg:id/$token")
                if (matches.isNotEmpty()) {
                    matches.forEach { it.recycle() }
                    candidates.add(DetectionResult(token, DetectionMethod.EXACT_VIEW_ID, DetectionMethod.EXACT_VIEW_ID.baseConfidence))
                }
            } catch (_: Exception) { }
        }
        if (bestReliable(candidates) != null) return bestReliable(candidates)

        // Strategy 2: BFS tree traversal matching any id ending with :id/<token>.
        for (token in sig.surfaceViewIdTokens) {
            val found = bfsFindNode(root) { node ->
                val viewId = node.viewIdResourceName
                viewId != null && viewId.endsWith(":id/$token", ignoreCase = true)
            }
            if (found != null) {
                found.recycle()
                candidates.add(DetectionResult(token, DetectionMethod.BFS_VIEW_ID, DetectionMethod.BFS_VIEW_ID.baseConfidence))
            }
        }
        if (bestReliable(candidates) != null) return bestReliable(candidates)

        // Strategy 3: Match by exact content description in video context.
        for (desc in sig.contentDescriptions) {
            val found = bfsFindNode(root) { node ->
                val nodeDesc = node.contentDescription?.toString() ?: return@bfsFindNode false
                nodeDesc.equals(desc, ignoreCase = true)
                    && hasReelsParentAncestry(node)
            }
            if (found != null) {
                found.recycle()
                candidates.add(DetectionResult(desc, DetectionMethod.CONTENT_DESCRIPTION_EXACT, DetectionMethod.CONTENT_DESCRIPTION_EXACT.baseConfidence))
            }
        }

        // Strategy 4: Match by visible text label in reels context.
        for (label in sig.textLabels) {
            val found = bfsFindNode(root) { node ->
                val text = node.text?.toString() ?: return@bfsFindNode false
                text.equals(label, ignoreCase = true)
                    && node.isVisibleToUser
                    && hasReelsParentAncestry(node)
            }
            if (found != null) {
                found.recycle()
                candidates.add(DetectionResult(label, DetectionMethod.TEXT_LABEL, DetectionMethod.TEXT_LABEL.baseConfidence))
            }
        }

        // Strategy 5: Fuzzy content description (contains) — only if another signal exists.
        if (candidates.isNotEmpty()) {
            for (desc in sig.contentDescriptions) {
                val found = bfsFindNode(root) { node ->
                    val nodeDesc = node.contentDescription?.toString() ?: return@bfsFindNode false
                    nodeDesc.contains(desc, ignoreCase = true)
                        && !nodeDesc.equals(desc, ignoreCase = true)
                        && hasReelsParentAncestry(node)
                }
                if (found != null) {
                    found.recycle()
                    candidates.add(DetectionResult("fuzzy:$desc", DetectionMethod.CONTENT_DESCRIPTION_FUZZY, DetectionMethod.CONTENT_DESCRIPTION_FUZZY.baseConfidence))
                }
            }
        }

        // Strategy 6: Class name (lowest confidence) — only if another signal exists.
        if (candidates.isNotEmpty()) {
            for (className in sig.classNames) {
                val found = bfsFindNode(root) { node ->
                    val nodeClassName = node.className?.toString() ?: return@bfsFindNode false
                    nodeClassName.equals(className, ignoreCase = true)
                }
                if (found != null) {
                    found.recycle()
                    candidates.add(DetectionResult(className, DetectionMethod.CLASS_NAME, DetectionMethod.CLASS_NAME.baseConfidence))
                }
            }
        }

        // Evaluate combined confidence.
        return evaluateBest(candidates)
    }

    fun detectFromWindowTitle(event: AccessibilityEvent, pkg: String, sig: Signature): DetectionResult? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val titleString = try {
            val getter = AccessibilityEvent::class.java.getMethod("getWindowTitle")
            getter.invoke(event)?.toString()
        } catch (_: Exception) {
            null
        } ?: return null

        for (pattern in sig.windowTitlePatterns) {
            if (titleString.contains(pattern, ignoreCase = true)) {
                return DetectionResult("window:$pattern", DetectionMethod.WINDOW_TITLE, DetectionMethod.WINDOW_TITLE.baseConfidence)
            }
        }

        // Also check the source class name of the event.
        val sourceClass = event.className?.toString()?.lowercase() ?: ""
        if (sourceClass.contains("reel") || sourceClass.contains("shorts")) {
            return DetectionResult("class:${event.className}", DetectionMethod.WINDOW_TITLE, DetectionMethod.WINDOW_TITLE.baseConfidence * 0.9f)
        }

        return null
    }

    fun isFacebookReelsDetected(root: AccessibilityNodeInfo, pkg: String): Boolean {
        val sig = signatureFor(pkg) ?: return false
        val result = findFeedViewIdWithConfidence(root, pkg, sig)
        return result != null && result.isReliable
    }

    private fun hasReelsParentAncestry(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        val maxDepth = 12

        while (current != null && depth < maxDepth) {
            val viewId = current.viewIdResourceName?.lowercase() ?: ""
            if (viewId.contains("reel")) return true
            if (viewId.contains("video") && viewId.contains("player")) return true

            val className = current.className?.toString()?.lowercase() ?: ""
            if (className.contains("reel")) return true
            // Very specific video player classes only.
            if (className.contains("videoplayer") || className.contains("video_view")) return true

            current = current.parent
            depth++
        }
        return false
    }

    private fun bestReliable(candidates: List<DetectionResult>): DetectionResult? {
        return candidates.maxByOrNull { it.confidence }?.takeIf { it.isReliable }
    }

    private fun evaluateBest(candidates: List<DetectionResult>): DetectionResult? {
        if (candidates.isEmpty()) return null

        // If any single candidate is reliable, use it.
        val reliable = bestReliable(candidates)
        if (reliable != null) return reliable

        // Combine confidence: 1 - (1-c1)*(1-c2)*...
        // Two medium signals can combine to a reliable detection.
        var combinedConfidence = 0f
        for (c in candidates) {
            combinedConfidence = combinedConfidence + c.confidence * (1f - combinedConfidence)
        }

        val bestMethod = candidates.maxByOrNull { it.confidence }!!
        return DetectionResult(bestMethod.token, bestMethod.method, combinedConfidence.coerceAtMost(0.99f))
    }

    private fun bfsFindNode(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val visited = mutableSetOf<Int>()
        var iterations = 0
        val maxIterations = 500

        queue.add(root)

        while (queue.isNotEmpty() && iterations < maxIterations) {
            val node = queue.removeFirst()
            iterations++

            val nodeId = System.identityHashCode(node)
            if (!visited.add(nodeId)) continue

            if (predicate(node)) {
                return node
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) queue.add(child)
            }
        }
        return null
    }
}
