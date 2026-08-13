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

    /**
     * "Brawling parries weapons at -3." (p. 37)
     *
     * The Parry (Unarmed) row prints THREE rules and this codebase applied
     * one of them -- the cumulative penalty (6ap). This is the second: a bare
     * hand turning a knife is three points worse than turning a fist, and the
     * app showed one number for both.
     *
     * **Boxing is exempt and Wrestling is not.** The row says "Boxing uses
     * Boxing / 2 + 3 and ignores that penalty", and the skill section agrees
     * independently -- "Unlike Brawling, Boxing parries weapons without
     * penalty" (p. 38). Wrestling is named by neither exemption, and its own
     * -3 is a different rule in a different place (inside the halving,
     * always, weapon or not). Two printings agreeing is what licenses reading
     * the exemption as Boxing's alone.
     *
     * A SEPARATE penalty from [parryPenalty], because they compose: a second
     * parry, of a weapon, bare-handed, is -4 and -3.
     */
    const val VS_WEAPON_PENALTY = -3

    /** What [style] loses for parrying a weapon rather than a bare hand. */
    fun versusWeaponPenalty(style: UnarmedStyle): Int =
        if (style == UnarmedStyle.BOXING) 0 else VS_WEAPON_PENALTY

    /**
     * "Cannot parry ranged attacks." (p. 37)
     *
     * The third clause of the same cell, and a REFUSAL rather than a penalty
     * -- the distinction `Languages.ritualModifier` draws for a Broken
     * mystical tongue (6x). A number here would imply a parry exists to
     * modify.
     *
     * Scoped to the unarmed row that prints it. An armed parry is NOT
     * covered: chapter 8 gives no melee weapon a ranged defence rule, and
     * extending this sentence to weapons would author one. Wrestling's own
     * section corroborates the scope from the other side -- "Wrestlers can
     * parry MELEE attacks with their hands" (p. 38) -- which is what makes
     * this a rule about hands rather than about one row.
     *
     * A function taking the style rather than a constant, because the book
     * refuses all three unarmed styles and a bare `false` would have to be
     * re-read the day it prints an exception.
     */
    fun canParryRanged(style: UnarmedStyle): Boolean = false

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
