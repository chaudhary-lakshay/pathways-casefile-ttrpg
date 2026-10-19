package com.pathways.rules

import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty

/**
 * Ch. 3, p. 25-26. Cumulative point totals form a doubling ladder;
 * difficulty decides where the first rung lands relative to the
 * controlling attribute, and each further rung is worth +1.
 *
 * This is NOT vanilla GURPS costing — under GURPS 4e core the ladder is
 * 1/2/4/8/12/16 with +4 per level past Attr+2. Using core costs would make
 * 14 of the 232 published skill levels wrong.
 */
object SkillLadder {

    private val RUNGS = intArrayOf(1, 2, 4, 8, 16, 32, 64, 128, 256)

    fun levelFor(points: Int, difficulty: Difficulty, attributeLevel: Int): Int {
        require(points > 0) { "spend at least 1 point, or ask SkillDefaults for an untrained level" }
        val rung = RUNGS.indexOfLast { it <= points }
        return attributeLevel + difficulty.firstRungOffset + rung
    }

    /**
     * Ch. 2's table and nothing else: null for Very Hard, which prints "No
     * default". A SKILL's default is [SkillDefaults]', because three skills
     * print a line of their own that departs from this.
     */
    fun defaultFor(difficulty: Difficulty, attributeLevel: Int): Int? =
        difficulty.defaultPenalty?.let { attributeLevel + it }

    /**
     * The cumulative points the next rung up costs, or null past the printed
     * grid's last rung.
     *
     * The ladder's own knowledge, exposed so [Study] can plan against it
     * rather than keeping a second copy of [RUNGS] -- the duplication 6at
     * removed from the divination clarity table. Points BELOW a rung still sit
     * on the rung under them, so 3 points has 4 as its next rung, not 8.
     */
    fun nextRungPoints(points: Int): Int? {
        require(points >= 0) { "points spent cannot be negative; was $points" }
        return RUNGS.firstOrNull { it > points }
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
     * bought point, down un-buys it as it always did. Past the printed grid,
     * up buys nothing.
     */
    fun stepped(bought: Int, studied: Int, up: Boolean): Int {
        require(bought > 0 && studied >= 0) { "a bought skill holds at least 1 point; was $bought + $studied" }
        val total = bought + studied
        if (up) return (nextRungPoints(total) ?: return bought) - studied
        val rung = RUNGS.indexOfLast { it <= total }
        val below = if (rung == 0) 0 else RUNGS[rung - 1]
        val sold = below - studied
        return when {
            sold >= 1 -> sold
            bought > 1 -> 1
            else -> 0
        }
    }
}
