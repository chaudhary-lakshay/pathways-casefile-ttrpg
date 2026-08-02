package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.Shield
import com.pathways.rules.content.ShieldRule

/**
 * Carrying a shield (Ch. 5, p. 37).
 *
 * [ParryBlock.blockFor] has taken a `shieldDb` since M0 with nothing able to
 * supply one -- the defense dialog printed "+ shield DB" and left the number
 * to the GM. This is that number.
 *
 * **The Defense Bonus is not a Block bonus.** "While Readied, its DB (Defense
 * Bonus) adds to all active defenses (Dodge, Parry, and Block) against attacks
 * from the front or shield side." Block is the only defence whose FORMULA
 * mentions a shield, which makes applying the DB there alone the easy wrong
 * reading -- and it would understate a shielded character's Dodge by up to 3.
 *
 * "From the front or shield side" is not modelled: facing is the GM's to
 * adjudicate and the book prints no arc to test against. The bonus is offered,
 * and the note travels with it.
 */
object Shields {

    /**
     * The Block penalties printed in the Notes column, keyed by the first word
     * of the shield's type.
     *
     * In Kotlin rather than in the content file for the reason the maneuver
     * table settled: these are numbers inside prose ("-1 to Block vs. ranged"),
     * and a parser reading integers out of note text at render time is a
     * parser nobody tests. `extract_shields.py`'s verify() pins the sentences
     * they were read from, and `ShieldsTest` pins that the cells still say so.
     */
    private val BLOCK_PENALTY = mapOf("Light" to -1, "Improvised" to -2)

    /** Light's penalty is "vs. ranged"; Improvised's names no scope at all. */
    private val RANGED_ONLY = setOf("Light")

    /** The four printed shields, in the book's order. */
    fun all(): List<Shield> = ContentLoader.shields()

    /** The bullets under the table: breaking, cover, and the shield bash. */
    fun rules(): List<ShieldRule> = ContentLoader.shieldRules()

    /**
     * The shield whose printed type starts with [type] ("Medium").
     *
     * Refused rather than defaulted: a shield the book does not print is a
     * caller bug, and answering with a bare hand would silently drop a bonus.
     */
    fun named(type: String): Shield =
        all().firstOrNull { it.type.startsWith(type, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no shield type starts with '$type'; the book prints " +
                    all().joinToString { it.type },
            )

    /** [defense] with [shield]'s DB added, or unchanged when there is none. */
    fun withBonus(defense: Int, shield: Shield?): Int = defense + (shield?.db ?: 0)

    /**
     * What [shield] costs its owner's Block, and whether that cost applies.
     *
     * Two rows carry one and they are not the same rule: a light shield is
     * "-1 to Block vs. ranged" and an improvised one is "-2 to Block" with no
     * scope named, so it applies in reach too. Same shape as Spiritual
     * Attrition and the prone target's separate melee value -- the scope
     * travels with the number or the number gets misapplied.
     */
    fun blockPenalty(shield: Shield, isRanged: Boolean): Int {
        val key = shield.type.substringBefore(" ")
        val penalty = BLOCK_PENALTY[key] ?: return 0
        return if (key in RANGED_ONLY && !isRanged) 0 else penalty
    }

    /** Block behind [shield]: Shield/2 + 3, its DB, and its own penalty. */
    fun blockFor(shield: Shield, shieldSkillLevel: Int, isRanged: Boolean = false): Int =
        ParryBlock.blockFor(shieldSkillLevel, shield.db) + blockPenalty(shield, isRanged)
}
