package com.pathways.rules

import com.pathways.rules.content.BoonGranting
import com.pathways.rules.content.BoonModifier
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.LanguageProficiency
import com.pathways.rules.content.RitualWeight

/**
 * Boon granting (Ch. 13, p. 144-145).
 *
 * A mortal prays to a Hidden Existence with its full honorific name and is
 * granted a fragment of its power. Mechanically it is a Chapter 7 ritual with
 * its parameters nailed down, so nothing here re-implements resolution --
 * [RitualResolution] rolls it, charges its failure bands, and rolls the
 * Critical Failure Table the refusal penalty points back at.
 *
 * **Heavy is the time, Moderate is the weight.** The book prints
 * "Ritual Complexity: Heavy (1 hour base time)" and "Effect Weight: Moderate
 * (-2 difficulty, 4-6 SPI required)" one line apart, and only the second
 * reaches the dice: "its effect weight is Moderate (-2) because the actual
 * power comes from the patron, not the ritual itself." Taking the complexity
 * as the weight rolls at -4 and charges 8+ SPI.
 *
 * **Chapter 7's Phase I preparation is deliberately not applied here.** This
 * chapter prints its own Ritual quality row covering the same idea, and
 * adding both would charge one preparation twice -- the same double-count the
 * corruption ladder already had to be scoped out of.
 */
object Boons {

    /** The ladder the book counts down, Sequence 9 to Sequence 0. */
    const val LOWEST_SEQUENCE = 0
    const val HIGHEST_SEQUENCE = 9

    /** "Sequence 8 (-2)" -- the first rung, and what the doubling starts from. */
    private const val FIRST_PENALTY = 2

    fun granting(): BoonGranting = ContentLoader.boonGranting()

    fun modifiers(): List<BoonModifier> = ContentLoader.boonModifiers()

    /** The modifier groups the book offers, in printed order. */
    fun groups(): List<String> = modifiers().map { it.group }.distinct()

    /**
     * Chapter 7's weight row this ritual is fixed at -- Moderate, never the
     * Heavy that names its running time.
     */
    fun weight(): RitualWeight {
        val named = granting().weight
        return RitualResolution.weights().firstOrNull { it.weight.equals(named, ignoreCase = true) }
            ?: error("Chapter 13 fixes this ritual at '$named', which Chapter 7 no longer prints")
    }

    /** What the ritual costs in SPI, inside the weight's own printed cell. */
    fun spiFor(chosen: Int = weight().spi, burned: Int = 0): Int =
        RitualResolution.spiFor(weight(), chosen, burned)

    /**
     * Whether a burn can still move [outcome] (ch. 13 via ch. 7, p. 66).
     *
     * **Chapter 13 prints no burn sentence of its own, and that is not its
     * silence.** "The boon granting ritual uses the standard Ritualistic Magic
     * system (see Chapter 7)" is the chapter's own first line about the
     * procedure, and chapter 2's SPI row names Ritualistic Magic outright. So
     * the push arrives here by referral rather than by resemblance -- the bar
     * 6ck set for widening a rule, cleared by a sentence rather than by the
     * two screens looking alike (6cx). What chapter 13 fixes is the baseline:
     * Heavy complexity, Moderate weight. It replaces no part of chapter 7 it
     * does not name.
     */
    fun canPush(outcome: RitualOutcome): Boolean = RitualResolution.canPush(outcome)

    /** The same dice read against a target [points] SPI higher. See [canPush]. */
    fun pushed(outcome: RitualOutcome, points: Int): RitualOutcome =
        RitualResolution.pushed(outcome, points)

    /**
     * The penalty for supplicating a boon at [sequence].
     *
     * READING. The book prints "Sequence 9 equivalent (+0), Sequence 8 (-2),
     * Sequence 7 (-4), and so on, doubling the penalty with each sequence."
     * Those three values fit two different ladders -- a doubling and a flat
     * -2 per step -- which only diverge at Sequence 6 (-8 against -6). The
     * sentence says doubling, so doubling wins, and the sentence itself is
     * pinned by extract_boons.verify() so a content change surfaces here.
     *
     * It gets brutal fast, which is the point: the chapter says a generous
     * sacrifice "can offset or even negate this penalty", and the best one
     * printed is +6 -- enough at Sequence 8, not at Sequence 6.
     */
    fun penaltyForSequence(sequence: Int): Int {
        require(sequence in LOWEST_SEQUENCE..HIGHEST_SEQUENCE) {
            "a boon runs Sequence $HIGHEST_SEQUENCE down to $LOWEST_SEQUENCE; was $sequence"
        }
        if (sequence == HIGHEST_SEQUENCE) return 0
        return -(FIRST_PENALTY shl (HIGHEST_SEQUENCE - 1 - sequence))
    }

    /**
     * What the [chosen] circumstances are worth, with [gmValue] picking inside
     * any that print a range.
     *
     * One option per group: a sacrifice cannot be both perfunctory and great,
     * and summing two rows of one bullet is not a choice the book offers. A
     * row that prints a single number takes no override -- overriding it would
     * be inventing a value, not reading one.
     *
     * [gmValue] is keyed by [BoonModifier.key], not by label: the two groups
     * are free to print the same words, and a label-keyed value would move
     * both rows at once. A key naming nothing chosen is refused rather than
     * ignored, so a caller that mistypes one learns about it.
     */
    fun modifierTotal(chosen: List<BoonModifier>, gmValue: Map<String, Int> = emptyMap()): Int {
        val duplicated = chosen.groupBy { it.group }.filterValues { it.size > 1 }.keys
        require(duplicated.isEmpty()) {
            "pick one option per group; $duplicated got more than one"
        }
        val unknown = gmValue.keys - chosen.map { it.key }.toSet()
        require(unknown.isEmpty()) { "no chosen option is keyed $unknown" }
        return chosen.sumOf { row ->
            val value = gmValue[row.key] ?: return@sumOf row.modifier
            val best = row.best
            require(best != null) {
                "'${row.label}' prints ${row.printed}, not a range to pick $value inside"
            }
            val ends = listOf(row.modifier, best).sorted()
            require(value in ends[0]..ends[1]) {
                "'${row.label}' prints ${row.printed}; $value is outside it"
            }
            value
        }
    }

    /**
     * The number to roll under: the caster's skill, the fixed Moderate weight,
     * the boon's sequence, their corruption, and the circumstances chosen.
     *
     * **[language] and [answer] are the owner's rulings (2026-10-13, 6hq),
     * not this chapter's list**, which names neither. A prayer is said in a
     * mystical tongue and priced as chapter 3 prices any ritual in one, and
     * the patron is asked on chapter 7's Entity Response Table first. Until
     * then this refused both, on "the actual power comes from the patron".
     * An [answer] that ends the prayer has no target and is refused: ask
     * [endsPrayer] first.
     *
     * No preparation: this chapter prints its own Ritual quality row for it,
     * and that reason still stands. No power sources either: chapter 13
     * prices the patron's involvement itself, and its own Ritual quality row
     * already stands in for Section IV's circumstances. The genuinely open one
     * is IV.C's assistants, which chapter 13 neither offers nor forbids -- so
     * it is left off rather than assumed, and this sentence exists so the
     * omission stays a decision instead of becoming an oversight. IV.B's HP
     * is fuel and "does not grant a bonus to the roll", so it never reaches
     * this target: the dialog charges it, and it is how a mortal prays.
     *
     * And no Flourish Bonus, for the same reason as the preparation it sits
     * beside: Phase II awards it for narrating the incantation, and chapter 13
     * prices that whole performance through its own Ritual quality row.
     */
    fun targetFor(
        skillLevel: Int,
        cor: Int,
        sequence: Int,
        chosen: List<BoonModifier> = emptyList(),
        gmValue: Map<String, Int> = emptyMap(),
        language: LanguageProficiency? = null,
        answer: EntityAnswer? = null,
    ): Int {
        require(answer == null || !endsPrayer(answer)) {
            "the patron's answer ended the prayer; there is no roll to compose"
        }
        return RitualResolution.targetFor(
            skillLevel = skillLevel + penaltyForSequence(sequence) + modifierTotal(chosen, gmValue) +
                (answer?.ritualModifier ?: 0),
            weight = weight(),
            cor = cor,
            language = language,
        )
    }

    /**
     * Ask the patron, on chapter 7's table. Never shifted: "Hidden
     * existences and evil gods use the table unmodified" (line 4297), and
     * the Great Ones are "hidden existences" (line 8912).
     */
    fun ask(source: RandomSource): EntityAnswer = Invocation.ask(source, orthodoxInGoodStanding = false)

    /**
     * Whether [answer] leaves no prayer to roll.
     *
     * A rejection is the table's own: "Ritual fails automatically". **No
     * response is the owner's ruling, against chapter 7**, which lets that
     * ritual go on at +0 on the caster's residual power: a boon's power
     * "comes from the patron" (line 8939), and a patron who did not answer
     * gave none.
     */
    fun endsPrayer(answer: EntityAnswer): Boolean = answer.failsRitual || answer.unanswered

    /**
     * What asking costs, owed at the asking: a fuel paid only at the roll
     * let a closed dialog ask again for nothing.
     *
     * A rejection charges nothing, as [Invocation.fuelFor] refuses it any.
     * No response takes chapter 7's 2 SPI out of the fuel declared
     * ([Invocation.residualFuel]). Any other answer costs what was declared.
     */
    fun fuelFor(answer: EntityAnswer, cost: Int, hpSacrificed: Int): Fuel = when {
        answer.failsRitual -> Fuel(0, 0)
        answer.unanswered -> Invocation.residualFuel(hpSacrificed)
        else -> Fuel(PowerSources.spiOwed(cost, hpSacrificed), hpSacrificed)
    }

    /**
     * Petition for the boon, and let Chapter 7 resolve what comes back.
     *
     * The sequence penalty and the chosen circumstances ride on the skill
     * rather than being a second weight, because the weight is the one thing
     * this chapter fixes. No tongue and no patron's answer: this has no
     * production caller, and the screen composes [targetFor] with both.
     */
    fun attempt(
        skillLevel: Int,
        cor: Int,
        sequence: Int,
        source: RandomSource,
        chosen: List<BoonModifier> = emptyList(),
        gmValue: Map<String, Int> = emptyMap(),
    ): RitualOutcome =
        // Through [targetFor], so the adjustments are written once: this
        // used to repeat them, and the screen rolls the printed target.
        RitualResolution.rollAgainst(targetFor(skillLevel, cor, sequence, chosen, gmValue), source)
}
