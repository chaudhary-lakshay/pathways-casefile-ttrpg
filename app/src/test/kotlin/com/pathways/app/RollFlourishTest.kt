package com.pathways.app

import com.pathways.model.Outcome
import com.pathways.model.RollResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A verdict's flourish: which of four tiers plays, read off what the card shows. */
class RollFlourishTest {

    private fun roll(outcome: Outcome) = RollResult(total = 10, target = 12, margin = 2, outcome = outcome)

    @Test
    fun `a critical plays its own tier only when the verdict agrees with the dice`() {
        assertEquals(Tier.CRITICAL_SUCCESS, Tier.of(passed = true, roll(Outcome.CRITICAL_SUCCESS)))
        assertEquals(Tier.CRITICAL_FAILURE, Tier.of(passed = false, roll(Outcome.CRITICAL_FAILURE)))
    }

    @Test
    fun `a success the card shows as a failure plays a failure`() {
        // A critical-only divination demotes an ordinary success (6dz); the
        // card says Failure, so a success sting would contradict it.
        assertEquals(Tier.FAILURE, Tier.of(passed = false, roll(Outcome.SUCCESS)))
        assertEquals(Tier.FAILURE, Tier.of(passed = false, roll(Outcome.CRITICAL_SUCCESS)))
    }

    @Test
    fun `a plain verdict plays the plain tier`() {
        assertEquals(Tier.SUCCESS, Tier.of(passed = true, roll(Outcome.SUCCESS)))
        assertEquals(Tier.FAILURE, Tier.of(passed = false, roll(Outcome.FAILURE)))
        // "Held the focus" can be read off a roll the dice call a failure.
        assertEquals(Tier.SUCCESS, Tier.of(passed = true, roll(Outcome.CRITICAL_FAILURE)))
    }
}
