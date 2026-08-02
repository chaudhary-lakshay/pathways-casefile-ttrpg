package com.pathways.rules

import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.RitualCritical
import com.pathways.rules.content.RitualFailure
import com.pathways.rules.content.RitualPreparation
import com.pathways.rules.content.RitualWeight

/**
 * A ritual attempt and everything the book charges for it.
 *
 * [marginOfFailure] is 0 on a success. [critical] is filled only when the
 * Critical Failure Table was actually consulted -- a failure by 1-9 costs
 * something without ever reaching it.
 */
data class RitualOutcome(
    val roll: RollResult,
    val target: Int,
    val marginOfFailure: Int,
    val consequence: RitualFailure? = null,
    val criticalRoll: Int? = null,
    val critical: RitualCritical? = null,
) {
    val succeeded: Boolean get() = consequence == null
}

/**
 * Ritual resolution (Ch. 7, p. 67-68).
 *
 * "Failure always costs. You spent the time, materials, and SPI or HP. The
 * question is only how badly it went wrong."
 *
 * Three inputs make the target: the caster's skill, the ritual's weight
 * (Light +0, Moderate -2, Heavy -4), and their corruption -- this is
 * [Corruption.ritualPenalty]'s first production caller, and the reason that
 * penalty is ritual-scoped rather than general.
 *
 * **Two ways into the Critical Failure Table, and they are not the same.**
 * The book names one row by the ROLL ("Critical Failure (roll 17-18)") and
 * one by the MARGIN ("Failure by 10+... with -2 to the result roll"). Under
 * GURPS a failure by 10 or more is already a critical failure, so the two
 * overlap -- an 18 against a low target is both. The row named for the roll
 * wins, because that is the condition the book actually printed on it, and it
 * carries no -2. Same reading as the Potion Consumption Roll: specific beats
 * general where the specific rule speaks.
 */
object RitualResolution {

    /** p. 67: a severe misfire rolls the table "with -2 to the result roll". */
    const val SEVERE_MISFIRE_SHIFT = -2

    /** The table is 3d6, so nothing outside this can be looked up. */
    const val LOWEST_CRITICAL_ROLL = 3
    const val HIGHEST_CRITICAL_ROLL = 18

    /** GURPS calls 17 and 18 a critical failure whatever the target. */
    private const val CRITICAL_ROLL_FLOOR = 17

    fun weights(): List<RitualWeight> = ContentLoader.ritualWeights()

    /** The Phase I preparation qualities, best first (p. 63). */
    fun preparations(): List<RitualPreparation> = ContentLoader.ritualPreparation()

    /** The preparation quality named [quality]; refused rather than defaulted. */
    fun preparationNamed(quality: String): RitualPreparation =
        preparations().firstOrNull { it.quality.equals(quality, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no preparation quality named '$quality'; the book prints " +
                    preparations().joinToString { it.quality },
            )

    /**
     * What [weight] costs in SPI when the GM picks [chosen] inside its cell.
     *
     * Every weight prints a cost -- 2, "4-6", "8+" -- and the app charged none
     * of them until this slice, which made a ritual something a caster could
     * repeat forever. Two of the three cells are not a single number, so the
     * end is the GM's; a value outside what the book prints is refused rather
     * than clamped, because a clamp would quietly charge a different ritual.
     */
    fun spiFor(weight: RitualWeight, chosen: Int = weight.spi, burned: Int = 0): Int {
        require(burned >= 0) { "SPI burned cannot be negative; was $burned" }
        // Null, not Int.MAX_VALUE: an open cell has no ceiling to compare
        // against, and using the largest Int as one let chosen + burned wrap
        // to a NEGATIVE cost -- which spend() would have paid back as healing
        // rather than charged (Codex).
        val most = if (weight.spiOpen) null else weight.spiMax ?: weight.spi
        require(chosen >= weight.spi && (most == null || chosen <= most)) {
            "a ${weight.weight} ritual costs ${weight.printedCost}; $chosen is outside that"
        }
        return try {
            Math.addExact(chosen, burned)
        } catch (overflow: ArithmeticException) {
            throw IllegalArgumentException("$chosen SPI plus $burned burned does not fit", overflow)
        }
    }

    /**
     * The number to roll under: skill, less the ritual's weight, less the
     * spiritual instability of [cor], plus how well it was [preparation]ed.
     *
     * Preparation is null for the quick mode, which the book resolves without
     * Phase I at all. Where it is given, [RitualPreparation.modifier] is the
     * end of its cell nearer zero -- the gentle reading the darkness row and
     * the fright triggers already settled.
     */
    fun targetFor(
        skillLevel: Int,
        weight: RitualWeight,
        cor: Int,
        preparation: RitualPreparation? = null,
    ): Int = skillLevel + weight.modifier + Corruption.ritualPenalty(cor) +
        (preparation?.modifier ?: 0)

    /**
     * Attempt the ritual, and resolve the consequence if it fails.
     *
     * Rolls once for the ritual and, only for a severe misfire or a rolled
     * 17-18, a second time on the Critical Failure Table.
     */
    fun attempt(
        skillLevel: Int,
        weight: RitualWeight,
        cor: Int,
        source: RandomSource,
        preparation: RitualPreparation? = null,
        failures: List<RitualFailure> = ContentLoader.ritualFailures(),
        criticals: List<RitualCritical> = ContentLoader.ritualCriticals(),
    ): RitualOutcome {
        val target = targetFor(skillLevel, weight, cor, preparation)
        val roll = SuccessRoll.against(target, source)
        // By outcome, not by margin. A 17 or 18 fails whatever the target, and
        // against a target of 18 its margin is not even negative -- reading
        // the margin alone would report the book's own critical-failure row as
        // a success.
        if (roll.outcome == Outcome.SUCCESS || roll.outcome == Outcome.CRITICAL_SUCCESS) {
            return RitualOutcome(roll = roll, target = target, marginOfFailure = 0)
        }

        // A rolled 17-18 against a generous target overshoots by nothing; the
        // row it lands on is named for the roll, so no margin is needed.
        val margin = (roll.total - target).coerceAtLeast(0)
        val rolledCritical = roll.total >= CRITICAL_ROLL_FLOOR
        val consequence =
            if (rolledCritical) failures.single { it.critical } else failureFor(margin, failures)

        if (!rolledCritical && !consequence.reachesTheTable) {
            return RitualOutcome(
                roll = roll,
                target = target,
                marginOfFailure = margin,
                consequence = consequence,
            )
        }

        // The shift belongs to the by-margin row only; the 17-18 row says
        // "immediately", with no adjustment. A 3 shifted by -2 would fall off
        // the bottom of a 3d6 table, so the lookup is clamped to it.
        val shift = if (rolledCritical) 0 else SEVERE_MISFIRE_SHIFT
        val criticalRoll = (rollThreeDSix(source) + shift)
            .coerceIn(LOWEST_CRITICAL_ROLL, HIGHEST_CRITICAL_ROLL)
        return RitualOutcome(
            roll = roll,
            target = target,
            marginOfFailure = margin,
            consequence = consequence,
            criticalRoll = criticalRoll,
            critical = criticalFor(criticalRoll, criticals),
        )
    }

    /** The consequence row a failure by [margin] lands on. */
    fun failureFor(
        margin: Int,
        failures: List<RitualFailure> = ContentLoader.ritualFailures(),
    ): RitualFailure {
        require(margin >= 1) { "a failure is by at least 1; was $margin" }
        return failures.lastOrNull {
            !it.critical && margin >= (it.from ?: 0) && (it.to == null || margin <= it.to)
        } ?: error("no ritual failure row covers a margin of $margin")
    }

    /** The Critical Failure Table row [roll] lands on. */
    fun criticalFor(
        roll: Int,
        criticals: List<RitualCritical> = ContentLoader.ritualCriticals(),
    ): RitualCritical {
        require(roll in LOWEST_CRITICAL_ROLL..HIGHEST_CRITICAL_ROLL) {
            "the Critical Failure Table is 3d6; was $roll"
        }
        return criticals.single { roll >= it.low && roll <= it.high }
    }

    /**
     * Whether this row sends the caster to the Critical Failure Table. Read
     * off the printed text rather than hardcoded to a band, so a change to
     * the content shows up here instead of being quietly ignored.
     */
    private val RitualFailure.reachesTheTable: Boolean
        get() = "Critical Failure Table" in consequence

    private fun rollThreeDSix(source: RandomSource) = (1..3).sumOf { source.roll(6) }
}
