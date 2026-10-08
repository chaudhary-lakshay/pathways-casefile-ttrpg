package com.pathways.rules

import com.pathways.model.Weapon

/**
 * What a hand parries with. Three are skills; [BARE_HANDS] is the other
 * operand of the Parry (Unarmed) formula cell, "Brawling or DX ÷ 2 + 3":
 * DX itself, which every character has.
 */
enum class UnarmedStyle { BRAWLING, BOXING, WRESTLING, BARE_HANDS }

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
 * Ch. 5, p. 37. An ARMED parry is a property of a readied weapon, not of a
 * character — a character carrying two weapons has two parry values, and
 * some weapons have none at all. A hand is the exception: its row prints DX
 * as an operand, so every character has one ([bareHandParry]).
 */
object ParryBlock {

    /**
     * A published SHEET's weapon row, whose cell prints its own modifier.
     *
     * **No production caller today, and that is a scope decision rather than
     * dead code.** None of the 23 sheets prints a weapon row, so this has
     * never had data; the weapons a player carries are chapter 8's catalogue
     * and are priced by [Weapons.parryFor], which takes no modifier because
     * the book prints none. Kept, with its formula pinned by a test, for the
     * provenance that does print one -- a sheet carrying a fencing blade at
     * +1 would otherwise have that number silently dropped.
     */
    fun parryFor(weapon: Weapon, skillLevel: Int): Int? =
        weapon.parryMod?.let { mod -> Math.floorDiv(skillLevel, 2) + 3 + mod }

    /** [skillLevel] is the style's skill, or DX itself for [UnarmedStyle.BARE_HANDS]. */
    fun unarmedParry(style: UnarmedStyle, skillLevel: Int): Int = when (style) {
        UnarmedStyle.BRAWLING, UnarmedStyle.BOXING, UnarmedStyle.BARE_HANDS ->
            Math.floorDiv(skillLevel, 2) + 3
        // The -3 sits inside the halving, so this is not the same as subtracting 3 at the end.
        UnarmedStyle.WRESTLING -> Math.floorDiv(skillLevel - 3, 2) + 3
    }

    /**
     * The parry of a hand with no skill behind it, or null where printing it
     * would tell the GM nothing (Ch. 5, p. 37).
     *
     * The Parry (Unarmed) row's formula cell reads "Brawling or DX ÷ 2 + 3",
     * and from 6g to 6ev this app read the first operand alone: a sheet with
     * no unarmed skill was told the screen "offers no parry from a default".
     * It is not one. The operand is DX, not Brawling's "Default: DX-2 if
     * untrained" (p. 38) halved -- where the book means a penalised operand
     * it prints it, as the Shield Bash's "Skill defaults to Shield/DX or
     * Brawling-2" does on the next page -- so Versatile's +1 "at default"
     * has nothing here to add to.
     *
     * **[brawling] does not withdraw it.** The cell says "or", not "only
     * without Brawling" (the design review's change), so the row is withheld
     * only where a held Brawling already parries at least as well: the two
     * rows would then differ in nothing, because [versusWeaponPenalty] and
     * [canParryRanged] answer the same for both. No premade through any
     * potion chain holds Brawling below DX today (2024 states, counted by a
     * throwaway test in 6ev), so the second row is offered on the cell's
     * word and not on a state seen: a granted level frozen under a DX that
     * a later potion raised further is the shape that would show it.
     */
    fun bareHandParry(dx: Int, brawling: Int?): Int? =
        unarmedParry(UnarmedStyle.BARE_HANDS, dx).takeIf { own ->
            brawling == null || unarmedParry(UnarmedStyle.BRAWLING, brawling) < own
        }

    /**
     * "Brawling parries weapons at -3." (p. 37)
     *
     * The Parry (Unarmed) row's Notes cell prints THREE rules and this codebase applied
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
     * **A bare hand at DX takes it too, and that is a READING.** The cell's
     * subject is "Brawling" and no line prints "unarmed parries weapons at
     * -3". DX takes it because it stands in Brawling's slot of the formula
     * and Boxing is the one exemption the book names; without it an
     * untrained hand at DX 10 would turn a knife at 8 beside a brawler's 5.
     * The screen says so beside the row.
     *
     * A SEPARATE penalty from [parryPenalty], because they compose: a second
     * parry, of a weapon, bare-handed, is -4 and -3.
     */
    const val VS_WEAPON_PENALTY = -3

    /** What [style] loses for parrying a weapon rather than a bare hand. */
    fun versusWeaponPenalty(style: UnarmedStyle): Int = when (style) {
        UnarmedStyle.BOXING -> 0
        // Each named rather than left to an `else`: a style added later must
        // be argued for here, not inherit the penalty by falling through.
        UnarmedStyle.BRAWLING, UnarmedStyle.WRESTLING, UnarmedStyle.BARE_HANDS -> VS_WEAPON_PENALTY
    }

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
     * refuses every unarmed style and a bare `false` would have to be
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
