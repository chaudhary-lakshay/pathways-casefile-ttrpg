package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.RitualComplexity
import com.pathways.rules.content.RitualRevelation
import com.pathways.rules.content.RitualWeight

/**
 * How long the caster took over it (the Time & Complexity table, ch. 9 III.B).
 *
 * [NORMAL] is the Base Time column, which prints no roll value -- taking the
 * time the book allots is neither rushed nor extended, and reading a modifier
 * into it would invent one.
 */
enum class RitualPacing { RUSHED, NORMAL, EXTENDED }

/**
 * Everything declared about a ritual's power sources before it is rolled
 * (Ch. 9 IV): "Declare your source before rolling -- it cannot be changed
 * once begun".
 *
 * [assistants] counts helpers, NOT the caster. [sacrifice] is the GM's value
 * for External Sacrifice/Catalysts. [invocation] is null when the caster prays
 * to themselves, and the GM's +1..+5 when an entity is invoked.
 */
data class PowerSourceDeclaration(
    val pacing: RitualPacing = RitualPacing.NORMAL,
    val assistants: Int = 0,
    val sacrifice: Int = 0,
    val suspended: Boolean = false,
    val invocation: Int? = null,
    /** The entity petitioned for revelations, or null for a ritual that petitions none. */
    val revelation: Revelation? = null,
)

/** A petition for revelations (v6.9i 4008): the entity, and the sacrifices made to it. */
data class Revelation(val entity: RitualRevelation, val sacrifices: Int = 0)

/**
 * Ritual power sources (Ch. 9 IV, v6.9i).
 *
 * Personal Spirituality and HP Sacrifice are FUEL; Multiple Participants,
 * External Sacrifice/Catalysts and Invocation are BONUSES to the roll.
 *
 * **HP sacrifice deliberately grants nothing to the roll.** "2 HP = 1 SPI
 * toward ritual cost. HP sacrifice does not grant a roll bonus -- it only
 * provides spiritual fuel." So [modifierTotal] never sees HP at all.
 *
 * **"Each assistant beyond the first" counts from the CASTER**: the owner's
 * ruling (2026-10-19). v6.9i deleted the worked Ritual 3 that used to settle
 * it ("Two trained assistants ... +2"), so the sentence alone now reads
 * either way and the ruling picks.
 *
 * **The numbers here are inside sentences, which is why they are here.**
 * Only the complexity ladder is a table, and that is committed content.
 */
object PowerSources {

    /** "2 HP = 1 SPI toward ritual cost." */
    const val HP_PER_SPI = 2

    /** What each assistant adds, and the ceiling on the lot of them. */
    const val PARTICIPANT_BONUS = 1
    const val PARTICIPANT_CAP = 3

    /** "Sacrifice quality ranges from -40 to +40 ... at GM's discretion." */
    const val SACRIFICE_LOWEST = -40
    const val SACRIFICE_HIGHEST = 40

    /** Invocation "Adds +1 to +5 to the ritual roll (GM discretion ...)". */
    const val INVOCATION_LOWEST = 1
    const val INVOCATION_HIGHEST = 5

    /**
     * "Orthodox deities grant +2 when the request aligns with their domain and
     * the caster is in good standing." The value inside the +1..+5 range, not
     * a second bonus on top: the owner's ruling (2026-10-19).
     */
    const val ORTHODOX_BONUS = 2

    /** "At GM discretion, suspending costs -2 to effective skill." */
    const val SUSPENSION_PENALTY = -2

    /** The four rungs, cheapest first. */
    fun complexities(): List<RitualComplexity> = ContentLoader.ritualComplexity()

    /** The rung named [name]; refused rather than defaulted. */
    fun revelations(): List<RitualRevelation> = ContentLoader.ritualRevelation()

    fun revelationNamed(name: String): RitualRevelation =
        revelations().firstOrNull { it.entity == name }
            ?: throw IllegalArgumentException("no revelation entity named '$name'")

    /**
     * The penalty petitioning [entity] puts on the Ritualistic Magic roll.
     * The Seven Lights' "-5 per sacrifice" reduces it, READ as reaching 0
     * and never a bonus. The deities' "-10 with Blessed by Deity" is not
     * applied: no advantage table prints that row, so no character holds it.
     */
    fun revelationPenalty(entity: RitualRevelation, sacrifices: Int): Int {
        require(sacrifices >= 0) { "sacrifices cannot be negative (was $sacrifices)" }
        val per = entity.perSacrifice ?: 0
        require(sacrifices == 0 || per > 0) { "${entity.entity} prints no reduction per sacrifice" }
        return minOf(0, entity.penalty + per * sacrifices)
    }

    fun complexityNamed(name: String): RitualComplexity =
        complexities().firstOrNull { it.complexity.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no ritual complexity named '$name'; the book prints " +
                    complexities().joinToString { it.complexity },
            )

    /** The time row printed on the same name as [weight]. */
    fun complexityFor(weight: RitualWeight): RitualComplexity = complexityNamed(weight.weight)

    /**
     * The SPI still owed after [hpSacrificed] HP is burned against a [cost].
     *
     * Floored, because the book buys SPI in whole units: 3 HP covers 1 SPI and
     * the odd point is spent for nothing. [Math.floorDiv], never `/`.
     *
     * Refused when the HP would cover more than the ritual costs, rather than
     * clamped: HP spent past the cost buys nothing.
     */
    fun spiOwed(cost: Int, hpSacrificed: Int = 0): Int {
        require(cost >= 0) { "a ritual cannot cost negative SPI; was $cost" }
        require(hpSacrificed >= 0) { "HP sacrificed cannot be negative; was $hpSacrificed" }
        val covered = Math.floorDiv(hpSacrificed, HP_PER_SPI)
        require(covered <= cost) {
            "$hpSacrificed HP covers $covered SPI, more than the $cost this ritual costs"
        }
        return cost - covered
    }

    /** The HP that would cover [spi] outright, at 2 HP apiece. */
    fun hpFor(spi: Int): Int {
        require(spi >= 0) { "cannot convert negative SPI to HP; was $spi" }
        return Math.multiplyExact(spi, HP_PER_SPI)
    }

    /** What [assistants] add to the roll, capped at [PARTICIPANT_CAP]. */
    fun participantBonus(assistants: Int): Int {
        require(assistants >= 0) { "a ritual cannot have negative assistants; was $assistants" }
        return minOf(assistants * PARTICIPANT_BONUS, PARTICIPANT_CAP)
    }

    /** The GM's sacrifice value, held to the printed -40..+40. */
    fun sacrificeBonus(chosen: Int): Int {
        require(chosen in SACRIFICE_LOWEST..SACRIFICE_HIGHEST) {
            "sacrifice quality ranges from $SACRIFICE_LOWEST to +$SACRIFICE_HIGHEST; " +
                "$chosen is outside that"
        }
        return chosen
    }

    /** The GM's invocation value, held to +1..+5; null (praying to oneself) is 0. */
    fun invocationBonus(chosen: Int?): Int {
        if (chosen == null) return 0
        require(chosen in INVOCATION_LOWEST..INVOCATION_HIGHEST) {
            "an invocation adds +$INVOCATION_LOWEST to +$INVOCATION_HIGHEST; $chosen is outside that"
        }
        return chosen
    }

    /** What [pacing] costs or pays at this [complexity]. */
    fun pacingModifier(complexity: RitualComplexity, pacing: RitualPacing): Int = when (pacing) {
        RitualPacing.RUSHED -> complexity.rushed
        RitualPacing.NORMAL -> 0
        RitualPacing.EXTENDED -> complexity.extended
    }

    /**
     * Everything [declaration] adds to the ritual roll.
     *
     * HP sacrifice is deliberately absent: it is fuel.
     *
     * [complexity] may be null -- a ritual whose time was never declared --
     * and then a non-normal pacing is REFUSED rather than dropped. Rushed is
     * -3 at one rung and -5 at another, so there is no safe value to assume.
     */
    fun modifierTotal(
        complexity: RitualComplexity?,
        declaration: PowerSourceDeclaration,
    ): Int {
        require(complexity != null || declaration.pacing == RitualPacing.NORMAL) {
            "a ${declaration.pacing.name.lowercase()} ritual has no modifier until its " +
                "complexity is declared; the column runs from -3 to -5 by rung"
        }
        return (complexity?.let { pacingModifier(it, declaration.pacing) } ?: 0) +
            participantBonus(declaration.assistants) +
            sacrificeBonus(declaration.sacrifice) +
            invocationBonus(declaration.invocation) +
            (if (declaration.suspended) SUSPENSION_PENALTY else 0) +
            (declaration.revelation?.let { revelationPenalty(it.entity, it.sacrifices) } ?: 0)
    }
}
