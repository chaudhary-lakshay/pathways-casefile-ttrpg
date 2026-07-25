package com.pathways.rules

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
        require(points > 0) { "spend at least 1 point, or use defaultFor()" }
        val rung = RUNGS.indexOfLast { it <= points }
        return attributeLevel + difficulty.firstRungOffset + rung
    }

    /** Null when the skill cannot be attempted untrained at all. */
    fun defaultFor(difficulty: Difficulty, attributeLevel: Int): Int? =
        difficulty.defaultPenalty?.let { attributeLevel + it }
}
