package com.pathways.rules

import com.pathways.model.Weapon

enum class UnarmedStyle { BRAWLING, BOXING, WRESTLING }

/**
 * Which cumulative penalty a parry accumulates, Ch. 5 p. 37.
 *
 * The book prints three tiers and **chapter 8 arms only one of them**: its
 * nine weapons include no fencing weapon and no rapier, so [FENCING] and
 * [RAPIER] are printed rules with nothing in this book that can reach them --
 * the same shape as `Guns (Pistol)`, a skill the weapon table never arms
 * (6t). Modelled anyway, because the tier is the book's and a GM who brings a
 * smallsword should not be silently charged the broadsword's penalty.
 */
enum class ParryClass(val perExtraParry: Int) {
    /** Every weapon chapter 8 prints, and every unarmed style. */
    ORDINARY(-4),
    FENCING(-2),
    RAPIER(-1),
}

/**
 * Ch. 5, p. 37. Parry is a property of a readied weapon, not of a
 * character — a character carrying two weapons has two parry values, and
 * some weapons have none at all.
 */
object ParryBlock {

    fun parryFor(weapon: Weapon, skillLevel: Int): Int? =
        weapon.parryMod?.let { mod -> Math.floorDiv(skillLevel, 2) + 3 + mod }

    fun unarmedParry(style: UnarmedStyle, skillLevel: Int): Int = when (style) {
        UnarmedStyle.BRAWLING, UnarmedStyle.BOXING -> Math.floorDiv(skillLevel, 2) + 3
        // The -3 sits inside the halving, so this is not the same as subtracting 3 at the end.
        UnarmedStyle.WRESTLING -> Math.floorDiv(skillLevel - 3, 2) + 3
    }

    fun blockFor(shieldSkillLevel: Int, shieldDb: Int): Int = Math.floorDiv(shieldSkillLevel, 2) + 3 + shieldDb

    /** "Once per round. Add shield's DB to your Block score." (p. 37) */
    const val BLOCKS_PER_ROUND = 1

    /**
     * What the parry after [priorParries] others this round is worth.
     *
     * "Each type may be used multiple times per round, but Parry accumulates
     * a -4 cumulative penalty **after the first** (-2 for fencing weapons)",
     * and the table's own Notes cell says "Cumulative -4 per additional parry
     * (-2 fencing, -1 rapier)". Two printings, and they agree: the FIRST
     * parry of a round is free and each one after it costs another tier.
     *
     * The rule was committed in `defenses.json`'s prose and shown to the GM
     * from 6g onward, and nothing ever applied it -- the class 6h, 6m and 6ai
     * each found. Applied here rather than in the screen, so a caller cannot
     * hold a different opinion about which parry is the free one.
     *
     * Cumulative, not a flat penalty on every parry past the first: a third
     * parry is -8, which is what "cumulative" says and what a naive reading
     * of "-4 per additional parry" also gives. Both printings agree, so there
     * is nothing here to record as a disagreement.
     *
     * Block is deliberately NOT covered: its row prints "Once per round",
     * which is a limit and not a penalty -- see [BLOCKS_PER_ROUND]. Dodge is
     * "May be used repeatedly" with no penalty at all, so the scope of this
     * function is parries, and it says so by taking no defence parameter.
     */
    fun parryPenalty(priorParries: Int, weaponClass: ParryClass = ParryClass.ORDINARY): Int {
        require(priorParries >= 0) {
            "a parry cannot follow $priorParries earlier ones"
        }
        return priorParries * weaponClass.perExtraParry
    }
}
