package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.Shield
import com.pathways.rules.content.ShieldRule
import com.pathways.rules.content.UnarmedAttack

/**
 * Where an attack is coming from, relative to the defender (Ch. 5, p. 37).
 *
 * The book's own three positions: the modifier list prints a penalty for the
 * side and for the rear, and the front is the unpenalised case it names by
 * printing nothing. A DECLARATION, like every other fact this app cannot
 * compute -- it holds no map and no facing, and the GM does (6bf).
 *
 * **The -2 and -4 are deliberately NOT applied**, and the reason is in
 * [ActiveDefenses.everyDefence]. This enum exists for the rule that IS printed
 * in the form this repo requires: [Shields.coverageAt].
 */
enum class Facing {
    FRONT,
    SIDE,
    REAR,
}

/** Whether a Readied shield's DB reaches the attack. See [Shields.coverageAt]. */
enum class ShieldCoverage {
    /** The book grants it: the attack comes from the front. */
    COUNTS,

    /**
     * The book neither grants nor withholds it. "Shield side" names a hand
     * this book never assigns to a character, so which flank a shield covers
     * is unprintable here -- the number is offered and the call is the GM's.
     */
    GM_DECIDES,

    /** The book withholds it: the rear is neither front nor shield side. */
    WITHHELD,
}

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
 * **"From the front or shield side" IS modelled, and only as far as the book
 * prints it.** This KDoc used to say "the book prints no arc to test against",
 * and so did `DefenseModifier`'s and `DefenseDialog.shielded`'s -- one excuse
 * in three places, all false. The book prints the arc two hundred lines above
 * this table ("Attacked from side: -2 to defense", "Attacked from rear: -4 to
 * defense"), in a list the defence dialog already renders.
 *
 * What is genuinely unprinted is narrower: WHICH side the shield covers. So
 * [coverageAt] answers in three states rather than two -- granted at the front,
 * withheld at the rear, and the GM's call from the side, where "shield side"
 * names a hand this book never assigns.
 */
object Shields {

    /**
     * The clause the Defense Bonus is conditional on, quoted not paraphrased.
     *
     * `extract_shields.PINNED` guards it. The two phrases beside it there pin
     * what the DB REACHES ("adds to all active defenses (Dodge, Parry, and
     * Block)"); this pins when it reaches nothing, and it had no guard at all
     * until the app began to withhold on it.
     */
    const val PRINTED_ARC = "against attacks from the front or shield side"

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

    /** The bash's damage, quoted: what [BASH] is read from. */
    const val PRINTED_BASH = "damage = thr-2 cr for light, thr-1 cr for medium, thr cr for heavy"

    /** The bash's to-hit, quoted: what [BASH_BRAWLING_PENALTY] is read from. */
    const val PRINTED_BASH_DEFAULT = "Skill defaults to Shield/DX or Brawling-2"

    /**
     * [PRINTED_BASH]'s three thrust modifiers, keyed like [BLOCK_PENALTY].
     * Improvised has none because the sentence names three types and not it:
     * no bash is offered for one, rather than a guessed number.
     */
    private val BASH = mapOf("Light" to -2, "Medium" to -1, "Heavy" to 0)

    private const val BASH_BRAWLING_PENALTY = -2

    /** The skill a shield blocks and bashes with. */
    const val SKILL = "Shield"

    /**
     * The Shield Bash as attack rows (p. 37), one per shield that prints a
     * damage: the Attack dialog has no shield in hand to read, so the player
     * picks which shield bashes, as Defend's shield chips do.
     *
     * Rolled on Shield at no modifier, or on an OWNED Brawling at -2, with
     * [defaultBonus] (Versatile's +1) because Brawling-2 is a default. An
     * untrained Brawling offers none: its DX-2 is itself a default, and the
     * untrained Shield row already rolls the table's DX-4.
     */
    fun bashesFor(skill: String, untrained: Boolean, defaultBonus: Int): List<UnarmedAttack> {
        val roll = when {
            skill.equals(SKILL, ignoreCase = true) -> 0
            skill.equals(SkillDefaults.BRAWLING, ignoreCase = true) && !untrained ->
                BASH_BRAWLING_PENALTY + defaultBonus
            else -> return emptyList()
        }
        return all().mapNotNull { shield ->
            val key = shield.type.substringBefore(" ")
            val modifier = BASH[key] ?: return@mapNotNull null
            UnarmedAttack(
                skill = skill,
                attack = "Shield Bash ($key)",
                rollModifier = roll,
                damage = if (modifier == 0) "thr cr" else "thr$modifier cr",
                notes = when {
                    roll == 0 -> shield.type
                    defaultBonus == 0 -> "${shield.type}; Brawling-2, a default"
                    else -> "${shield.type}; Brawling-2, a default, +$defaultBonus Versatile"
                },
                base = "thr",
                modifier = modifier,
                type = "cr",
            )
        }
    }

    /**
     * Whether [attack] puts a weapon in hand for [SkillDefaults.mastered]: a
     * bash on Shield does, being "an improvised weapon", so a Warrior's
     * "every weapon ... at effective DX" floors it. On Brawling it does not:
     * a floor under Brawling-2 prints DX-2, which the book gives no one, and
     * the Shield row already offers the master DX. Every attack on Shield is
     * a bash, so the skill is the whole test.
     */
    fun isWeaponInHand(attack: UnarmedAttack?): Boolean =
        attack != null && attack.skill.equals(SKILL, ignoreCase = true)

    /** The four printed shields, in the book's order. */
    fun all(): List<Shield> = ContentLoader.shields()

    /**
     * Whether a Readied shield's DB reaches an attack from [facing].
     *
     * Three states, because the clause draws two lines and this app can only
     * see one of them. Front and rear are decided by the sentence itself; the
     * side is not, and [ShieldCoverage.GM_DECIDES] says so rather than guessing
     * in either direction. Answering it as a plain Boolean would have to pick
     * one, and both picks are wrong half the time.
     */
    fun coverageAt(facing: Facing): ShieldCoverage = when (facing) {
        Facing.FRONT -> ShieldCoverage.COUNTS
        Facing.SIDE -> ShieldCoverage.GM_DECIDES
        Facing.REAR -> ShieldCoverage.WITHHELD
    }

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
