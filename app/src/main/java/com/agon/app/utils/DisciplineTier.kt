package com.agon.app.utils

/**
 * Discipline progression — six tiers inspired by ScreenZen, Screen Stoic
 * and Headspace's badge system. The user advances through the tiers
 * based on a composite [DisciplineScore] that mixes:
 *  - streak days (long-term consistency)
 *  - milestones achieved (one-off events)
 *  - porn-attempt count for the last 7 days (inverse)
 *  - whether today's pledge has been taken (bonus)
 *
 * Tiers are *display only* — the actual blocking behaviour is unaffected
 * by the user's tier, mirroring how Reddit/Discord badge systems
 * reward engagement without granting privileges.
 */
data class DisciplineTier(
    val id: String,
    val ordinal: Int,
    val titleRes: Int,
    val subtitleRes: Int,
    val emoji: String,
    val minScore: Int
) {
    val isHighest: Boolean get() = this == DisciplineTiers.HIGHEST
}

object DisciplineTiers {

    val MIND_BEGINNER = DisciplineTier(
        id = "mind_beginner",
        ordinal = 1,
        titleRes = com.agon.app.R.string.tier_mind_beginner_title,
        subtitleRes = com.agon.app.R.string.tier_mind_beginner_subtitle,
        emoji = "🌱",
        minScore = 0
    )

    val STEADY_CLIMBER = DisciplineTier(
        id = "steady_climber",
        ordinal = 2,
        titleRes = com.agon.app.R.string.tier_steady_climber_title,
        subtitleRes = com.agon.app.R.string.tier_steady_climber_subtitle,
        emoji = "🌿",
        minScore = 50
    )

    val MIND_MASTER = DisciplineTier(
        id = "mind_master",
        ordinal = 3,
        titleRes = com.agon.app.R.string.tier_mind_master_title,
        subtitleRes = com.agon.app.R.string.tier_mind_master_subtitle,
        emoji = "🌳",
        minScore = 200
    )

    val FOCUSED_WARRIOR = DisciplineTier(
        id = "focused_warrior",
        ordinal = 4,
        titleRes = com.agon.app.R.string.tier_focused_warrior_title,
        subtitleRes = com.agon.app.R.string.tier_focused_warrior_subtitle,
        emoji = "🛡️",
        minScore = 500
    )

    val DISCIPLINED_SAGE = DisciplineTier(
        id = "disciplined_sage",
        ordinal = 5,
        titleRes = com.agon.app.R.string.tier_disciplined_sage_title,
        subtitleRes = com.agon.app.R.string.tier_disciplined_sage_subtitle,
        emoji = "🦉",
        minScore = 1500
    )

    val ENLIGHTENED = DisciplineTier(
        id = "enlightened",
        ordinal = 6,
        titleRes = com.agon.app.R.string.tier_enlightened_title,
        subtitleRes = com.agon.app.R.string.tier_enlightened_subtitle,
        emoji = "🏆",
        minScore = 3000
    )

    val ALL: List<DisciplineTier> = listOf(
        MIND_BEGINNER,
        STEADY_CLIMBER,
        MIND_MASTER,
        FOCUSED_WARRIOR,
        DISCIPLINED_SAGE,
        ENLIGHTENED
    )

    val HIGHEST: DisciplineTier = ENLIGHTENED

    /**
     * Returns the highest tier the [score] qualifies for.
     */
    fun tierFor(score: Int): DisciplineTier {
        var current = MIND_BEGINNER
        for (t in ALL) {
            if (score >= t.minScore) current = t
        }
        return current
    }

    /**
     * Returns the next tier above the given one, or null if already
     * at the top. Used for the "X points to next tier" subtitle.
     */
    fun nextTier(current: DisciplineTier): DisciplineTier? =
        ALL.firstOrNull { it.ordinal == current.ordinal + 1 }

    /**
     * Returns a 0..1 fraction representing how far the user is from
     * the next tier. Returns 1.0 if at the top tier.
     */
    fun progressToNext(score: Int): Float {
        val tier = tierFor(score)
        val next = nextTier(tier) ?: return 1f
        val span = (next.minScore - tier.minScore).coerceAtLeast(1)
        val into = (score - tier.minScore).coerceAtLeast(0)
        return (into.toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }
}
