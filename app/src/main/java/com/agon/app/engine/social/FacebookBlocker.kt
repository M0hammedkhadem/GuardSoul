package com.agon.app.engine.social

import com.agon.app.engine.*

class FacebookBlocker {
    companion object {
        private val REELS_CLASS_PATTERNS = listOf("Reels", "Clips", "reel", "clip", "Reel", "ReelsTab")
        private val REELS_VIEW_IDS = setOf(
            "com.facebook.katana:id/reel_viewer",
            "com.facebook.katana:id/reels_in_feed",
            "com.facebook.katana:id/reel_player",
            "com.facebook.katana:id/clips_in_feed",
            "com.facebook.katana:id/clips_viewer",
            "com.facebook.katana:id/video_home",
            "com.facebook.katana:id/reel_container",
            "com.facebook.katana:id/clips_container",
            "com.facebook.katana:id/reels_feed"
        )

        private val FEED_VIEW_IDS = setOf(
            "com.facebook.katana:id/feed_story",
            "com.facebook.katana:id/newsfeed",
            "com.facebook.katana:id/feed_content",
            "com.facebook.katana:id/story_container"
        )

        private val STORIES_VIEW_IDS = setOf(
            "com.facebook.katana:id/stories_container",
            "com.facebook.katana:id/stories_recycler"
        )

        private val MESSENGER_IDS = setOf(
            "com.facebook.orca:id/messages_list",
            "com.facebook.orca:id/thread_list",
            "com.facebook.orca:id/conversation_list"
        )

        val FACEBOOK_DOMAINS = listOf(
            "facebook.com", "fb.com", "fbcdn.net", "fb.me", "messenger.com",
            "facebook.net", "fbsbx.com", "meta.com", "instagram.com", "cdninstagram.com"
        )

        val FACEBOOK_CLASS_PATTERNS = listOf("Reels", "Clips", "reel", "clip", "Reel", "ReelsTab")

        fun getFacebookPartialRules(config: PlatformConfig): List<BlockMatch> {
            val matches = mutableListOf<BlockMatch>()
            for (target in config.partialTargets) {
                val match = when (target) {
                    PartialTarget.SHORTS -> BlockMatch(BlockAction.BLOCK_PARTIAL, "Facebook Reels blocked", MatchSource.SOCIAL_PARTIAL_BLOCK, 75)
                    PartialTarget.FEED -> BlockMatch(BlockAction.HIDE_ELEMENT, "Facebook Feed hidden", MatchSource.SOCIAL_PARTIAL_BLOCK, 60)
                    PartialTarget.STORIES -> BlockMatch(BlockAction.HIDE_ELEMENT, "Facebook Stories hidden", MatchSource.SOCIAL_PARTIAL_BLOCK, 60)
                    PartialTarget.NOTIFICATIONS -> BlockMatch(BlockAction.HIDE_ELEMENT, "Facebook Notifications hidden", MatchSource.SOCIAL_PARTIAL_BLOCK, 55)
                    PartialTarget.MESSAGES -> BlockMatch(BlockAction.BLOCK_PARTIAL, "Facebook Messages blocked", MatchSource.SOCIAL_PARTIAL_BLOCK, 70)
                    PartialTarget.EXPLORE -> BlockMatch(BlockAction.HIDE_ELEMENT, "Facebook Explore hidden", MatchSource.SOCIAL_PARTIAL_BLOCK, 60)
                    else -> null
                }
                if (match != null) matches.add(match)
            }
            return matches
        }

        fun getReelsViewIds(): Set<String> = REELS_VIEW_IDS

        fun getReelsClassPatterns(): List<String> = REELS_CLASS_PATTERNS

        fun detectPartialTarget(viewIdResourceName: String?, className: String?, contentDesc: String?, text: String? = null): PartialTarget? {
            val viewId = viewIdResourceName ?: ""
            val cls = className ?: ""
            val desc = contentDesc ?: ""
            val txt = text ?: ""

            if (viewId in REELS_VIEW_IDS) return PartialTarget.SHORTS
            if (REELS_CLASS_PATTERNS.any { cls.contains(it, ignoreCase = true) || desc.contains(it, ignoreCase = true) || txt.contains(it, ignoreCase = true) }) return PartialTarget.SHORTS
            if (viewId in FEED_VIEW_IDS) return PartialTarget.FEED
            if (viewId in STORIES_VIEW_IDS) return PartialTarget.STORIES
            if (viewId in MESSENGER_IDS) return PartialTarget.MESSAGES

            return null
        }

        fun getSafeSearchParams(): Map<String, String> {
            return mapOf("safe" to "active", "filter" to "1")
        }
    }
}
