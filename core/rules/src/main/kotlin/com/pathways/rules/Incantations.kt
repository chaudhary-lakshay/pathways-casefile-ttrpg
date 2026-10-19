package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.Incantation

/**
 * What the caster actually says (Ch. 9 III.B Phase II, and the summoning
 * incantation).
 *
 * Phase II prints "Narrate incantation (4-part Hermes structure: Invocation,
 * Grace, Request, Empowerment). GM may award +1 Flourish Bonus for good
 * roleplay." This is the structure that sentence names.
 *
 * **Reference, not a modifier.** Nothing here reaches a roll: the +1 is the
 * GM's to award and [RitualMode.FLOURISH_BONUS] already carries it. v6.9i
 * deleted Praying to Oneself's self-description, so there is one structure to
 * recite and nothing to select between.
 */
object Incantations {

    fun all(): List<Incantation> = ContentLoader.incantations()

    /** The structure every full ritual recites: the one not marked reference-only. */
    fun standard(): Incantation =
        all().singleOrNull { !it.referenceOnly }
            ?: throw IllegalStateException(
                "expected one recited incantation structure; the book prints " +
                    all().joinToString { it.scope },
            )
}
