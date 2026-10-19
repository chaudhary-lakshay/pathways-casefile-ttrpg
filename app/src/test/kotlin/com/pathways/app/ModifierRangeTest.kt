package com.pathways.app

import com.pathways.rules.Divination
import com.pathways.rules.Ranged
import com.pathways.rules.RitualMode
import com.pathways.rules.content.TargetLink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The values a printed range offers a GM to pick between.
 *
 * When this file was written three screens printed ranges -- the divination
 * modifiers, chapter 13's boon modifiers and the ranged darkness row -- and
 * only the boon screen let one be picked. The other two applied the row's own
 * first figure, so a chip reading "+0 to +2" added nothing at all. The ritual
 * screen was a fourth doing the same thing until 6ef, with six cells.
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

    /**
     * Chapter 7's three tables: six cells that are not one number. The chips
     * are the only way a GM reaches the far end of one, and `targetFor` runs
     * while the dialog DRAWS -- so an offered value the engine refuses would
     * crash the screen rather than misprice a roll.
     */
    @Test
    fun `every value a ritual cell offers is one the engine takes, its own figure first`() {
        val preparations = (RitualMode.FULL.preparations() + RitualMode.QUICK.preparations())
            .filter { it.best != null || it.openEnded }
        assertEquals(
            listOf("Good +1 to +2", "Poor -1 to -2", "None -4 or worse", "Poor / None -2 to -4"),
            preparations.map { "${it.quality} ${it.printed}" },
        )
        for (row in preparations) {
            val offered = valuesOf(row.modifier, row.best, row.openEnded)
            assertTrue(offered.size > 1, "${row.quality} offers a choice")
            assertEquals(RitualMode.valueFor(row), offered.first(), "unpicked is the engine's default")
            row.best?.let { assertEquals(it, offered.last()) }
            offered.forEach { assertEquals(it, RitualMode.valueFor(row, it)) }
        }
        // The INTERIOR, which first-and-last cannot see: "-2 to -4" prints -3,
        // and an offer of the two ends alone passes everything above.
        val poorOrNone = RitualMode.QUICK.preparationNamed("Poor/None")
        assertEquals(
            listOf(-2, -3, -4),
            valuesOf(poorOrNone.modifier, poorOrNone.best, poorOrNone.openEnded),
        )

        val links = RitualMode.links().filter { it.best != null }
        assertEquals(
            listOf("Strong +2 to +3", "Weak +0 to -1"),
            links.map { "${it.link} ${it.printed}" },
        )
        for (row in links) {
            val offered = valuesOf(row.modifier, row.best, openEnded = false)
            assertEquals(RitualMode.valueFor(row), offered.first())
            assertEquals(row.best, offered.last())
            offered.forEach { assertEquals(it, RitualMode.valueFor(row, it)) }
        }
    }

    /**
     * "-4 or worse" names no floor, so three steps is this screen's limit and
     * not the book's: the engine takes -9 as readily as -7.
     */
    @Test
    fun `an open end offers its printed figure and three steps away from zero`() {
        assertEquals(listOf(-4, -5, -6, -7), endsBeyond(-4))
        assertEquals(listOf(4, 5, 6, 7), endsBeyond(4))
        assertEquals(endsBeyond(-4), valuesOf(-4, to = null, openEnded = true))
        assertEquals(listOf(1, 2), valuesOf(1, to = 2, openEnded = false))
        assertEquals(listOf(3), valuesOf(3, to = null, openEnded = false))
    }

    /**
     * A pick is held WITH the row it was made inside, and read back only for
     * that row, so Good's +2 can never be handed to the engine beside
     * Excellent, which refuses it (6ef's diff review).
     */
    @Test
    fun `a pick does not follow the GM onto another row`() {
        val good = RitualMode.FULL.preparationNamed("Good")
        val excellent = RitualMode.FULL.preparationNamed("Excellent")
        assertEquals(2, pickInside(good, good to 2))
        // What the dialog hands the engine after the switch: Excellent's own +3,
        // where the bare 2 is a refusal.
        assertEquals(3, RitualMode.valueFor(excellent, pickInside(excellent, good to 2)))
        assertThrows<IllegalArgumentException> { RitualMode.valueFor(excellent, 2) }

        // A link switched off is no row at all, and the engine refuses a pick
        // with no row to pick it inside.
        val strong = RitualMode.linkNamed("Strong")
        val off: TargetLink? = null
        assertEquals(0, RitualMode.valueFor(off, pickInside(off, strong to 3)))
        assertThrows<IllegalArgumentException> { RitualMode.valueFor(off, 3) }
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
