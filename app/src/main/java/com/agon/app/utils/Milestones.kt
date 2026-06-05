package com.agon.app.utils

/**
 * Streak milestones inspired by I Am Sober + Nomo. The user unlocks
 * an achievement (and earns XP) the first time their continuous
 * shield-active streak hits a milestone day.
 *
 * Days are inclusive: 1 day, 3 days, 7 days, ...
 */
object Milestones {

    data class Milestone(
        val id: String,
        val days: Int,
        val titleRes: Int,
        val descRes: Int,
        val xpReward: Int
    )

    val all: List<Milestone> = listOf(
        Milestone("m1",  1,   com.agon.app.R.string.milestone_1_title,   com.agon.app.R.string.milestone_1_desc,    10),
        Milestone("m3",  3,   com.agon.app.R.string.milestone_3_title,   com.agon.app.R.string.milestone_3_desc,    25),
        Milestone("m7",  7,   com.agon.app.R.string.milestone_7_title,   com.agon.app.R.string.milestone_7_desc,    75),
        Milestone("m14", 14,  com.agon.app.R.string.milestone_14_title,  com.agon.app.R.string.milestone_14_desc,  150),
        Milestone("m30", 30,  com.agon.app.R.string.milestone_30_title,  com.agon.app.R.string.milestone_30_desc,  300),
        Milestone("m90", 90,  com.agon.app.R.string.milestone_90_title,  com.agon.app.R.string.milestone_90_desc,  900),
        Milestone("m180",180, com.agon.app.R.string.milestone_180_title, com.agon.app.R.string.milestone_180_desc,1800),
        Milestone("m365",365, com.agon.app.R.string.milestone_365_title, com.agon.app.R.string.milestone_365_desc,3650),
    )

    fun findById(id: String): Milestone? = all.firstOrNull { it.id == id }

    /** Return the highest milestone the user has not yet hit, given their current streak. */
    fun nextUnlocked(currentDays: Int, achieved: Set<String>): Milestone? =
        all.firstOrNull { it.days <= currentDays && it.id !in achieved }

    /**
     * Given a streak day count and the set of already-achieved IDs,
     * return the milestones newly reached.
     */
    fun newlyAchieved(currentDays: Int, achieved: Set<String>): List<Milestone> =
        all.filter { it.days <= currentDays && it.id !in achieved }
}
