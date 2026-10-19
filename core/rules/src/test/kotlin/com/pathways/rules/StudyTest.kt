package com.pathways.rules

import com.pathways.model.Difficulty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Downtime study (Ch. 3, p. 32).
 *
 * The cases worth pinning are the three refusals -- each is a sentence the
 * book prints, and each would read perfectly plausibly as a number instead.
 */
class StudyTest {

    private fun reasonOf(result: Result<StudyPlan>): String =
        (result.exceptionOrNull() as StudyRefusedException).reason

    // ---- the table -------------------------------------------------------

    @Test
    fun `four difficulties, both roads, and one cell that is not a price`() {
        assertEquals(4, Study.costs().size)
        assertTrue(Study.isPriced(Difficulty.HARD, StudyMethod.SELF_STUDY))
        assertFalse(Study.isPriced(Difficulty.VERY_HARD, StudyMethod.SELF_STUDY))
    }

    /**
     * "Not possible" is a refusal, not a price of zero. Read as free it would
     * make the hardest road in the table the cheapest -- the distinction
     * `RitualCritical.corGained` draws, with teeth this time.
     */
    @Test
    fun `the impossible cell carries no cost and refuses to be quoted`() {
        val cell = Study.cellFor(Difficulty.VERY_HARD, StudyMethod.SELF_STUDY)
        assertEquals("Not possible", cell.printed)
        assertNull(cell.hours)
        assertNull(cell.amount)
        assertThrows<IllegalArgumentException> {
            Study.costPerPoint(Difficulty.VERY_HARD, StudyMethod.SELF_STUDY)
        }

        val refused = Study.plan(0, Difficulty.VERY_HARD, 10, StudyMethod.SELF_STUDY)
        assertTrue(refused.isFailure)
        assertTrue(reasonOf(refused).contains("Not possible"), reasonOf(refused))
    }

    /** Very Hard WITH a tutor is priced, so the refusal is the road, not the skill. */
    @Test
    fun `the same skill is studiable with a tutor`() {
        val plan = Study.plan(0, Difficulty.VERY_HARD, 10, StudyMethod.TUTOR).getOrThrow()
        assertEquals("48 hrs + £2", plan.printed)
    }

    /** Two currencies in one column; nothing here converts between them (6u). */
    @Test
    fun `the money is quoted in the currency the book prints it in`() {
        assertEquals("s", Study.costPerPoint(Difficulty.EASY, StudyMethod.TUTOR).unit)
        assertEquals("£", Study.costPerPoint(Difficulty.HARD, StudyMethod.TUTOR).unit)
        assertEquals(
            "6 hrs + 4s",
            Study.plan(0, Difficulty.EASY, 10, StudyMethod.TUTOR).getOrThrow().printed,
        )
    }

    // ---- the cap ---------------------------------------------------------

    @Test
    fun `study alone stops at Attribute plus one`() {
        assertEquals(11, Study.capFor(10))
        // An Easy skill reaches Attr+1 on its SECOND rung, so the cap bites at
        // a different point total per difficulty -- which is why it is computed
        // from the level and never from the points.
        assertEquals(11, SkillLadder.levelFor(2, Difficulty.EASY, 10))
        assertTrue(Study.plan(2, Difficulty.EASY, 10, StudyMethod.TUTOR).isFailure)
        assertTrue(Study.plan(1, Difficulty.EASY, 10, StudyMethod.TUTOR).isSuccess)
    }

    /**
     * A skill ALREADY bought past the cap is offered nothing rather than more.
     *
     * "Through study alone" does not settle what happens to a skill whose
     * levels were paid for at creation, so the smaller reading is taken: study
     * adds nothing at or above Attr+1. Granting rungs there would let study
     * reach a level the sentence never promises.
     */
    @Test
    fun `a skill bought past the cap is refused, not topped up`() {
        val bought = SkillLadder.levelFor(8, Difficulty.AVERAGE, 10)
        assertEquals(12, bought, "two rungs past the study cap")
        val refused = Study.plan(8, Difficulty.AVERAGE, 10, StudyMethod.TUTOR)
        assertTrue(refused.isFailure)
        assertTrue(reasonOf(refused).contains("already 12"), reasonOf(refused))
    }

    // ---- the potion refusal ----------------------------------------------

    /**
     * "Beyonder potion-granted skills (Chapters 9-11) cannot be studied."
     *
     * Prohibition language, and the app contradicted nothing only because
     * study did not exist. The moment the planner ships this is a live gate.
     */
    @Test
    fun `a potion-granted skill cannot be studied by either road`() {
        StudyMethod.entries.forEach { method ->
            val refused = Study.plan(0, Difficulty.AVERAGE, 10, method, isPotionGranted = true)
            assertTrue(refused.isFailure, "$method")
            assertTrue(reasonOf(refused).contains("potion"), reasonOf(refused))
        }
        // The identical skill is studiable when no potion granted it.
        assertTrue(Study.plan(0, Difficulty.AVERAGE, 10, StudyMethod.TUTOR).isSuccess)
    }

    // ---- what is shown and never enforced --------------------------------

    /**
     * The tutor's requirements and the two-skills-per-period limit are printed
     * and deliberately unapplied: this app models no tutor and no downtime
     * period. They are carried as sentences so the GM can see them, the call
     * the Sealed Artifact grade table needed (6af).
     */
    @Test
    fun `the rules this app cannot enforce are still carried`() {
        val body = Study.rules().joinToString(" ") { it.rule }
        assertTrue(body.contains("Attribute+2 or higher"), body)
        assertTrue(body.contains("Max 3 students"), body)
        assertTrue(body.contains("two skills may be studied per downtime period"), body)
        // And the one that IS applied is printed here too.
        assertTrue(body.contains("cannot exceed Attribute+1 through study alone"), body)
    }

    @Test
    fun `the worked example's own paragraph did not leak into the rules`() {
        // The example wraps across three lines directly above the Limits and
        // is not bulleted, which is how the extractor tells them apart.
        assertEquals(5, Study.rules().size)
        assertTrue(Study.rules().none { it.rule.contains("Guns-9") })
    }

    // ---- boundaries ------------------------------------------------------

    @Test
    fun `negative points are refused rather than planned from`() {
        assertThrows<IllegalArgumentException> {
            Study.plan(-1, Difficulty.EASY, 10, StudyMethod.TUTOR)
        }
    }
}
