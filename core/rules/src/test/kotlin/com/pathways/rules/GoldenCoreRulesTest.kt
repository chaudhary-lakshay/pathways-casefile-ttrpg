package com.pathways.rules

import com.pathways.model.Difficulty
import com.pathways.model.Outcome
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Chapter 2 against the engine it was built from (p. 7-10).
 *
 * `SuccessRoll` and `Difficulty.defaultPenalty` have been production code
 * since M0 and were written FROM this chapter, which is exactly why nothing
 * ever compared them to it -- the chapter reads like a summary of the code.
 * This marks that homework, as `GoldenSkillLadderTest` marks M0's skill grid
 * and `GoldenRitualTest` marks chapter 7's worked examples.
 *
 * **It passes on the code as written**, the expected outcome of marking
 * homework. Its value is in what now fails if either side drifts, and in the
 * one thing the chapter turned out to say that a section of this handover had
 * recorded as absent.
 */
class GoldenCoreRulesTest {

    private val bands = ContentLoader.coreMechanic()
    private val defaults = ContentLoader.skillDefaults()
    private val contests = ContentLoader.quickContests()

    // ---- the success roll -------------------------------------------------

    @Test
    fun `the always-rules match SuccessRoll's own thresholds`() {
        val critical = bands.first { it.outcome == "Critical Success" }
        val fumble = bands.first { it.outcome == "Critical Failure" }
        assertTrue(critical.meaning.contains("Roll of 3 or 4"), critical.meaning)
        assertTrue(fumble.meaning.contains("Roll of 17 or 18"), fumble.meaning)
        assertEquals(4, SuccessRoll.ALWAYS_SUCCEEDS_AT_OR_UNDER)
        assertEquals(17, SuccessRoll.ALWAYS_FAILS_AT_OR_OVER)
        // "or fail by 10+" -- the margin door into the same row.
        assertTrue(fumble.meaning.contains("fail by 10+"), fumble.meaning)
        assertEquals(
            Outcome.CRITICAL_FAILURE,
            SuccessRoll.evaluate(total = 15, target = 5).outcome,
        )
    }

    /**
     * **A correction to 6ad, and the reason it was wrong.**
     *
     * That section adopted Appendix A's ladder partly because "Ch. 2 ...
     * tabulates nothing". Chapter 2 tabulates it: the same three edges at
     * 0-2, 3-4 and 5+, under different names. The sentence above the list was
     * read and the list was not, because **this PDF prints list bullets as
     * U+007F** (6g) -- a bulleted list can look like no list at all.
     *
     * The conclusion survives and is stronger. Only Appendix A NAMES the
     * bands, which is what makes it a definition rather than a summary, and
     * chapter 2 independently prints the same edges. Corroboration, not a
     * competing table.
     */
    @Test
    fun `chapter 2 prints the same margin edges Appendix A names`() {
        val success = bands.filter { it.outcome == "Success" }
        assertEquals(listOf(0, 3, 5), success.map { it.low })
        assertEquals(listOf(2, 4, null), success.map { it.high })
        assertEquals(0, DegreeOfSuccess.marginFor(SuccessDegree.PLAIN))
        assertEquals(3, DegreeOfSuccess.marginFor(SuccessDegree.GOOD))
        assertEquals(5, DegreeOfSuccess.marginFor(SuccessDegree.EXCELLENT))
        // ...and chapter 2 does NOT name them, which is 6ad's actual warrant.
        assertTrue(
            success.none { it.meaning.contains("Good Success") },
            "chapter 2 now names a band; 6ad's warrant rested on it not doing so",
        )
    }

    /**
     * "Success by 5+" prints no ceiling, so the band carries none. A large
     * number there would be one this app invented -- the open-cell reading
     * `RitualWeight` already needed, and the reason a margin of 40 still
     * lands on the deepest band rather than falling off the top.
     */
    @Test
    fun `the deepest band has no printed ceiling`() {
        assertNull(bands.first { it.low == 5 && it.outcome == "Success" }.high)
        assertEquals(
            SuccessDegree.EXCELLENT,
            DegreeOfSuccess.of(Outcome.SUCCESS, margin = 40),
        )
    }

    /**
     * Chapter 2 prints a FAILURE band ("Failure by 1-2: Barely failed") that
     * Appendix A's ladder does not. Recorded and applied nowhere: nothing in
     * this app grades a failure, and one band is not a ladder. A finding if
     * it ever gains siblings.
     */
    @Test
    fun `the one failure band is recorded and not graded`() {
        val failures = bands.filter { it.outcome == "Failure" }
        assertEquals(1, failures.size, "chapter 2 grew a failure ladder")
        assertEquals(1, failures.single().low)
        assertEquals(2, failures.single().high)
        assertNull(DegreeOfSuccess.of(Outcome.FAILURE, margin = -1))
    }

    // ---- the defaults -----------------------------------------------------

    /**
     * The book's own table against `Difficulty.defaultPenalty`, which has held
     * these three numbers since M0. Appendix A restates them as a range and
     * 6ae used that restatement as its warrant; this is the primary printing
     * both were taken from.
     */
    @Test
    fun `the untrained defaults match the engine, Very Hard included`() {
        assertEquals(listOf(-4, -5, -6, null), defaults.map { it.penalty })
        // The engine's own answer, through the ladder rather than the table.
        assertEquals(5, SkillLadder.defaultFor(Difficulty.AVERAGE, attributeLevel = 10))
        assertEquals(6, SkillLadder.defaultFor(Difficulty.EASY, attributeLevel = 10))
        assertEquals(4, SkillLadder.defaultFor(Difficulty.HARD, attributeLevel = 10))
        assertNull(SkillLadder.defaultFor(Difficulty.VERY_HARD, attributeLevel = 10))
    }

    // ---- the contests -----------------------------------------------------

    /**
     * **Not resolved here, and the book is not why.** Several sections record
     * a Quick Contest as "deliberately not modelled" (Feint in 6o, six more in
     * 6ah), and the reason given each time -- this app holds one character
     * sheet -- is still true. What was implied and is NOT true is that the
     * book leaves the procedure unstated. It prints six worked rows and a
     * complete resolution rule, so they are carried for the GM to read.
     */
    @Test
    fun `the six printed contests are carried, and one of them is this app's caster`() {
        assertEquals(6, contests.size)
        assertEquals("Deception vs. detection", contests.first().situation)
        assertTrue(
            contests.any { it.attacker == "Ritualistic Magic" },
            "the ritual contest row is gone; it is the one this app can reach",
        )
        contests.forEach {
            assertTrue(it.attacker.isNotBlank() && it.defender.isNotBlank(), it.situation)
        }
    }
}
