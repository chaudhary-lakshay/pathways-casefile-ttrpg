package com.pathways.app

import com.pathways.model.BeyonderState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Frames 10-11 as the owner ruled them (2026-10-18): Drink offers the NEXT
 * potion of the walked pathway on every rung of it, so tapping a tile only
 * changes what is read.
 */
class PathwayDrinkTest {

    private val printed = { s: BeyonderState -> s.sequence in 6..9 }

    @Test
    fun `a walker is offered the next potion whichever rung is browsed`() {
        val drunk = BeyonderState("Fool", 9, digestion = 40)
        val next = BeyonderState("Fool", 8, digestion = 0)
        assertEquals(next, drinkTarget("Fool", 9, drunk, printed))
        assertEquals(next, drinkTarget("Fool", 6, drunk, printed))
    }

    @Test
    fun `the next potion keeps the walked chain's drawback`() {
        val drunk = BeyonderState("Hanged Man", 9, drawback = "Paranoia")
        assertEquals("Paranoia", drinkTarget("Hanged Man", 7, drunk, printed)?.drawback)
    }

    @Test
    fun `another pathway offers a walker nothing`() {
        assertNull(drinkTarget("Sun", 8, BeyonderState("Fool", 9), printed))
    }

    @Test
    fun `no potion is offered past the last printed rung`() {
        assertNull(drinkTarget("Fool", 6, BeyonderState("Fool", 6), printed))
    }

    @Test
    fun `a mortal is offered the browsed pathway's first potion only`() {
        assertEquals(BeyonderState("Sun", 9), drinkTarget("Sun", 9, null, printed))
        assertNull(drinkTarget("Sun", 8, null, printed))
    }

    @Test
    fun `the caption names the potion before the roll`() {
        assertEquals(
            "next: Sequence 8 of the Fool Pathway · at 40% digested, rolls 3d6 ≤ 8 — " +
                "on success +10 CoR minimum; the GM may charge more",
            drinkCaption(BeyonderState("Fool", 8), BeyonderState("Fool", 9, 40), cor = 0, ceilingAfter = 30),
        )
    }

    @Test
    fun `a mortal's caption is the refusal alone, or nothing`() {
        assertNull(drinkCaption(BeyonderState("Sun", 9), null, cor = 0, ceilingAfter = 10))
        assertEquals(
            drinkLine(null, 0, 0),
            drinkCaption(BeyonderState("Abyss", 9), null, cor = 0, ceilingAfter = 0),
        )
    }

    @Test
    fun `the digestion bar fills a cell per whole ten percent`() {
        assertEquals(listOf(0, 4, 4, 10), listOf(0, 40, 45, 100).map(::digestionCells))
    }

    @Test
    fun `the corruption ladder prints all four rungs and marks the reached`() {
        val ladder = corLadder(5)
        assertEquals(listOf(3, 5, 8, 10), ladder.map { it.first.cor })
        assertEquals(listOf(true, true, false, false), ladder.map { it.second })
    }
}
