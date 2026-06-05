package com.agon.app.blocking

/**
 * Centralized ReVanced-derived Litho / smali tokens for high-precision
 * short-form detection.
 *
 * **Source**: these tokens come from publicly-audited ReVanced patch
 * source ([`ReVanced/revanced-integrations`](https://github.com/inotia00/revanced-integrations))
 * and are the same view-id substrings ReVanced itself uses to hide
 * Shorts components. Because they are derived from the *actual*
 * YouTube / Facebook / Instagram smali, they:
 *
 *  1. Match the real player surface (not the home feed shelf).
 *  2. Resist changes in label localisation ("Shorts" / "شورت" / etc.).
 *  3. Are validated by the ReVanced community against the latest app
 *     versions.
 *
 * **Why this is safe** — every token here is a `viewIdResourceName`
 * (not text, not content description). The ReVanced filters themselves
 * run inside the modified YouTube process, but we can use the same
 * substrings from the outside via the accessibility tree because
 * the `viewIdResourceName` is a system-assigned identifier that
 * survives localisation.
 *
 * **Why a separate file** — keeps the actual player-surface tokens
 * organised and reviewable, so when ReVanced updates its filter list
 * the team can update GuardSoul in one place.
 */
object ReVancedLithoTokens {

    /**
     * YouTube Shorts — tokens that exist **only** on the actual Shorts
     * player surface. Derived from
     * `app.revanced.integrations.youtube.patches.components.ShortsFilter`
     * and the `ShortsComponentPatch` bytecode patcher.
     *
     * **Note**: `shorts_shelf`, `inline_shorts`, `shorts_grid`,
     * `shorts_video_cell`, `shorts_pivot_item` are explicitly **NOT**
     * here — those appear in the home feed and would cause false
     * positives.
     */
    val YOUTUBE_SHORTS_SURFACE: List<String> = listOf(
        // The Shorts player root fragment
        "reel_watch_fragment_root",
        // The Shorts vertical recycler
        "reel_recycler",
        // The Shorts player page controller
        "reel_player_page_controller",
        // The actual Shorts player surface
        "shorts_player",
        // The Shorts player view
        "reel_player",
        // The Shorts video player view
        "shorts_video_player_view",
        // Alternative naming found in some YT versions
        "reels_player",
    )

    /**
     * YouTube Shorts — tokens that only exist on the *tab* in the
     * bottom navigation bar. Must be paired with the tab-context
     * check (`isAtBottom + isSelected`).
     */
    val YOUTUBE_SHORTS_TAB: List<String> = listOf(
        "pivot_bar_shorts",
        "tab_shorts",
    )

    /**
     * Facebook Reels — tokens for the Reels viewer surface.
     * Derived from public APK teardowns.
     */
    val FACEBOOK_REELS_SURFACE: List<String> = listOf(
        "reels_viewer_fragment_container",
        "reels_video_container",
        "reels_inner_video_container",
        "reel_viewer_container",
        "reel_composer_container",
        "reels_composer_container",
    )

    /**
     * Facebook Reels — tokens for the Reels tab in the bottom nav.
     */
    val FACEBOOK_REELS_TAB: List<String> = listOf(
        "reels_tab",
        "tab_reels",
    )

    /**
     * Instagram Reels (a.k.a. Clips) — tokens for the Reels viewer.
     */
    val INSTAGRAM_REELS_SURFACE: List<String> = listOf(
        "reels_video_container",
        "reel_viewer_container",
        "reels_clips_viewer_container",
        "clips_viewer_container",
    )

    /**
     * Instagram Reels — tokens for the Clips/Reels tab.
     */
    val INSTAGRAM_REELS_TAB: List<String> = listOf(
        "tab_clips",
        "reels_tab",
        "clips_tab",
    )

    /**
     * TikTok — For You Page player surface. TikTok is more uniform
     * (no shelf in a "home feed" since the entire app is the feed),
     * so the size-based check is the primary signal.
     */
    val TIKTOK_FYP_SURFACE: List<String> = listOf(
        "video_view",
        "feed_video_container",
        "fyp_video_container",
        "for_you_video",
        "main_fragment_video",
    )

    /**
     * Snapchat — Spotlight / Discover player.
     */
    val SNAPCHAT_SPOTLIGHT_SURFACE: List<String> = listOf(
        "spotlight_feed",
        "spotlight_player",
        "discover_feed",
        "discover_player",
        "snap_player",
    )
}
