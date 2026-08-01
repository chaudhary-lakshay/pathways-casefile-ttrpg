package com.pathways.rules

import com.pathways.model.ModEffect
import com.pathways.model.SourceKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Calling a shot: what it costs to hit, and what it multiplies when it lands.
 *
 * The interesting cases are the two rows whose multiplier is not one number,
 * the types the book leaves unlisted, and the fact that the two tables
 * MULTIPLY rather than one replacing the other.
 */
class WoundsTest {

    private fun location(target: String) = Wounds.locationNamed(target)

    @Test
    fun `the torso is the default and costs nothing`() {
        val torso = location("Torso")
        assertEquals(0, torso.penalty)
        assertEquals(1.0, Wounds.multiplierFor(torso, "cr"))
        assertNull(Wounds.modifier(torso), "a +0 penalty is not a modifier")
    }

    @Test
    fun `calling a shot is a situational penalty on the roll`() {
        val skull = Wounds.modifier(location("Skull"))!!
        assertEquals(-7, (skull.effect as ModEffect.Flat).n)
        assertEquals(SourceKind.SITUATIONAL, skull.source.kind)
    }

    @Test
    fun `no location is easier to hit than the torso`() {
        assertTrue(Wounds.locations().all { it.penalty <= 0 }, "a called shot never helps")
    }

    @Test
    fun `the book's own worked example - a cutting neck hit is times three`() {
        // "a cutting hit to the Neck (Cut x2) with a sword (Cut x1.5) applies
        // x3 total to penetrating damage." The two tables stack; neither
        // replaces the other.
        assertEquals(3.0, Wounds.multiplierFor(location("Neck"), "cut"))
    }

    @Test
    fun `the neck's multiplier varies by damage type`() {
        val neck = location("Neck")
        // Cr x1.5 * cr x1, Cut x2 * cut x1.5, Imp x2 * imp x2.
        assertEquals(1.5, Wounds.multiplierFor(neck, "cr"))
        assertEquals(3.0, Wounds.multiplierFor(neck, "cut"))
        assertEquals(4.0, Wounds.multiplierFor(neck, "imp"))
    }

    @Test
    fun `a real type the neck row does not list takes the plain multiplier`() {
        // The book prints Cr, Cut and Imp there and is silent about piercing.
        // x1 is the reading; guessing which listed type it is nearest to would
        // be inventing a row.
        val neck = location("Neck")
        assertEquals(setOf("cr", "cut", "imp"), neck.byType.keys)
        assertEquals(1.0, Wounds.multiplierFor(neck, "pi"))
    }

    @Test
    fun `a key that is not a damage type is refused, not read as times one`() {
        // The two cases look identical in byType, and only one is a fallback:
        // "pi" is a type the Neck row leaves out, "pi-" is not a type at all
        // (Codex). Resolving through the map alone would answer x1 for both.
        assertThrows<IllegalArgumentException> { Wounds.multiplierFor(location("Neck"), "pi-") }
        assertThrows<IllegalArgumentException> { Wounds.multiplierFor(null, "nonsense") }
    }

    @Test
    fun `a punch to the vitals is not tripled`() {
        // "x3 (imp/pi only)" means it. Reading x3 and dropping the bracket
        // would triple every crushing hit the book leaves at x1.
        assertEquals(1.0, Wounds.multiplierFor(location("Vitals"), "cr"))
    }

    @Test
    fun `an impaling hit to the vitals stacks both multipliers`() {
        // x3 for the location and x2 for the type. GURPS proper uses the
        // location value INSTEAD of the type's; this book says they stack and
        // prints a worked example saying so, so the book wins at x6.
        assertEquals(6.0, Wounds.multiplierFor(location("Vitals"), "imp"))
    }

    @Test
    fun `the damage types the book abbreviates resolve, and no others`() {
        assertEquals(1.0, Wounds.typeMultiplier("cr"))
        assertEquals(1.5, Wounds.typeMultiplier("cut"))
        assertEquals(2.0, Wounds.typeMultiplier("imp"))
        assertEquals(1.0, Wounds.typeMultiplier("pi"))
        // "pi-" is how GURPS writes Piercing (small); this book never does.
        val ex = assertThrows<IllegalArgumentException> { Wounds.typeMultiplier("pi-") }
        assertTrue(ex.message!!.contains("pi-"))
    }

    @Test
    fun `the unabbreviated rows are still carried, just not keyed`() {
        val small = ContentLoader.woundingMultipliers().first { it.type == "Piercing (small)" }
        assertEquals(0.5, small.multiplier)
        assertNull(small.abbreviation)
    }

    @Test
    fun `every unarmed attack in this book resolves against the type table`() {
        // If a cut or imp row ever appears, this test keeps it honest rather
        // than letting an unknown key reach a lookup at render time.
        ContentLoader.unarmedAttacks().mapNotNull { it.type }.distinct().forEach {
            Wounds.typeMultiplier(it)
        }
    }

    @Test
    fun `an unknown location is refused, not defaulted to the torso`() {
        assertThrows<IllegalArgumentException> { Wounds.locationNamed("Wing") }
    }

    @Test
    fun `a whole multiplier prints without a decimal tail`() {
        assertEquals("×3", Wounds.render(3.0))
        assertEquals("×1.5", Wounds.render(1.5))
        assertEquals("×0.5", Wounds.render(0.5))
    }
}
