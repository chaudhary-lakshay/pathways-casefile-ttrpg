package com.pathways.rules

import com.pathways.model.Outcome
import kotlin.test.assertEquals
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
}
