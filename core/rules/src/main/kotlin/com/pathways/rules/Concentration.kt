package com.pathways.rules

import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.rules.content.ContentLoader

/** One attempt to hold a focus that is being interfered with. */
data class ConcentrationOutcome(val target: Int, val roll: RollResult) {
    /**
     * A CRITICAL success holds too.
     *
     * This row prints no override of the general critical rules, so a rolled
     * 3 or 4 succeeds against any target -- and comparing `== SUCCESS` alone
     * would report the book's best roll as a lost focus, the read-by-outcome
     * slip [FrightCheck], [RitualResolution] and [Wounds] each made once.
     */
    val held: Boolean
        get() = roll.outcome == Outcome.SUCCESS || roll.outcome == Outcome.CRITICAL_SUCCESS
}

/**
 * Ch. 5's Concentrate maneuver: "Roll Will-3 if injured or defending."
 *
 * Printed on p. 36 in the maneuver table, extracted since the combat slice
 * and read by nothing -- so every ability this book gates behind a Concentrate
 * maneuver (a ritual's channelling, the gauntlet's aura, the candle, the
 * Astral projection) went unopposed however badly the caster's turn was going.
 *
 * **The roll is CONDITIONAL, and that is the whole reading.** The sentence
 * calls for it only when the character is injured or defending; a caster
 * concentrating undisturbed rolls nothing at all. Offering an unconditional
 * Will-3 would tax every act of focus in the book with a penalty it never
 * printed, so [attempt] refuses a roll nobody asked for rather than answering
 * one -- the refusal-over-a-plausible-number call [Detection.attributeFor]
 * (6ah) and [Pools.canAffordSpi] (6ar) each needed.
 *
 * **One declaration, because the book prints one sentence and one number.**
 * The three defence refusals are three causes (6bg) precisely because they
 * print three sentences and a trait cancels exactly one. Here "injured" and
 * "defending" share a clause, a target and an effect, and nothing tells them
 * apart -- so splitting them would be inventing a distinction to display.
 *
 * **A declaration, not a computation.** This app holds no turn, so whether the
 * character was hurt or parried while concentrating is the GM's to say -- the
 * fourth rule in a row to become buildable that way (6bg, 6bl, 6bm), against a
 * handover that ranked a round tracker first (6bd).
 *
 * **The roll is offered, never fired**, exactly as a Fright Check (6c), a
 * Major Wound's HT roll (6n) and a self-control roll (6am) are: WHEN a focus
 * is disturbed is the GM's call.
 *
 * **No printed critical override, so [SuccessRoll]'s stand** -- the
 * [FrightCheck] reading (6c), not the PCR's.
 *
 * **And no consequence is generated.** The book prints the roll and stops: no
 * row says what a failed concentration costs. [ConcentrationOutcome] therefore
 * reports whether it held and nothing else, the call ch. 4's two sense tables
 * needed when they printed no failure row (6aj). Inventing "the power is lost"
 * would be authoring the only consequence in the rule.
 */
object Concentration {

    /**
     * The penalty the maneuver's own cell prints.
     *
     * A number inside a prose cell, so it lives here with the sentence pinned
     * by `extract_combat.PINNED_MANEUVERS_EXACT` -- the split the maneuver
     * table's +4/-4 (6i), the +3 support cap (6y) and the orthodox -2 (6z)
     * all take.
     */
    const val PENALTY = -3

    /** The maneuver row this rule is printed on. */
    private const val ROW = "Concentrate"

    /** That row, quoted whole rather than paraphrased. */
    fun printed(): String = ContentLoader.maneuvers().first { it.maneuver == ROW }.description

    /**
     * The number to roll 3d6 under.
     *
     * [will] is the DIGESTED Will, because a Beyonder's is the one their
     * potions left them with -- the reading [Corruption.maxFor] already needed.
     *
     * No GM modifier: the cell prints one number and no range, unlike the
     * fright triggers and the darkness row, whose steppers exist because the
     * book itself declines to pick a value there. A circumstance beyond the
     * printed one is Appendix A's business ([CommonModifiers]).
     */
    fun targetFor(will: Int): Int = will + PENALTY

    /**
     * Roll it, if the book calls for a roll at all.
     *
     * [disturbed] is the GM's declaration that the character is injured or
     * defending. Refused rather than rolled when it is false: a caster who
     * concentrated in peace is not one who succeeded at a Will-3 roll, and
     * answering with an outcome would put a failure in front of a GM the book
     * never asked to roll.
     */
    fun attempt(will: Int, disturbed: Boolean, source: RandomSource): ConcentrationOutcome {
        require(disturbed) { "the book calls for no roll here: ${printed()}" }
        val target = targetFor(will)
        return ConcentrationOutcome(target, SuccessRoll.against(target, source))
    }
}
