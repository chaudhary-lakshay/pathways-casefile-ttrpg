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
    fun `every printed divination range is offered, ends included`() {
        // Read off the content rather than typed here: a re-extraction that
        // widens a row must widen what the screen offers, and the engine
        // REFUSES a value outside the printed ends, so an offer that
        // overshot would throw on the Roll button rather than misprice.
        //
        // The SET is pinned first, because a filter that goes empty passes
        // every assertion below it -- a re-extraction dropping `best` from
        // all four rows would silently stop testing anything, which is this
        // repo's oldest way of shipping a guard that cannot fire.
        val ranged = Divination.modifiers().filter { it.best != null }
        assertEquals(
            listOf(
                "Method match (right tool for the question)",
                "Target link (true name, possession, blood)",
                "Unfavourable timing (wrong phase, entity's off-day)",
                "Quiet, dedicated space",
            ),
            ranged.map { it.source },
        )
        for (row in ranged) {
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

    @Test
    fun `a pick reaches the roll only while its own row is ticked`() {
        // The picker's whole wiring: without it the divination screen applies
        // each row's first figure, which is what "+0 to +2" adding nothing
        // looked like. And a value left behind by unticking must not price a
        // row the GM has not re-ticked.
        val method = Divination.modifiers().single { it.source.startsWith("Method match") }
        val link = Divination.modifiers().single { it.source.startsWith("Target link") }
        val picked = mapOf(method.source to 2, link.source to 4)

        val both = overridesFor(listOf(method, link), picked)
        assertEquals(6, Divination.modifierTotal(listOf(method, link), both))

        // Only the link is ticked now: the method's 2 is held, not applied.
        val linkOnly = overridesFor(listOf(link), picked)
        assertEquals(mapOf(link.source to 4), linkOnly)
        assertEquals(4, Divination.modifierTotal(listOf(link), linkOnly))

        // And with nothing picked the engine takes each row's own figure --
        // the behaviour this slice exists to stop being the only one.
        assertEquals(0, Divination.modifierTotal(listOf(method, link), emptyMap()))
    }
}
