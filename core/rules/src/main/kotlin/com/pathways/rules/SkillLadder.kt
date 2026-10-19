package com.pathways.rules

import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty

/**
 * Ch. 3 Step 4 (v6.9i line 1665-1700). Cumulative point totals double for
 * five rungs (1, 2, 4, 8, 16) and then climb by a flat 16 with no end:
 * "every level after that costs a flat 16 pts". Difficulty decides where the
 * first rung lands relative to the controlling attribute, and each further
 * rung is worth +1.
 *
 * This is NOT vanilla GURPS costing — under GURPS 4e core the ladder is
 * 1/2/4/8/12/16 with +4 per level past Attr+2.
 */
object SkillLadder {

    /** The doubling opening; the last of these is where the flat rate starts. */
    private val OPENING = intArrayOf(1, 2, 4, 8, 16)

    /** "every level after that costs a flat 16 pts" (line 1672-1673). */
    private const val FLAT = 16

    /** The rung [points] buys: -1 below the first, then 0, 1, 2 ... */
    private fun rungOf(points: Int): Int =
        if (points < OPENING.last()) OPENING.indexOfLast { it <= points }
        else OPENING.lastIndex + Math.floorDiv(points - OPENING.last(), FLAT)

    /** The cumulative points rung [rung] costs. */
    private fun pointsAt(rung: Int): Int =
        if (rung <= OPENING.lastIndex) OPENING[rung] else OPENING.last() + (rung - OPENING.lastIndex) * FLAT

    fun levelFor(points: Int, difficulty: Difficulty, attributeLevel: Int): Int {
        require(points > 0) { "spend at least 1 point, or ask SkillDefaults for an untrained level" }
        return attributeLevel + difficulty.firstRungOffset + rungOf(points)
    }

    /**
     * Ch. 2's table and nothing else: null for Very Hard, which prints "No
     * default". A SKILL's default is [SkillDefaults]', because three skills
     * print a line of their own that departs from this.
     */
    fun defaultFor(difficulty: Difficulty, attributeLevel: Int): Int? =
        difficulty.defaultPenalty?.let { attributeLevel + it }

    /**
     * The cumulative points the next rung up costs. There is always one: the
     * flat rate has no printed end.
     *
     * The ladder's own knowledge, exposed so [Study] can plan against it
     * rather than keeping a second copy of the rungs -- the duplication 6at
     * removed from the divination clarity table. Points BELOW a rung still sit
     * on the rung under them, so 3 points has 4 as its next rung, not 8.
     */
    fun nextRungPoints(points: Int): Int {
        require(points >= 0) { "points spent cannot be negative; was $points" }
        return pointsAt(rungOf(points) + 1)
    }

    /**
     * The points to have bought after one step of the builder's stepper:
     * "you pay the difference to move from one step to the next" (Ch. 3,
     * line 1573), so a step lands on a rung, counting [studied] toward it and
     * never buying it. Zero means the skill is un-bought.
     *
     * Down sells to the rung below what bought and studied hold together. If
     * study alone already holds that rung, one bought point stays rather than
     * un-buying the skill, because un-buying also discards its study; at one
     * bought point, down un-buys it as it always did.
     */
    fun stepped(bought: Int, studied: Int, up: Boolean): Int {
        require(bought > 0 && studied >= 0) { "a bought skill holds at least 1 point; was $bought + $studied" }
        val total = bought + studied
        if (up) return nextRungPoints(total) - studied
        val rung = rungOf(total)
        val below = if (rung == 0) 0 else pointsAt(rung - 1)
        val sold = below - studied
        return when {
            sold >= 1 -> sold
            bought > 1 -> 1
            else -> 0
        }
    }
}
