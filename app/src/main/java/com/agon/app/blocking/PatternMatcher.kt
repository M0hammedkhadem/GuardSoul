package com.agon.app.blocking

import android.view.accessibility.AccessibilityNodeInfo

class PatternMatcher {

    data class Signature(
        val surfaceViewIdTokens: List<String>,
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

    private val signatures: Map<String, Signature> = mapOf(
        "com.facebook.katana" to Signature(
            surfaceViewIdTokens = listOf(
                "reels_viewer_fragment_container", "reels_video_container",
                "reels_inner_video_container", "reel_viewer_container",
                "reel_composer_container", "reels_composer_container",
            ),
        ),
        "com.facebook.lite" to Signature(
            surfaceViewIdTokens = listOf("reels_video_view", "reel_container"),
        ),
        "com.google.android.youtube" to Signature(
            surfaceViewIdTokens = listOf(
                "reel_watch_fragment_root", "reel_recycler", "reel_player_page_controller",
                "shorts_player", "reel_player", "shorts_video_player_view", "reels_player",
            ),
        ),
        "com.instagram.android" to Signature(
            surfaceViewIdTokens = listOf(
                "reels_video_container", "reel_viewer_container",
                "reels_clips_viewer_container", "clips_viewer_container",
            ),
        ),
        "com.zhiliaoapp.musically" to Signature(
            surfaceViewIdTokens = listOf(
                "video_view", "feed_video_container", "fyp_video_container",
                "for_you_video", "main_fragment_video",
            ),
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
    )

    fun signatureFor(packageName: String): Signature? = signatures[packageName]

    fun hasCuratedSignature(packageName: String): Boolean = packageName in signatures

    fun surfaceFor(packageName: String): Surface = when {
        packageName.startsWith("com.facebook") -> Surface.FACEBOOK_REELS
        packageName.startsWith("com.google.android.youtube") -> Surface.YOUTUBE_SHORTS
        packageName.startsWith("com.instagram") -> Surface.INSTAGRAM_REELS
        packageName.startsWith("com.zhiliaoapp") || packageName.startsWith("com.ss.android") -> Surface.TIKTOK_FYP
        packageName.startsWith("com.snapchat") -> Surface.SNAPCHAT_SPOTLIGHT
        packageName.startsWith("com.twitter") || packageName.startsWith("com.x.android") -> Surface.TWITTER_VIDEO
        else -> Surface.UNKNOWN
    }

    fun findFeedViewId(root: AccessibilityNodeInfo, pkg: String, sig: Signature): String? {
        for (token in sig.surfaceViewIdTokens) {
            val matches = root.findAccessibilityNodeInfosByViewId("$pkg:id/$token")
            if (matches.isNotEmpty()) {
                matches.forEach { it.recycle() }
                return token
            }
        }
        return null
    }
}
