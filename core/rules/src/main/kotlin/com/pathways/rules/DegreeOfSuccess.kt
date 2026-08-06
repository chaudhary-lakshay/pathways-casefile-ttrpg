package com.pathways.rules

import com.pathways.model.Outcome
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SuccessBand

/**
 * How well a successful roll succeeded (Appendix A, p. 159).
 *
 * [PLAIN] is a success that beat its target by less than 3. It is deliberately
 * a degree rather than the absence of one, because the book prints "Success"
 * as its own row of the same ladder.
 */
enum class SuccessDegree { PLAIN, GOOD, EXCELLENT }

/**
 * Appendix A's degree-of-success ladder.
 *
 * **Why an appendix defines this at all.** Chapter 2 says "Margins matter --
 * the degree of success or failure often determines quality of outcome" and
 * tabulates nothing; five Sequence abilities then scale their effect by "the
 * degree of success". The only table is on p. 159. That is a cross-reference
 * making another page reachable, not a summary overruling a chapter -- the
 * distinction CLAUDE.md 6v had to draw about this same appendix, which
 * elsewhere restates chapter 5 wrongly. Five of the ladder's seven rows
 * repeat rules [SuccessRoll] has held since M0 and agree exactly, and
 * `extract_success_ladder.verify()` checks all five; that agreement is the
 * whole warrant for adopting the two rows nothing else prints.
 *
 * **This is deliberately not part of [Outcome].** Good and Excellent are
 * sub-bands of a success, so widening that enum would break every
 * `== Outcome.SUCCESS` in the codebase -- a ritual whose caster rolled well
 * would stop counting as cast.
 *
 * **It is scoped to plain skill rolls.** Divination reads clarity off its own
 * margin table, and [RitualResolution], [FrightCheck] and [Wounds] each grade
 * a margin against a table their own chapter prints. Specific beats general,
 * the same call the PCR's critical rules and the ritual CoR ladder already
 * needed, so none of them layers this general ladder on top.
 */
object DegreeOfSuccess {

    private const val PLAIN_ROW = "Success"
    private const val GOOD_ROW = "Good Success"
    private const val EXCELLENT_ROW = "Excellent Success"

    /** The printed ladder, least- to most-demanding. */
    fun bands(): List<SuccessBand> = ContentLoader.successLadder()

    /**
     * The degree a roll reached, or null when it was not a plain success --
     * a failure of either kind, a critical success, or a roll that never
     * happened.
     *
     * **Outcome first, margin second.** A rolled 3 against a target of 5 is a
     * critical success by the always-rule and has a margin of only 2, so
     * reading the margin first would report the book's own best row as an
     * ordinary success. That inversion is the slip [FrightCheck],
     * [RitualResolution] and [Wounds] each made once; the table prints its
     * "3-4 always" row above the ladder for exactly this reason.
     *
     * Pass the EFFECTIVE outcome. [SkillRollOutcome.degree] does, which is why
     * a roll demoted by `requiresCritical` cannot come back as a Good Success.
     */
    fun of(outcome: Outcome?, margin: Int): SuccessDegree? {
        if (outcome != Outcome.SUCCESS) return null
        // The roll-under rows overlap -- a margin of 5 satisfies all three --
        // and the book prints them least- to most-demanding, so the LAST row
        // that fits is the answer rather than the first.
        val reached = bands().filter { it.comparison == "<=" && margin >= -(it.offset ?: 0) }
        return degreeNamed(reached.lastOrNull()?.result ?: PLAIN_ROW)
    }

    private fun degreeNamed(result: String): SuccessDegree = when (result) {
        PLAIN_ROW -> SuccessDegree.PLAIN
        GOOD_ROW -> SuccessDegree.GOOD
        EXCELLENT_ROW -> SuccessDegree.EXCELLENT
        else -> error("the ladder printed a degree this app has no case for: $result")
    }

    /** The margin a degree needs, for a screen that wants to say "Good at 3+". */
    fun marginFor(degree: SuccessDegree): Int {
        val row = when (degree) {
            SuccessDegree.PLAIN -> PLAIN_ROW
            SuccessDegree.GOOD -> GOOD_ROW
            SuccessDegree.EXCELLENT -> EXCELLENT_ROW
        }
        val band = bands().firstOrNull { it.result == row }
            ?: error("the ladder no longer prints a $row row")
        return -(band.offset ?: 0)
    }
}
