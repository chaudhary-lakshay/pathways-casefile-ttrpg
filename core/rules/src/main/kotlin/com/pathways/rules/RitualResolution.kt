package com.pathways.rules

import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.LanguageProficiency
import com.pathways.rules.content.RitualComplexity
import com.pathways.rules.content.RitualCritical
import com.pathways.rules.content.RitualFailure
import com.pathways.rules.content.RitualPreparation
import com.pathways.rules.content.RitualSuccess
import com.pathways.rules.content.RitualWeight
import com.pathways.rules.content.TargetLink

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
    /**
     * How WELL it worked, filled only on a success -- Step 4's grade (p. 62).
     *
     * The mirror of [consequence], and the reason the two are separate
     * fields rather than one graded band: a caller asking what happened must
     * not be able to read a success row off a failed ritual, the slip
     * `SkillRollOutcome.degree` avoids one object over (6ad).
     */
    val success: RitualSuccess? = null,
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

    /** "1 SPI = +1, no cap", spent after seeing the result (p. 63, p. 66). */
    const val BURN_PER_SPI = 1

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
     *
     * **The app no longer charges from here.** Section IV.A prints a second,
     * fuller SPI table on the COMPLEXITY axis, and the book's own six worked
     * rituals follow that one -- three of them fall outside these cells. Fuel
     * comes from [PowerSources.spiFor] now. This reader stays because Section
     * V's column is genuinely printed and refusing to read it would be its own
     * kind of repair; see [com.pathways.rules.content.RitualComplexity].
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
     * Preparation may be null, which is simply "not described". Where it is
     * given and its cell prints a span, [preparationValue] is the GM's pick
     * inside it and [linkValue] the same for [link]; unpicked, each takes the
     * end nearer zero. See [RitualMode.valueFor] -- and note that this
     * paragraph used to call the near end "the gentle reading the darkness
     * row and the fright triggers already settled", which neither had.
     *
     * **CORRECTION.** This KDoc used to say "preparation is null for the quick
     * mode, which the book resolves without Phase I at all". That is wrong:
     * the Quick Ritual prints its OWN preparation table (p. 62) whose
     * Excellent is +2 where Phase I's is +3. [mode] picks between them, and
     * defaults to [RitualMode.FULL] because that is the table every caller was
     * already passing rows from.
     *
     * [link] is the Quick Ritual's Target Link and belongs to that mode alone
     * -- a Full ritual's preparation row already priced one, so passing both
     * is refused rather than summed. See [RitualMode].
     *
     * [flourish] is Phase II's mirror of it: "The GM may award a Flourish
     * Bonus (+1) for good in-character delivery", printed for the Full ritual
     * alone and refused for a Quick one. It defaults to false, which leaves
     * the target exactly where it was before Phase II was read -- and false is
     * the honest default, because the bonus is something a GM AWARDS rather
     * than something a caster has.
     *
     * [complexity] and [powerSources] are Section IV's declaration (p. 65-66):
     * how long the caster took, who helped, what was sacrificed, what was
     * burned as a catalyst, and whether the work was suspended part-way. They
     * default to "nothing declared", which leaves the target exactly where it
     * was before that section was read. HP sacrifice is NOT among them -- it
     * is fuel, and the book says in its own sentence that it "does not grant a
     * bonus to the roll". See [PowerSources].
     *
     * [complexity] also carries the ritual's SPI cost, on a different axis
     * from [weight]: the weight is the CATEGORY and gives the modifier above,
     * the complexity is the SCOPE and gives the fuel. See
     * [com.pathways.rules.content.RitualComplexity] for why they are not one
     * table, and for the three worked rituals that settle it.
     *
     * [language] is the mystical proficiency the ritual is spoken in (Ch. 3,
     * p. 14). Null leaves the target exactly where it was before that table
     * was read, which is what every caller that does not ask for a language
     * still gets. A proficiency that cannot cast -- Broken, or any common
     * language -- is REFUSED by [Languages.ritualModifier] rather than rolled
     * at a penalty: the book forbids the attempt, and a roll at -4 is not the
     * same answer as no roll.
     */
    fun targetFor(
        skillLevel: Int,
        weight: RitualWeight,
        cor: Int,
        preparation: RitualPreparation? = null,
        language: LanguageProficiency? = null,
        mode: RitualMode = RitualMode.FULL,
        link: TargetLink? = null,
        flourish: Boolean = false,
        support: List<String> = emptyList(),
        complexity: RitualComplexity? = null,
        powerSources: PowerSourceDeclaration = PowerSourceDeclaration(),
        preparationValue: Int? = null,
        linkValue: Int? = null,
    ): Int = skillLevel + weight.modifier + Corruption.ritualPenalty(cor) +
        mode.modifierTotal(
            preparation, link, flourish,
            preparationValue = preparationValue, linkValue = linkValue,
        ) +
        RitualMode.supportBonus(support) +
        Languages.ritualModifier(language) +
        PowerSources.modifierTotal(complexity, powerSources)

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
        language: LanguageProficiency? = null,
        mode: RitualMode = RitualMode.FULL,
        link: TargetLink? = null,
        flourish: Boolean = false,
        support: List<String> = emptyList(),
        complexity: RitualComplexity? = null,
        powerSources: PowerSourceDeclaration = PowerSourceDeclaration(),
        failures: List<RitualFailure> = ContentLoader.ritualFailures(),
        criticals: List<RitualCritical> = ContentLoader.ritualCriticals(),
        successBands: List<RitualSuccess> = ContentLoader.ritualSuccess(),
        preparationValue: Int? = null,
        linkValue: Int? = null,
    ): RitualOutcome = rollAgainst(
        targetFor(
            skillLevel, weight, cor, preparation, language, mode, link, flourish,
            support, complexity, powerSources,
            preparationValue = preparationValue, linkValue = linkValue,
        ),
        source, failures, criticals, successBands,
    )

    /**
     * The roll itself, against a [target] already composed by [targetFor].
     *
     * A screen that prints "3d6 <= N" and then rolls must roll against N.
     * Handing [attempt] the same inputs a second time is two call sites
     * answering one question, and the second runs in a click handler: it can
     * read a row, a mode or a complexity the printed number never saw, and
     * [targetFor] refuses the mismatch by throwing (6ef, 6eg).
     *
     * [target] is not checked for where it came from. [classify] needs the
     * dice and the number to agree, not the number's provenance, and [pushed]
     * already re-reads a roll against a target no [targetFor] returned.
     */
    fun rollAgainst(
        target: Int,
        source: RandomSource,
        failures: List<RitualFailure> = ContentLoader.ritualFailures(),
        criticals: List<RitualCritical> = ContentLoader.ritualCriticals(),
        successBands: List<RitualSuccess> = ContentLoader.ritualSuccess(),
    ): RitualOutcome {
        val roll = SuccessRoll.against(target, source)
        return classify(roll, target, failures, criticals, successBands) { shift ->
            // The shift belongs to the by-margin row only; the 17-18 row says
            // "immediately", with no adjustment. A 3 shifted by -2 would fall
            // off the bottom of a 3d6 table, so the lookup is clamped to it.
            (rollThreeDSix(source) + shift)
                .coerceIn(LOWEST_CRITICAL_ROLL, HIGHEST_CRITICAL_ROLL)
        }
    }

    /**
     * Whether a burn can still move [outcome] (p. 63, p. 66).
     *
     * False for a rolled 17 or 18. That row is named for the ROLL and not for
     * the margin -- "Critical Failure (17-18)" is taken against any target
     * however high, and it carries no -2 to adjust. Nothing a caster spends
     * there buys anything, which is the arithmetic [EffortBurn.canPush]
     * refuses a critical-only rule on rather than a reading of the sentence.
     * Every other result is pushable, a success included: Step 4 grades that
     * by margin, so a point still buys a better grade.
     */
    fun canPush(outcome: RitualOutcome): Boolean =
        outcome.roll.total < CRITICAL_ROLL_FLOOR

    /**
     * The same dice read against a target [points] SPI higher (p. 63, p. 66).
     *
     * "You may spend additional SPI (1 SPI = +1, no cap) after seeing your
     * result, separate from the ritual's fuel cost." Separate is the load-
     * bearing word: [spiFor] takes the same points as COST and this takes
     * them as the bonus, so the two halves of one spend live apart. The dice
     * are never rolled again -- "after seeing your result" is the whole reason
     * this is a second step rather than a modifier.
     *
     * **The Critical Failure Table's roll is a second sample, not derived
     * data.** It is kept where the pushed outcome still reaches the table and
     * dropped where it no longer does: rolling it again would hand the caster
     * a fresh chance at a milder row the book never offered, and a caster who
     * pushes OFF the table must not be left holding a row -- nor the CoR that
     * row charges. A push can only shrink the margin, so it can never need a
     * sample that was not already taken, and that is asserted rather than
     * quietly handled.
     *
     * Returns [outcome] unchanged when [canPush] is false, so a caller that
     * forgets to ask cannot spend SPI on a result the burn cannot move.
     */
    fun pushed(
        outcome: RitualOutcome,
        points: Int,
        failures: List<RitualFailure> = ContentLoader.ritualFailures(),
        criticals: List<RitualCritical> = ContentLoader.ritualCriticals(),
        successBands: List<RitualSuccess> = ContentLoader.ritualSuccess(),
    ): RitualOutcome {
        require(points >= 0) { "cannot burn a negative number of SPI; was $points" }
        if (points == 0 || !canPush(outcome)) return outcome
        val target = Math.addExact(outcome.target, points * BURN_PER_SPI)
        val roll = SuccessRoll.evaluate(outcome.roll.total, target)
        return classify(roll, target, failures, criticals, successBands) {
            requireNotNull(outcome.criticalRoll) {
                "a push shrinks the margin, so it cannot reach a Critical " +
                    "Failure Table the unpushed ritual missed"
            }
        }
    }

    /**
     * What a known total against a known target means, in one place.
     *
     * [attempt] and [pushed] ask the same question of the same three tables
     * and must not answer it twice. [tableRoll] is the only thing they differ
     * on, and it takes the shift so that the caller which SAMPLES can apply
     * it -- the caller which re-uses an earlier sample has no use for it,
     * because the shift is already inside the number it kept.
     */
    private fun classify(
        roll: RollResult,
        target: Int,
        failures: List<RitualFailure>,
        criticals: List<RitualCritical>,
        successBands: List<RitualSuccess>,
        tableRoll: (shift: Int) -> Int,
    ): RitualOutcome {
        // By outcome, not by margin. A 17 or 18 fails whatever the target, and
        // against a target of 18 its margin is not even negative -- reading
        // the margin alone would report the book's own critical-failure row as
        // a success.
        if (roll.outcome == Outcome.SUCCESS || roll.outcome == Outcome.CRITICAL_SUCCESS) {
            return RitualOutcome(
                roll = roll,
                target = target,
                marginOfFailure = 0,
                success = successFor(target - roll.total, successBands),
            )
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

        val criticalRoll = tableRoll(if (rolledCritical) 0 else SEVERE_MISFIRE_SHIFT)
        return RitualOutcome(
            roll = roll,
            target = target,
            marginOfFailure = margin,
            consequence = consequence,
            criticalRoll = criticalRoll,
            critical = criticalFor(criticalRoll, criticals),
        )
    }

    /**
     * The grade a success by [margin] earns (Ch. 7 Step 4, p. 62).
     *
     * **A critical success is graded by its margin, like any other.** The
     * table prints no critical row, and a rolled 3 or 4 succeeds against any
     * target however hopeless -- so against a target of 2 the margin is
     * NEGATIVE and no printed band covers it. That takes the mildest row
     * rather than crashing or inventing a "surpasses intent" the book never
     * granted: the same call `FrightCheck` makes for a roll that overshoots
     * its table by nothing, and the reason the milder band is pinned as
     * starting at 0 rather than at 1.
     */
    fun successFor(
        margin: Int,
        bands: List<RitualSuccess> = ContentLoader.ritualSuccess(),
    ): RitualSuccess = bands.firstOrNull {
        margin >= it.from && (it.to == null || margin <= it.to)
    } ?: bands.minByOrNull { it.from } ?: error("the book prints no ritual success band")

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

    private fun rollThreeDSix(source: RandomSource) = source.threeDSix()
}
