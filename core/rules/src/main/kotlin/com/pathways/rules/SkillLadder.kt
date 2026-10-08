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
     * mortal does not get them.
     *
     * ACQUIRING one, not attempting it: chapter 2's table gives every
     * non-Very-Hard skill an untrained default, and no sentence exempts these
     * ([SkillDefaults]). A mortal's SPI is 0, so theirs is a negative
     * target -- which is not a gate: [SuccessRoll] still gives a 3 or a 4 its
     * critical success.
     */
    fun isBuyableWithPoints(attr: ControllingAttribute): Boolean = attr != ControllingAttribute.Spi
}
