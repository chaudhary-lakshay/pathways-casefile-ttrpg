package com.pathways.rules

import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SelfControlRoll
import com.pathways.rules.content.TraitDefinition

/** A self-control roll a character owns, with the book's own effect text. */
data class SelfControlCheck(
    val trait: String,
    val spec: SelfControlRoll,
    /** The book's effect cell, quoted rather than paraphrased. */
    val printed: String,
)

/**
 * One attempt to hold a compulsion.
 *
 * [held] is the SUCCESS side. Every one of the book's phrasings runs the same
 * direction once read carefully -- "Must roll vs. 12 **or** investigate any
 * mystery", "Must roll vs. 12 **to avoid** angry outbursts", "Must roll vs.
 * Will **to undertake** any sustained effort" -- so a success is always the
 * character keeping hold of themselves. The consequence is never generated
 * from that: [SelfControlCheck.printed] is shown verbatim, because three
 * different sentence shapes mean a paraphrase would get one of them backwards.
 */
data class SelfControlOutcome(
    val check: SelfControlCheck,
    val target: Int,
    val roll: RollResult,
) {
    /**
     * A CRITICAL success holds too. This table prints no override of the
     * general critical rules, so a rolled 3 or 4 succeeds against any target
     * -- and comparing `== SUCCESS` alone would report the book's best roll
     * as a character losing control, the read-by-outcome slip [FrightCheck],
     * [RitualResolution] and [Wounds] each made once.
     */
    val held: Boolean
        get() = roll.outcome == Outcome.SUCCESS || roll.outcome == Outcome.CRITICAL_SUCCESS
}

/**
 * Ch. 3's self-control rolls -- the disadvantages that print a number.
 *
 * **The roll is offered, never fired.** 6al left these out on the grounds
 * that WHEN to call for one is the GM's. That is true and is not a reason to
 * leave the number unreachable: a Fright Check (6c) and a Major Wound's HT
 * roll (6n) are equally the GM's, and both ship as a button the GM presses.
 * Nothing here rolls on its own.
 *
 * **A flat target and an attribute roll are different rules.** "Curious (12)"
 * is 12 whatever the character's Will is, so a potion that raises Will makes
 * no difference to it. Fifteen rows key off Will or IQ instead and move with
 * the digested character. They are kept apart in [SelfControlRoll] and they
 * are kept apart here.
 *
 * **The attribute is the DIGESTED one**, because a Beyonder's Will is the one
 * their potions left them with -- the reading [Corruption.maxFor] already
 * needed. Callers pass the derived values.
 *
 * **No printed critical override, so [SuccessRoll]'s stand.** A 3 or 4 holds
 * against any target and a 17 or 18 fails one. The PCR prints its own
 * override and this table prints none, so specific-beats-general does not
 * fire -- the [FrightCheck] reading (6c), not the PCR's.
 *
 * **Conditions are NOT applied automatically.** [FrightCheck] takes a single
 * GM modifier and no [Conditions] ledger, and two Will rolls in one app
 * behaving differently would be worse than either choice. The stepper is the
 * GM's, exactly as the fright trigger's is.
 *
 * **Matching is by exact name and nothing looser.** Three sheets print
 * "Absent-Minded" where the book's list prints "Absent-Mindedness", and one
 * prints "Paranoia: Bodies of water" against a bare "Paranoia". Those are
 * sheet/book disagreements, and 4.1 says the book wins and the disagreement
 * is recorded rather than repaired -- so they match nothing here, and
 * `SelfControlTest` pins that they are still unmatched, which is what stops a
 * later "fix" erasing the conflict.
 */
object SelfControl {

    /**
     * The two attributes this book keys a self-control roll on.
     *
     * [SelfControlRoll] refuses anything else at LOAD time, so the throw
     * below is unreachable from content -- it guards a caller that builds a
     * spec by hand. The sheet screen prints this target for every
     * disadvantage it lists, and a content row that only failed here would
     * have taken that whole screen down.
     */
    const val WILL = "Will"
    const val IQ = "IQ"

    /**
     * The self-control rolls [traits] carry, in sheet order.
     *
     * Only [TraitKind.DISADVANTAGE] rows are considered: self-control is a
     * disadvantage mechanic, and an advantage sharing a name would not be one.
     */
    fun checksFor(
        traits: List<Trait>,
        catalogue: List<TraitDefinition> = ContentLoader.disadvantages(),
    ): List<SelfControlCheck> {
        val byName = catalogue.associateBy { it.name.lowercase() }
        return traits.mapNotNull { owned ->
            if (owned.kind != TraitKind.DISADVANTAGE) return@mapNotNull null
            val row = byName[TraitEffects.catalogueName(owned.name).lowercase()]
                ?: return@mapNotNull null
            val spec = row.selfControl ?: return@mapNotNull null
            SelfControlCheck(TraitEffects.displayName(owned.name), spec, row.notes)
        }
    }

    /**
     * The number to roll 3d6 under.
     *
     * [modifier] is the GM's, and it applies to a flat target too: the book
     * prints no modifier for either kind, so refusing one on the flat rows
     * while allowing it on the attribute rows would draw a distinction the
     * text does not. What must not happen is an ATTRIBUTE reaching a flat
     * row, and it cannot -- the two shapes are exclusive by construction.
     */
    fun targetFor(spec: SelfControlRoll, will: Int, iq: Int, modifier: Int = 0): Int {
        val base = spec.target ?: when (spec.attribute) {
            WILL -> will
            IQ -> iq
            // Refused rather than defaulted: a row keyed on an attribute this
            // book never uses for self-control means the reading has changed,
            // and answering with Will would hide that behind a plausible
            // number -- the call `Detection.attributeFor` already needed.
            else -> throw IllegalArgumentException(
                "self-control roll keyed on '${spec.attribute}'; this book " +
                    "prints only $WILL and $IQ"
            )
        }
        return base + spec.offset + modifier
    }

    /** Roll it. Nothing here decides WHEN; that is the GM's, as for a Fright Check. */
    fun attempt(
        check: SelfControlCheck,
        will: Int,
        iq: Int,
        source: RandomSource,
        modifier: Int = 0,
    ): SelfControlOutcome {
        val target = targetFor(check.spec, will, iq, modifier)
        return SelfControlOutcome(check, target, SuccessRoll.against(target, source))
    }
}
