package com.pathways.rules

import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.rules.content.AstralColor
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.EtherBodyRegion
import com.pathways.rules.content.PathwayDifference
import com.pathways.rules.content.SpecialForm
import com.pathways.rules.content.SpiritVisionGrant
import com.pathways.rules.content.SpiritVisionRow

/** One reading, and what moved its target. */
data class SpiritVisionResult(
    val roll: RollResult,
    val row: SpiritVisionRow,
    val parts: List<String>,
)

/**
 * Reading a soul (Ch. 7.6, p. 77-78).
 *
 * "Activate by expending 1 SPI (1 SPI per minute to maintain). Make a
 * Perception-based roll to interpret correctly."
 *
 * **Chapter 4 prices it a second time and says strictly less.** p. 34's skill
 * overview reads "Cost: 1 SPI per minute" -- the maintenance rate alone, with
 * no activation charge. It agrees with the sentence above on the term it
 * prints and is silent on the one it does not, so there is nothing to
 * reconcile: a second printing widens a rule only by saying something the
 * first did not. 7.6 is titled "A Complete Guide" and governs. The activation
 * charge `SpiritVisionDialog` bills is not disputed by that line.
 *
 * **The gate is each pathway's own block, never chapter 7.6's summary.**
 * Section C lists five Sequence 9 pathways and there are five Sequence 9
 * grants, but the membership differs by one in each direction: it prints
 * Visionary, whose Spectator block prices Body Language Analysis in FP and
 * names no Spirit Vision, and omits Moon, whose Apothecary block grants it
 * outright. A screen gated on C would refuse the ability to a character who
 * has it and offer it to one who does not. [grantedTo] reads [grants], which
 * `tools/extract_spirit_vision.py` derives from the blocks themselves.
 *
 * **The prose matching lives in the extractor, deliberately.** 21 of the 88
 * potion blocks contain the words "Spirit Vision" and mean three different
 * things by them -- a grant, an upgrade to a grant, or a DEFENCE against
 * someone else's (the Werewolf reveals only black hair; the Hypnotist's
 * illusion works "even against Spirit Vision"). Reading that here would make
 * this object a second authority on what the book grants, competing with the
 * content files. What ships is structure; what parses English is the tool.
 *
 * **Nothing modifies this roll.** The chapter prints no bonus, no penalty and
 * no situational table, so [targetFor] adds only what "all rolls" already
 * reaches -- shock, and reeling or fatigue. A circumstance beyond those is
 * Appendix A's business ([CommonModifiers]), as it is for [Concentration].
 *
 * **This table prints a FAILURE row, which its chapter 4 siblings do not.**
 * "Incorrect reading" is an answer, not the absence of one: the character
 * comes away believing something false. [SpiritualSenses.rowFor] returns null
 * on a failure by documented design; this one must not, and [isMisleading]
 * exists so a caller can tell the two apart without matching on the text.
 */
object SpiritVision {

    private fun file() = ContentLoader.spiritVision()

    /** A: the eight Astral Projection colours and what each reveals. */
    fun astralColors(): List<AstralColor> = file().astralColors

    /** B: the eight Ether Body regions and their colours. */
    fun etherBody(): List<EtherBodyRegion> = file().etherBody

    /** B's closing sentence, which is a rule about reading the table. */
    fun etherBodyNote(): String = file().etherBodyNote

    /**
     * C, which is REFERENCE and is wrong in both directions -- see the KDoc
     * above. Show it, never gate on it.
     */
    fun pathwayDifferences(): List<PathwayDifference> = file().pathwayDifferences

    /** D's closing sentence: each pathway's own name for the ability. */
    fun specialForms(): List<SpecialForm> = file().specialForms

    /** D's five result rows, in printed order. */
    fun rows(): List<SpiritVisionRow> = file().reading.rows

    /** Which pathway grants it, and from which rung. Derived from the blocks. */
    fun grants(): List<SpiritVisionGrant> = file().grants

    /** SPI to switch it on. */
    fun activation(): Int = file().reading.activation

    /** SPI per minute to keep it on. */
    fun maintenance(): Int = file().reading.maintenance

    /**
     * Whether a character on [pathway] at [sequence] has the ability.
     *
     * Sequences count DOWN and are cumulative -- a Sequence 8 Fool drank the
     * Seer's potion first -- so the grant reaches every rung at or below the
     * one that made it.
     *
     * An unprinted pathway throws rather than answering false: this app walks
     * 22 pathways plus the non-standard ones, and a typo silently withdrawing
     * a character's ability is the failure this refuses to have.
     */
    fun grantedTo(pathway: String, sequence: Int): Boolean {
        require(ContentLoader.pathways().any { it.pathway == pathway }) {
            "no pathway named $pathway is printed"
        }
        val granted = grants().firstOrNull { it.pathway == pathway } ?: return false
        return sequence <= granted.sequence
    }

    /**
     * The book's own name for the ability on [pathway], or null where it
     * grants none.
     *
     * Not always "Spirit Vision": the Mystery Pryer's block writes "Eyes of
     * Mystery Prying" and never the common name, which chapter 7.6 and the
     * Special Forms sentence both confirm is the same ability. A screen that
     * labels the control "Spirit Vision" for a Hermit names something their
     * sheet does not.
     */
    fun formFor(pathway: String): String? =
        grants().firstOrNull { it.pathway == pathway }?.form

    /**
     * The number to roll 3d6 under.
     *
     * The bare Perception, plus the two penalties that reach every roll in the
     * book. The chapter itself prints no modifier at all.
     */
    fun targetFor(per: Int, shock: Int = 0, hurt: Int = 0): Int = per + shock + hurt

    /**
     * The row a successful reading of [margin] lands on.
     *
     * The LAST band that fits, because the book prints them least-demanding
     * first: a margin of 5 satisfies "Success", "Success by 3+" and "Success
     * by 5+" alike, and the richest reading is the one earned.
     */
    fun rowFor(margin: Int): SpiritVisionRow =
        rows().lastOrNull { it.margin != null && it.margin <= margin }
            ?: rows().firstOrNull { it.result == "Success" }
            ?: error("the Spirit Vision table prints no success row")

    /**
     * The row the book names for [outcome], for the results it does not band.
     *
     * "Critical" and "Failure" are printed rows here. A critical FAILURE has
     * none, and takes the Failure row: the book grades this table in five
     * rows and a worse failure is still an incorrect reading.
     */
    fun rowForOutcome(outcome: Outcome): SpiritVisionRow {
        val named = when (outcome) {
            Outcome.CRITICAL_SUCCESS -> "Critical"
            Outcome.FAILURE, Outcome.CRITICAL_FAILURE -> "Failure"
            Outcome.SUCCESS -> "Success"
        }
        return rows().firstOrNull { it.result == named }
            ?: error("the Spirit Vision table prints no $named row")
    }

    /**
     * Whether [row] is a reading the character should NOT trust.
     *
     * The rule this exists for: a failed Spirit Vision does not tell the
     * player they failed, it tells them something wrong. A screen that prints
     * "Failure" has modelled the die and not the rule, so a caller needs to
     * know which side of that line a row falls on without matching its text.
     */
    fun isMisleading(row: SpiritVisionRow): Boolean = row.result == "Failure"

    /**
     * What made the target the number it is, in the order it is built.
     *
     * Public because the dialog prints this above the button that rolls, and
     * the roll returns it too. Two sites assembling one breakdown is the
     * defect this repo has produced three times over -- the answer is
     * returned once, in the form both callers consume.
     */
    fun partsFor(per: Int, shock: Int = 0, hurt: Int = 0): List<String> = buildList {
        add("Per $per")
        if (shock != 0) add("shock $shock")
        if (hurt != 0) add("hurt or tired $hurt")
    }

    /**
     * Roll it, and read the row. [SpiritVisionResult.parts] is [partsFor].
     */
    fun attempt(
        per: Int,
        source: RandomSource,
        shock: Int = 0,
        hurt: Int = 0,
    ): SpiritVisionResult =
        // Through [targetFor], never re-derived: the caption above the button
        // and the roll under it take the same terms or they disagree.
        rollAgainst(targetFor(per, shock, hurt), source, partsFor(per, shock, hurt))

    /**
     * The roll itself, against a [target] already composed by [targetFor],
     * carrying the [parts] already composed by [partsFor] -- what a screen
     * that printed both calls, so each is composed once (6eg, 6eh).
     */
    fun rollAgainst(target: Int, source: RandomSource, parts: List<String>): SpiritVisionResult {
        val roll = SuccessRoll.against(target, source)
        val row = if (roll.outcome == Outcome.SUCCESS) rowFor(roll.margin)
        else rowForOutcome(roll.outcome)
        return SpiritVisionResult(roll, row, parts)
    }
}
