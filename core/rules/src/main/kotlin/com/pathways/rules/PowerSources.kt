package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.RitualComplexity
import com.pathways.rules.content.SacrificeTier

/**
 * How long the caster took over it (the Time & Complexity Reference, p. 65).
 *
 * [NORMAL] is the Base Time column, which prints no roll value -- taking the
 * time the book allots is neither rushed nor extended, and reading a modifier
 * into it would invent one.
 */
enum class RitualPacing { RUSHED, NORMAL, EXTENDED }

/**
 * Everything declared about a ritual's power sources before it is rolled
 * (Ch. 7, Section IV.B-E, p. 66).
 *
 * One object rather than five more parameters on [RitualResolution.targetFor],
 * which already takes eight. "Before rolling, you must declare where the
 * energy comes from. This choice cannot be changed once the ritual has begun."
 *
 * [assistants] counts helpers, NOT the caster. [sentientBeings] is meaningful
 * only alongside a [sacrifice], and is refused without one.
 */
data class PowerSourceDeclaration(
    val pacing: RitualPacing = RitualPacing.NORMAL,
    val assistants: Int = 0,
    val sacrifice: SacrificeTier? = null,
    val sentientBeings: Int = 0,
    val catalyst: Int = 0,
    val suspended: Boolean = false,
)

/**
 * Ritual power sources (Ch. 7, Section IV, p. 65-66).
 *
 * Five ways to fuel a ritual, and only three of them touch the roll.
 * A. Personal Spirituality and B. HP Sacrifice are FUEL; C. Multiple
 * Participants, D. External Sacrifice and E. Catalysts are BONUSES;
 * F. Invocation is its own table ([Invocation]).
 *
 * **HP sacrifice deliberately grants nothing to the roll.** "Every 2 HP spent
 * counts as 1 SPI toward the ritual cost" is followed immediately by "HP
 * sacrifice does not grant a bonus to the roll -- it only provides spiritual
 * fuel." A fuel rule sitting one line above a list of roll modifiers is
 * exactly the shape that gets read as a bonus, so [modifierTotal] never sees
 * HP at all and `extract_power_sources.verify()` pins that sentence.
 *
 * **"Each assistant beyond the first" counts from the CASTER.** Read as
 * "beyond the first assistant", two helpers would be worth +1. The book's own
 * Ritual 3 prints "Two trained assistants ... +2", so every assistant counts
 * and the caster is the first participant. The sentence alone does not settle
 * it; the worked example does, and the extractor pins that row.
 *
 * **The numbers here are inside sentences, which is why they are here.** Only
 * the complexity ladder and the three sacrifice tiers are real tables, and
 * those are committed content. A column for +1-per-assistant or +0-to-+2 for a
 * catalyst would make the book look like it printed a table it never did --
 * the split [RitualMode.SUPPORT_CAP], [Invocation.ORTHODOX_SHIFT] and the
 * maneuver table's +4/-4 all take.
 */
object PowerSources {

    /** B: "Every 2 HP spent counts as 1 SPI toward the ritual cost." */
    const val HP_PER_SPI = 2

    /** C: what each assistant adds, and the ceiling on the lot of them. */
    const val PARTICIPANT_BONUS = 1
    const val PARTICIPANT_CAP = 3

    /** D: "Living sacrifice adds +2 per sentient being offered." */
    const val SENTIENT_BONUS = 2

    /** E: "A catalyst typically provides +0 to +2 ... at the GM's discretion." */
    const val CATALYST_LOWEST = 0
    const val CATALYST_HIGHEST = 2

    /** "At the GM's discretion, suspending a ritual costs -2 to the effective skill." */
    const val SUSPENSION_PENALTY = -2

    /** The four rungs, cheapest first. */
    fun complexities(): List<RitualComplexity> = ContentLoader.ritualComplexity()

    /** The rung named [name]; refused rather than defaulted. */
    fun complexityNamed(name: String): RitualComplexity =
        complexities().firstOrNull { it.complexity.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no ritual complexity named '$name'; the book prints " +
                    complexities().joinToString { it.complexity },
            )

    /** The three External Sacrifice tiers, cheapest first. */
    fun sacrificeTiers(): List<SacrificeTier> = ContentLoader.ritualSacrifice()

    /** The tier named [name]; refused rather than defaulted. */
    fun sacrificeNamed(name: String): SacrificeTier =
        sacrificeTiers().firstOrNull { it.tier.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no sacrifice tier named '$name'; the book prints " +
                    sacrificeTiers().joinToString { it.tier },
            )

    /**
     * What [complexity] costs in SPI when the GM picks [chosen] inside its
     * cell, plus any [burned] on top.
     *
     * Burning is a SECOND use of SPI, not a cheaper first one: "you may spend
     * additional SPI after seeing your roll to boost it (+1 per SPI, no cap)
     * ... The two uses of SPI are cumulative."
     *
     * A value outside the printed cell is refused rather than clamped -- a
     * clamp charges a different ritual than the one described. The open TOP on
     * "11-15+" is a null ceiling rather than [Int.MAX_VALUE], because using
     * the largest Int as one let `chosen + burned` wrap NEGATIVE, and a
     * negative cost is paid back as healing (Codex, on the ritual slice).
     */
    fun spiFor(
        complexity: RitualComplexity,
        chosen: Int = complexity.spi,
        burned: Int = 0,
    ): Int {
        require(burned >= 0) { "SPI burned cannot be negative; was $burned" }
        val most = if (complexity.spiOpen) null else complexity.spiMax ?: complexity.spi
        require(chosen >= complexity.spi && (most == null || chosen <= most)) {
            "a ${complexity.complexity} ritual costs ${complexity.printedCost}; " +
                "$chosen is outside that"
        }
        return try {
            Math.addExact(chosen, burned)
        } catch (overflow: ArithmeticException) {
            throw IllegalArgumentException("$chosen SPI plus $burned burned does not fit", overflow)
        }
    }

    /**
     * The SPI still owed after [hpSacrificed] HP is burned against a [cost].
     *
     * Floored, because the book buys SPI in whole units: 3 HP covers 1 SPI and
     * the odd point is spent for nothing. That waste is the book's -- "every 2
     * HP spent counts as 1 SPI" -- and rounding it up would hand out a free
     * point of fuel. [Math.floorDiv], never `/`: this codebase has had the
     * truncation defect three times.
     *
     * Refused when the HP would cover more than the ritual costs, rather than
     * clamped: HP spent past the cost buys nothing, and a caller asking for it
     * has asked for something the book does not sell.
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

    /**
     * C: what [assistants] add to the roll, capped at [PARTICIPANT_CAP].
     *
     * Every assistant counts -- see this object's KDoc for why, and for the
     * worked example that settles it.
     */
    fun participantBonus(assistants: Int): Int {
        require(assistants >= 0) { "a ritual cannot have negative assistants; was $assistants" }
        return minOf(assistants * PARTICIPANT_BONUS, PARTICIPANT_CAP)
    }

    /**
     * D: the tier's own value plus [SENTIENT_BONUS] per living offering.
     *
     * The two ADD. Precious names "sentient sacrifices" among its examples and
     * the next sentence still says "Living sacrifice adds +2 per sentient
     * being offered", so one sentient being on a Precious altar is +4 and +2.
     * That is the literal reading of two consecutive sentences; excluding one
     * from the other would need a clause the book does not print.
     *
     * [sentientBeings] without a [tier] is refused rather than ignored -- a
     * modifier naming nothing chosen is the stale-value shape the boon dialog
     * already had to be fixed for.
     */
    fun sacrificeBonus(tier: SacrificeTier?, sentientBeings: Int = 0): Int {
        require(sentientBeings >= 0) {
            "cannot offer negative sentient beings; was $sentientBeings"
        }
        require(tier != null || sentientBeings == 0) {
            "$sentientBeings sentient beings were offered with no sacrifice declared; " +
                "the per-being bonus rides on an External Sacrifice"
        }
        if (tier == null) return 0
        return tier.modifier + sentientBeings * SENTIENT_BONUS
    }

    /** E: the GM's value inside "+0 to +2", validated against both ends. */
    fun catalystBonus(chosen: Int): Int {
        require(chosen in CATALYST_LOWEST..CATALYST_HIGHEST) {
            "a catalyst provides +$CATALYST_LOWEST to +$CATALYST_HIGHEST; $chosen is outside that"
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
     * HP sacrifice is deliberately absent: it is fuel, and the book says so in
     * its own sentence. See this object's KDoc.
     *
     * [complexity] may be null -- a ritual whose scope was never declared --
     * and then a non-normal pacing is REFUSED rather than dropped. Rushed is
     * -3 at one rung and -5 at another, so there is no safe value to assume,
     * and silently ignoring it would leave a GM believing the rush was priced.
     * The other four sources do not depend on the rung and still apply.
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
            sacrificeBonus(declaration.sacrifice, declaration.sentientBeings) +
            catalystBonus(declaration.catalyst) +
            (if (declaration.suspended) SUSPENSION_PENALTY else 0)
    }
}
