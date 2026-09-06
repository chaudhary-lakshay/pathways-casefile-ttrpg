package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.Incantation

/**
 * What the caster actually says (Ch. 7 p. 63/65, Ch. 7.5 p. 76).
 *
 * Phase II prints "The player narrates the incantation (4-part Hermes
 * structure -- see reference below). The GM may award a Flourish Bonus (+1)
 * for good in-character delivery." The app claimed that sentence, rendered its
 * SECOND half beside a chip, and awarded the bonus for narrating a structure
 * it never showed. This is the reference the first half points at.
 *
 * **Reference, not a modifier.** Nothing here reaches a roll: the +1 is the
 * GM's to award and [RitualMode.FLOURISH_BONUS] already carries it. The same
 * standing as `Equipment.forSkill` -- shown, never applied.
 */
object Incantations {

    /** Invoking an entity: what Phase II sends the caster to by default. */
    const val INVOCATION = "Invocation"

    /** Ch. 7 IV.A, and what the page's own note replaces the four parts with. */
    const val PRAYING_TO_ONESELF = "Praying to Oneself"

    fun all(): List<Incantation> = ContentLoader.incantations()

    /**
     * The structure this caster recites, chosen by where the power comes from.
     *
     * The book's own sentence: "For Praying to Oneself, replace the four-part
     * structure with the three-line self-description." That is a selection
     * rule, not two unrelated printings, and `extract_ritual.verify_incantations`
     * pins the sentence for exactly that reason.
     *
     * [isInvoking] is the two-state choice the ritual screen already offers --
     * "Personal SPI" against "Invocation" -- and p. 62 labels the first
     * "Personal SPI (Praying to Oneself)", which is what makes the join exact.
     * A caster burning HP, leading assistants or feeding a catalyst is still
     * praying to oneself: Section IV.B-E are fuel and bonuses stacked ON a
     * source, not sources of their own.
     *
     * A Boolean rather than the two-value enum a review proposed. The book's
     * sentence is binary, the control is a two-chip pair with no third state,
     * and this repo does not ship an abstraction whose only job is to rename
     * `true` -- the same call 6df made against a per-row scope enum. If a
     * third source is ever printed, the enum arrives with it.
     */
    fun forPowerSource(isInvoking: Boolean): Incantation {
        val scope = if (isInvoking) INVOCATION else PRAYING_TO_ONESELF
        return all().firstOrNull { it.scope == scope }
            ?: throw IllegalStateException(
                "no incantation structure for '$scope'; the book prints " +
                    all().joinToString { it.scope },
            )
    }
}
