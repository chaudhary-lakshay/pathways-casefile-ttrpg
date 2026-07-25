package com.pathways.rules

import com.pathways.model.DiceExpr
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class DiceTest {
    @Test
    fun `bare expression defaults to six sides`() {
        assertEquals(DiceExpr(3, 6, 0), DiceExpr.parse("3d"))
        assertEquals(DiceExpr(1, 6, 0), DiceExpr.parse("1d"))
    }

    @Test
    fun `a trailing minus is a modifier, not a die size`() {
        // The GURPS trap: 1d-3 is 1d6 minus 3, NOT 1d3.
        assertEquals(DiceExpr(1, 6, -3), DiceExpr.parse("1d-3"))
        assertEquals(DiceExpr(1, 6, -6), DiceExpr.parse("1d-6"))
        assertEquals(DiceExpr(5, 6, 2), DiceExpr.parse("5d+2"))
    }

    @Test
    fun `explicit die size is honoured`() {
        assertEquals(DiceExpr(2, 12, 2), DiceExpr.parse("2d12+2"))
        assertEquals(DiceExpr(3, 10, 4), DiceExpr.parse("3d10+4"))
    }

    @Test
    fun `render round-trips`() {
        listOf("3d", "1d-3", "5d+2", "2d12+2").forEach {
            assertEquals(it, DiceExpr.parse(it).render())
        }
    }

    @Test
    fun `garbage is rejected`() {
        assertFailsWith<IllegalArgumentException> { DiceExpr.parse("d6") }
        assertFailsWith<IllegalArgumentException> { DiceExpr.parse("banana") }
    }

    @Test
    fun `rolling sums the dice and applies the modifier`() {
        val alwaysFour = RandomSource { 4 }
        assertEquals(12, DiceExpr(3, 6, 0).roll(alwaysFour))
        assertEquals(1, DiceExpr(1, 6, -3).roll(alwaysFour))
    }

    @Test
    fun `negative results are legal - there is no damage floor in this book`() {
        val alwaysOne = RandomSource { 1 }
        assertEquals(-5, DiceExpr(1, 6, -6).roll(alwaysOne))
    }

    @Test
    fun `seeded rolls are reproducible`() {
        assertEquals(
            DiceExpr(3, 6, 0).roll(SeededRandom(42)),
            DiceExpr(3, 6, 0).roll(SeededRandom(42)),
        )
    }
}
