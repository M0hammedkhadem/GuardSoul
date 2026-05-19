package com.agon.app.engine.social

import com.agon.app.engine.*

class SocialBlocker(
    private val platformConfigs: Map<SocialPlatform, PlatformConfig> = emptyMap(),
    private val appBlacklist: Set<String> = emptySet(),
    private val appWhitelist: Set<String> = emptySet(),
    private val browserPackages: Set<String> = setOf(
        "com.android.chrome", "org.mozilla.firefox", "com.opera.browser",
        "com.microsoft.emmx", "com.brave.browser", "com.duckduckgo.mobile.android"
    )
) {
    fun evaluate(ctx: FilterContext): BlockMatch? {
        val pkg = ctx.packageName ?: return null
        if (pkg in appWhitelist) return null

        for ((platform, config) in platformConfigs) {
            if (config.mode == BlockMode.OFF) continue
            if (pkg !in platform.packageNames && !shouldBlockCrossPlatform(pkg, platform, config)) continue

            when (config.mode) {
                BlockMode.FULL -> {
                    if (pkg in platform.packageNames || config.blockAcrossApps) {
                        return BlockMatch(BlockAction.BLOCK_FULL, "${platform.displayName}: Full block", MatchSource.SOCIAL_FULL_BLOCK, 90)
                    }
                }
                BlockMode.PARTIAL -> {
                    if (pkg in platform.packageNames) {
                        val targets = config.partialTargets
                        val action = when {
                            PartialTarget.SHORTS in targets -> BlockAction.BLOCK_PARTIAL
                            PartialTarget.COMMENTS in targets -> BlockAction.HIDE_ELEMENT
                            PartialTarget.RECOMMENDATIONS in targets -> BlockAction.HIDE_ELEMENT
                            PartialTarget.FEED in targets -> BlockAction.BLOCK_PARTIAL
                            else -> BlockAction.BLOCK_PARTIAL
                        }
                        return BlockMatch(action, "${platform.displayName}: ${targets.joinToString { it.label }}", MatchSource.SOCIAL_PARTIAL_BLOCK, 70)
                    }
                }
                BlockMode.TIMED -> {
                    if (isWithinSchedule(config.schedule)) {
                        return BlockMatch(BlockAction.BLOCK_FULL, "${platform.displayName}: Scheduled block", MatchSource.SOCIAL_FULL_BLOCK, 80)
                    }
                }
                BlockMode.USAGE_LIMIT -> {
                    return BlockMatch(BlockAction.ALLOW, "${platform.displayName}: Usage limit tracking", MatchSource.NONE, 0)
                }
                else -> {}
            }
        }

        if (pkg in appBlacklist) {
            return BlockMatch(BlockAction.BLOCK_FULL, "Blacklisted app", MatchSource.BLACKLIST_APP, 100)
        }

        return null
    }

    private fun shouldBlockCrossPlatform(pkg: String, platform: SocialPlatform, config: PlatformConfig): Boolean {
        if (!config.blockAcrossApps && !config.blockAcrossBrowsers) return false
        if (config.blockAcrossApps && pkg in platform.packageNames) return true
        if (config.blockAcrossBrowsers && pkg in browserPackages) return true
        return false
    }

    private fun isWithinSchedule(schedule: TimeSchedule): Boolean {
        if (!schedule.enabled) return false
        val now = java.util.Calendar.getInstance()
        val day = schedule.activeDays.any { it.index == (now.get(java.util.Calendar.DAY_OF_WEEK) - 1).let { d -> if (d == 0) 7 else d } }
        if (!day) return false
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        return hour in schedule.startHour until schedule.endHour
    }
}
