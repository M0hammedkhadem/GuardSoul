package com.agon.app.engine.social

import com.agon.app.engine.*

class YouTubeBlocker {
    companion object {
        private val SHORTS_VIEW_IDS = setOf(
            "com.google.android.youtube:id/reel_progress_bar",
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_player_page",
            "com.google.android.youtube:id/shorts_video_list",
            "com.google.android.youtube:id/reel_watch_player",
            "com.google.android.youtube:id/reel_player_view",
            "com.google.android.youtube:id/reel_watch_fragment_root",
            "com.google.android.youtube:id/reel_container",
            "com.google.android.youtube:id/shorts_results",
            "com.google.android.youtube:id/shorts_player_circle",
            "com.google.android.youtube:id/shorts_video_view",
            "com.google.android.youtube:id/reel_action_bar",
            "com.google.android.youtube:id/bottom_sheet_reel"
        )

        private val COMMENT_VIEW_IDS = setOf(
            "com.google.android.youtube:id/comments_entrance",
            "com.google.android.youtube:id/comment_thread",
            "com.google.android.youtube:id/comment_simplebox",
            "com.google.android.youtube:id/comments_view"
        )

        private val RECOMMENDATION_VIEW_IDS = setOf(
            "com.google.android.youtube:id/player_controls_overlay",
            "com.google.android.youtube:id/watch_next_title",
            "com.google.android.youtube:id/watch_next_thumbnail",
            "com.google.android.youtube:id/recommendation_row"
        )

        private val FEED_VIEW_IDS = setOf(
            "com.google.android.youtube:id/home_content",
            "com.google.android.youtube:id/channel_header",
            "com.google.android.youtube:id/trending_content",
            "com.google.android.youtube:id/subscriptions_content"
        )

        val YOUTUBE_DOMAINS = listOf("youtube.com", "youtu.be", "ytimg.com", "googlevideo.com", "youtube.googleapis.com")

        fun getYoutubePartialRules(config: PlatformConfig): List<BlockMatch> {
            val matches = mutableListOf<BlockMatch>()
            for (target in config.partialTargets) {
                val match = when (target) {
                    PartialTarget.SHORTS -> BlockMatch(BlockAction.BLOCK_PARTIAL, "YouTube Shorts blocked", MatchSource.SOCIAL_PARTIAL_BLOCK, 75)
                    PartialTarget.COMMENTS -> BlockMatch(BlockAction.HIDE_ELEMENT, "YouTube Comments hidden", MatchSource.SOCIAL_PARTIAL_BLOCK, 60)
                    PartialTarget.RECOMMENDATIONS -> BlockMatch(BlockAction.HIDE_ELEMENT, "YouTube Recommendations hidden", MatchSource.SOCIAL_PARTIAL_BLOCK, 60)
                    PartialTarget.FEED -> BlockMatch(BlockAction.BLOCK_PARTIAL, "YouTube Feed blocked", MatchSource.SOCIAL_PARTIAL_BLOCK, 70)
                    else -> null
                }
                if (match != null) matches.add(match)
            }
            return matches
        }

        fun getShortsViewIds(): Set<String> = SHORTS_VIEW_IDS

        fun detectPartialTarget(viewIdResourceName: String?): PartialTarget? {
            if (viewIdResourceName == null) return null
            return when {
                viewIdResourceName in SHORTS_VIEW_IDS -> PartialTarget.SHORTS
                viewIdResourceName in COMMENT_VIEW_IDS -> PartialTarget.COMMENTS
                viewIdResourceName in RECOMMENDATION_VIEW_IDS -> PartialTarget.RECOMMENDATIONS
                viewIdResourceName in FEED_VIEW_IDS -> PartialTarget.FEED
                else -> null
            }
        }

        fun extractVideoId(url: String): String? {
            val patterns = listOf(
                Regex("""(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/embed/|youtube\.com/shorts/)([a-zA-Z0-9_-]{11})"""),
                Regex("""youtube\.com/watch\?.*v=([a-zA-Z0-9_-]{11})""")
            )
            for (pattern in patterns) {
                val match = pattern.find(url)
                if (match != null) return match.groupValues[1]
            }
            return null
        }

        fun isShortsUrl(url: String): Boolean {
            return url.contains("youtube.com/shorts/", ignoreCase = true) ||
                   url.contains("youtube.com/shorts?", ignoreCase = true)
        }

        fun getSafeSearchParams(): Map<String, String> {
            return mapOf("safe" to "active", "search_query" to "", "spf" to "nofilter")
        }
    }
}
