package com.pathways.app

import com.pathways.rules.Divination
import com.pathways.rules.Ranged
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The values a printed range offers a GM to pick between.
 *
 * Three screens print ranges -- the divination modifiers, chapter 13's boon
 * modifiers and the ranged darkness row -- and only the boon screen ever let
 * one be picked. The other two applied the row's own first figure, so a chip
 * reading "+0 to +2" added nothing at all.
 */
class ModifierRangeTest {

    @Test
    fun `a row with no second end offers only its own figure`() {
        assertEquals(listOf(1), endsBetween(1, null))
        assertEquals(listOf(-2), endsBetween(-2, null))
    }

    @Test
    fun `an ascending range runs from the row's own figure outward`() {
        // "+0 to +2" is offered 0, 1, 2 -- the row's figure first, because
        // that is what the engine applies when nothing is picked, so the
        // selected chip on an untouched row is the one the roll is using.
        assertEquals(listOf(0, 1, 2), endsBetween(0, 2))
    }

    @Test
    fun `a descending range runs outward too, and that is away from zero`() {
        // "-1 to -2" and "-3 to -9" both worsen as they run. Sorting these
        // numerically would put the WORST case first and make the default
        // chip the last one in the row.
        assertEquals(listOf(-1, -2), endsBetween(-1, -2))
        assertEquals(listOf(-3, -4, -5, -6, -7, -8, -9), endsBetween(-3, -9))
    }

    @Test
    fun `a range of one value is not a range`() {
        assertEquals(listOf(2), endsBetween(2, 2))
    }

    @Test
    fun `every printed divination range is offered, ends included`() {
        // Read off the content rather than typed here: a re-extraction that
        // widens a row must widen what the screen offers, and the engine
        // REFUSES a value outside the printed ends, so an offer that
        // overshot would throw on the Roll button rather than misprice.
        for (row in Divination.modifiers().filter { it.best != null }) {
            val offered = endsBetween(row.modifier!!, row.best)
            assertEquals(row.modifier, offered.first())
            assertEquals(row.best, offered.last())
            for (value in offered) {
                Divination.modifierTotal(listOf(row), mapOf(row.source to value))
            }
        }
    }

    @Test
    fun `the darkness row's whole severity span is offered`() {
        val row = Ranged.conditions().single { it.worstCase != null }
        val offered = endsBetween(row.modifier!!, row.worstCase)
        assertEquals(-3, offered.first())
        assertEquals(-9, offered.last())
        assertEquals(7, offered.size)
    }

    @Test
    fun `a repeat count starts at one, because the row is already the first`() {
        // "-2 per repeat" prices the repeat that is happening, so one repeat
        // is one application. Zero would silently drop a chip the GM ticked.
        val row = Divination.modifiers().single { it.perRepeat }
        assertEquals(-2, Divination.modifierTotal(listOf(row), repeats = 1))
        assertEquals(-6, Divination.modifierTotal(listOf(row), repeats = 3))
        assertThrows<IllegalArgumentException> {
            Divination.modifierTotal(listOf(row), repeats = 0)
        }
        assertEquals(listOf(1, 2, 3, 4), repeatChoices())
    }
}
