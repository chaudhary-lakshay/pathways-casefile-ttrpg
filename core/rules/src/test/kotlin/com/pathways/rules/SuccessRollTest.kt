package com.pathways.rules

import com.pathways.model.Outcome
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SuccessRollTest {

    @Test
    fun `rolling under the target succeeds with a positive margin`() {
        val r = SuccessRoll.evaluate(total = 9, target = 13)
        assertEquals(Outcome.SUCCESS, r.outcome)
        assertEquals(4, r.margin)
    }

    @Test
    fun `rolling exactly the target succeeds`() {
        assertEquals(Outcome.SUCCESS, SuccessRoll.evaluate(13, 13).outcome)
        assertEquals(0, SuccessRoll.evaluate(13, 13).margin)
    }

    @Test
    fun `three and four are always critical successes`() {
        assertEquals(Outcome.CRITICAL_SUCCESS, SuccessRoll.evaluate(3, 5).outcome)
        assertEquals(Outcome.CRITICAL_SUCCESS, SuccessRoll.evaluate(4, 5).outcome)
    }

    @Test
    fun `three is a critical success even against an impossible target`() {
        assertEquals(Outcome.CRITICAL_SUCCESS, SuccessRoll.evaluate(3, 1).outcome)
    }

    @Test
    fun `seventeen and eighteen are always critical failures`() {
        assertEquals(Outcome.CRITICAL_FAILURE, SuccessRoll.evaluate(17, 18).outcome)
        assertEquals(Outcome.CRITICAL_FAILURE, SuccessRoll.evaluate(18, 18).outcome)
    }

    @Test
    fun `failing by ten or more is a critical failure`() {
        assertEquals(Outcome.CRITICAL_FAILURE, SuccessRoll.evaluate(16, 6).outcome)
        assertEquals(Outcome.FAILURE, SuccessRoll.evaluate(15, 6).outcome)
    }

    @Test
    fun `a plain miss is an ordinary failure with a negative margin`() {
        val r = SuccessRoll.evaluate(12, 10)
        assertEquals(Outcome.FAILURE, r.outcome)
        assertEquals(-2, r.margin)
    }

    @Test
    fun `rolling against a target is reproducible under a seed`() {
        assertEquals(
            SuccessRoll.against(12, SeededRandom(7)),
            SuccessRoll.against(12, SeededRandom(7)),
        )
    }

    /**
     * What a screen prints under a target: the totals that succeed, which are
     * not "3d6 <= target" past either end. Play session 4 read "3d6 <= 18"
     * on a boon a 17 or 18 fails. Checked against [SuccessRoll.evaluate] for
     * every target a screen can draw, not against the helper's own clamp.
     */
    @Test
    fun `the printed roll names the totals that actually succeed`() {
        assertEquals("3d6 ≤ 12", SuccessRoll.printed(12))
        assertEquals("3d6 ≤ 16", SuccessRoll.printed(16))
        assertEquals("3d6 ≤ 16, 17–18 fail", SuccessRoll.printed(17))
        assertEquals("3d6 ≤ 16, 17–18 fail", SuccessRoll.printed(18))
        assertEquals("3d6 ≤ 4", SuccessRoll.printed(4))
        assertEquals("3d6: a 3 or 4 only", SuccessRoll.printed(3))
        assertEquals("3d6: a 3 or 4 only", SuccessRoll.printed(-2))
        // A Sequence 0 boon's doubling reaches -512 (Codex's regression,
        // once BoonDialog's own targetLine): still a 3 or a 4, not hopeless.
        assertEquals("3d6: a 3 or 4 only", SuccessRoll.printed(Boons.penaltyForSequence(0)))
        for (target in -5..30) {
            val best = (3..18).filter { SuccessRoll.evaluate(it, target).outcome.isSuccess }.max()
            val printed = SuccessRoll.printed(target)
            assertTrue("≤ $best" in printed || (best == 4 && "3 or 4" in printed), "$target: $printed")
        }
    }
}
