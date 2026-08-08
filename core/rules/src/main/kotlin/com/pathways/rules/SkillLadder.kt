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
        require(points > 0) { "spend at least 1 point, or use defaultFor()" }
        val rung = RUNGS.indexOfLast { it <= points }
        return attributeLevel + difficulty.firstRungOffset + rung
    }

    /** Null when the skill cannot be attempted untrained at all. */
    fun defaultFor(difficulty: Difficulty, attributeLevel: Int): Int? =
        difficulty.defaultPenalty?.let { attributeLevel + it }

    /**
     * Whether a skill controlled by [attr] may be bought with character points.
     *
     * Ch. 9, p. 85: *"Exception — SPI-based skills (Spiritual Intuition,
     * Spiritual Perception, Divination Arts) cannot be raised with character
     * points. They improve only through Sequence progression and pathway
     * bonuses."*
     *
     * The sentence names three skills and `skills.json` prints **exactly**
     * those three with `attr: "SPI"` -- 3 of 158. That exact match is what
     * licenses reading the rule as a property of the controlling attribute
     * rather than as a list of three special names, and `SkillLadderTest`
     * pins it: the standard 6ad set and 6v is why it has to be per-table.
     *
     * This is the same reading [Attr] already encodes -- SPI "starts at 0 and
     * cannot be raised by points", which is why it is not an [Attr] at all
     * (4.4). The skills that hang off it inherit that, and the book says so
     * out loud one chapter over. So a Beyonder gets them from potions and a
     * mortal does not get them, which is the book gating divination and the
     * spiritual senses on being a Beyonder -- explicitly, not merely by
     * arithmetic.
     */
    fun isBuyableWithPoints(attr: ControllingAttribute): Boolean = attr != ControllingAttribute.Spi
}
