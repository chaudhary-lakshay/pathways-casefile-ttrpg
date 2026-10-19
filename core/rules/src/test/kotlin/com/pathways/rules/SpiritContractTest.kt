package com.pathways.rules

import com.pathways.model.BeyonderState
import com.pathways.model.Outcome
import com.pathways.model.RollResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Ch. 9 VI's Spirit World Creature Contract (v6.9i 4078-4122). */
class SpiritContractTest {

    @Test
    fun `the penalty is the Sequence, less 1d6 per creature already under contract`() {
        val faces = ArrayDeque(listOf(4, 2))
        val thrown = mutableListOf<List<Int>>()
        val source = object : RandomSource {
            override fun roll(sides: Int) = faces.removeFirst()
            override fun thrown(faces: List<Int>, target: Int?) { thrown += faces }
        }
        val penalty = SpiritContract.penaltyFor(sequence = 9, held = 2, max = 4, source = source)
        assertEquals(listOf(4, 2), penalty.dice)
        assertEquals(-15, penalty.total)
        // The extra dice reach the roll log, as every throw does (6ht).
        assertEquals(listOf(listOf(4, 2)), thrown)
        assertEquals(-8, SpiritContract.penaltyFor(8, 0, 4) { error("no dice with no contract held") }.total)
        assertEquals(3, SpiritContract.spi())
        // A ritual roll: the CoR ladder reaches it, and ch. 9's burn adds +1 a point.
        assertEquals(16 - 15 + Corruption.ritualPenalty(5) + 2, SpiritContract.targetFor(16, cor = 5, penalty = penalty, burned = 2))
    }

    @Test
    fun `four contracts at most, eight once the walk reaches Scrolls Professor`() {
        assertEquals(4, SpiritContract.maxContracts(BeyonderState("Fool", 9)))
        assertEquals(4, SpiritContract.maxContracts(BeyonderState("Hermit", 7)))
        assertEquals(8, SpiritContract.maxContracts(BeyonderState("Hermit", 6)))
        assertThrows<IllegalArgumentException> { SpiritContract.penaltyFor(9, held = 4, source = { 1 }, max = 4) }
    }

    @Test
    fun `the margin picks the creature's demands`() {
        fun row(margin: Int, outcome: Outcome = Outcome.SUCCESS) =
            SpiritContract.outcomeFor(RollResult(total = 10 - margin, target = 10, margin = margin, outcome = outcome)).printed
        assertEquals("Success by 10+", row(12))
        assertEquals("Success by 7–9", row(7))
        assertEquals("Success by 1–2", row(1))
        assertEquals("Success by 0 (exact)", row(0))
        // A critical success below the target is read as the exact row.
        assertEquals("Success by 0 (exact)", row(-3, Outcome.CRITICAL_SUCCESS))
        assertEquals("Failure", row(-1, Outcome.FAILURE))
        assertEquals("Critical Failure", row(-8, Outcome.CRITICAL_FAILURE))
        assertTrue(SpiritContract.outcomes().size == 8)
    }
}
