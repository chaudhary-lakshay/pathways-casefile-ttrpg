package com.pathways.rules

import com.pathways.rules.content.BoonGranting
import com.pathways.rules.content.BoonModifier
import com.pathways.rules.content.ContentLoader
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
     */
    fun targetFor(
        skillLevel: Int,
        cor: Int,
        sequence: Int,
        chosen: List<BoonModifier> = emptyList(),
        gmValue: Map<String, Int> = emptyMap(),
    ): Int = RitualResolution.targetFor(
        skillLevel = skillLevel + penaltyForSequence(sequence) + modifierTotal(chosen, gmValue),
        weight = weight(),
        cor = cor,
    )

    /**
     * Petition for the boon, and let Chapter 7 resolve what comes back.
     *
     * The sequence penalty and the chosen circumstances ride on the skill
     * rather than being a second weight, because the weight is the one thing
     * this chapter fixes.
     */
    fun attempt(
        skillLevel: Int,
        cor: Int,
        sequence: Int,
        source: RandomSource,
        chosen: List<BoonModifier> = emptyList(),
        gmValue: Map<String, Int> = emptyMap(),
    ): RitualOutcome = RitualResolution.attempt(
        skillLevel = skillLevel + penaltyForSequence(sequence) + modifierTotal(chosen, gmValue),
        weight = weight(),
        cor = cor,
        source = source,
    )
}
