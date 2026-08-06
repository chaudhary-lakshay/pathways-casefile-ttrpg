package com.pathways.rules

import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Appendix A's degree-of-success ladder (p. 159), and the reasons it is
 * allowed to exist at all.
 */
class DegreeOfSuccessTest {

    private fun bandNamed(result: String) =
        DegreeOfSuccess.bands().firstOrNull { it.result == result }
            ?: error("the ladder no longer prints a $result row")

    // ---- the warrant ----------------------------------------------------

    /**
     * **This is why the two new rows are adopted at all.** Five of the
     * ladder's seven rows repeat rules `SuccessRoll` has implemented since M0.
     * CLAUDE.md 6v records that this same appendix restates chapter 5 WRONGLY
     * elsewhere, so "the appendix is reliable" is not available as a premise —
     * the evidence has to come from this table, and it is that every row here
     * which can be checked against the engine agrees with it exactly.
     */
    @Test
    fun `the five rows the engine already implements agree with it`() {
        val alwaysSucceeds = bandNamed("Critical Success (always)")
        assertEquals(3, alwaysSucceeds.rollLow, "3 is the lowest 3d6 can roll")
        assertEquals(SuccessRoll.ALWAYS_SUCCEEDS_AT_OR_UNDER, alwaysSucceeds.rollHigh)

        val alwaysFails = bandNamed("Critical Failure (always)")
        assertEquals(SuccessRoll.ALWAYS_FAILS_AT_OR_OVER, alwaysFails.rollLow)
        assertEquals(18, alwaysFails.rollHigh, "18 is the highest 3d6 can roll")

        assertEquals(0, bandNamed("Success").offset)
        assertEquals("<=", bandNamed("Success").comparison)
        assertEquals(0, bandNamed("Failure").offset)
        assertEquals(">", bandNamed("Failure").comparison)

        // The appendix writes the fail-by-10 rule from the target's side
        // ("roll >= target +10") where SuccessRoll writes it from the
        // margin's (margin <= -10). Same rule, and this asserts they meet.
        // Both rolls stay under 17 so the always-fails row cannot answer for
        // this one: at a target of 6, a 16 is exactly target+10 and a 15 is
        // one short.
        val byMargin = bandNamed("Critical Failure")
        assertEquals(10, byMargin.offset)
        assertEquals(Outcome.CRITICAL_FAILURE, SuccessRoll.evaluate(16, 6).outcome)
        assertEquals(Outcome.FAILURE, SuccessRoll.evaluate(15, 6).outcome)
    }

    /** The two rows nothing else in the book prints, at their printed offsets. */
    @Test
    fun `the degrees sit at the offsets the appendix prints`() {
        assertEquals(-3, bandNamed("Good Success").offset)
        assertEquals(-5, bandNamed("Excellent Success").offset)
        assertEquals(3, DegreeOfSuccess.marginFor(SuccessDegree.GOOD))
        assertEquals(5, DegreeOfSuccess.marginFor(SuccessDegree.EXCELLENT))
        assertEquals(0, DegreeOfSuccess.marginFor(SuccessDegree.PLAIN))
    }

    // ---- reading the ladder ---------------------------------------------

    @Test
    fun `a success is graded by how far it beat the target`() {
        assertEquals(SuccessDegree.PLAIN, DegreeOfSuccess.of(Outcome.SUCCESS, 0))
        assertEquals(SuccessDegree.PLAIN, DegreeOfSuccess.of(Outcome.SUCCESS, 2))
        assertEquals(SuccessDegree.GOOD, DegreeOfSuccess.of(Outcome.SUCCESS, 3))
        assertEquals(SuccessDegree.GOOD, DegreeOfSuccess.of(Outcome.SUCCESS, 4))
        assertEquals(SuccessDegree.EXCELLENT, DegreeOfSuccess.of(Outcome.SUCCESS, 5))
    }

    /**
     * The roll-under rows OVERLAP: a margin of 10 satisfies "<= Target",
     * "<= Target -3" and "<= Target -5" all at once. The book prints them
     * least- to most-demanding, so the LAST match is the answer. Taking the
     * first would report every success as plain and the two new rows would
     * be unreachable while still being loaded and tested.
     */
    @Test
    fun `overlapping bands resolve to the most demanding one`() {
        assertEquals(SuccessDegree.EXCELLENT, DegreeOfSuccess.of(Outcome.SUCCESS, 10))
        val reached = DegreeOfSuccess.bands()
            .filter { it.comparison == "<=" && 10 >= -(it.offset ?: 0) }
        assertEquals(3, reached.size, "a margin of 10 really does satisfy all three rows")
    }

    // ---- outcome first, margin second ------------------------------------

    /**
     * **The slip this codebase has made three times.** A rolled 3 against a
     * target of 5 is a critical success by the always-rule, and its margin is
     * only 2 — so a margin-first reading calls the book's best row an ordinary
     * success. The table prints "3-4 Critical Success (always)" ABOVE the
     * ladder precisely so it is settled before any margin is compared.
     */
    @Test
    fun `a critical success is not graded by its margin`() {
        assertNull(DegreeOfSuccess.of(Outcome.CRITICAL_SUCCESS, 2))
        assertNull(DegreeOfSuccess.of(Outcome.CRITICAL_SUCCESS, 17))
        assertEquals(Outcome.CRITICAL_SUCCESS, SuccessRoll.evaluate(3, 5).outcome)
        assertNull(DegreeOfSuccess.of(SuccessRoll.evaluate(3, 5).outcome, 2))
    }

    @Test
    fun `a failure of either kind has no degree`() {
        assertNull(DegreeOfSuccess.of(Outcome.FAILURE, -1))
        assertNull(DegreeOfSuccess.of(Outcome.CRITICAL_FAILURE, -10))
        assertNull(DegreeOfSuccess.of(null, 5), "a roll that never happened")
    }

    // ---- the demotion trap ------------------------------------------------

    /**
     * `requiresCritical` demotes an ordinary success to a failure, and
     * [SkillRollOutcome.degree] reads the EFFECTIVE outcome — otherwise a roll
     * the screen labels "Failure" would carry "Good success" beside it.
     */
    @Test
    fun `a roll demoted by requiresCritical carries no degree`() {
        val roll = RollResult(total = 5, target = 12, margin = 7, outcome = Outcome.SUCCESS)
        val demoted = SkillRollOutcome(
            label = "Knife", baseTarget = 12, target = 12, applied = emptyList(),
            forbidden = false, requiresCritical = true, roll = roll,
        )
        assertEquals(Outcome.FAILURE, demoted.effectiveOutcome)
        assertNull(demoted.degree, "the margin was 7, which is Excellent on the ladder")

        assertEquals(SuccessDegree.EXCELLENT, demoted.copy(requiresCritical = false).degree)
    }

    @Test
    fun `a forbidden roll has no degree`() {
        val forbidden = SkillRollOutcome(
            label = "Knife", baseTarget = 12, target = 12, applied = emptyList(),
            forbidden = true, requiresCritical = false, roll = null,
        )
        assertNull(forbidden.degree)
    }

    // ---- scope -------------------------------------------------------------

    /**
     * The ladder is general and every chapter that prints its own margin table
     * keeps it — specific beats general, the call the PCR's critical rules and
     * the ritual CoR ladder already needed. This pins the one that would be
     * easiest to double up: a divination reads its clarity off Ch. 6.5's
     * table, whose bands are not these.
     */
    @Test
    fun `the general ladder does not relabel a chapter's own margin table`() {
        val clarityRows = ContentLoader.divinationClarity().map { it.result }
        assertTrue(clarityRows.isNotEmpty())
        assertTrue(clarityRows.none { it == "Good Success" || it == "Excellent Success" }) {
            "divination grades its own margins and must not be relabelled by Appendix A"
        }
    }
}
