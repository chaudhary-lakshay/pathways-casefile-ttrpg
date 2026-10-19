package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Ch. 2's Basic Lift & Carrying Capacity (v6.9i 400-427). */
class EncumbranceTest {

    @Test
    fun `Basic Lift is ST times ST over five, with Lifting ST added for carrying`() {
        assertEquals(20.0, Encumbrance.basicLift(10, emptyList()))
        assertEquals(16.2, Encumbrance.basicLift(9, emptyList()), 1e-9)
        // "If you have Lifting ST ... add it to your ST before calculating BL."
        val lifting = listOf(Trait("${Encumbrance.LIFTING_ST} 2", 6, TraitKind.ADVANTAGE))
        assertEquals(24.2, Encumbrance.basicLift(9, lifting), 1e-9)
        assertTrue(Encumbrance.LIFTING_ST in TraitEffects.known())
    }

    @Test
    fun `the load picks the first level whose limit holds it`() {
        // ST 10: BL 20 lbs. "BL or less" is None; "Up to 2 x BL" is Light.
        assertEquals("None", Encumbrance.levelFor(20.0, 20.0)!!.name)
        assertEquals("Light", Encumbrance.levelFor(20.5, 20.0)!!.name)
        val overload = Encumbrance.levelFor(300.0, 20.0)!!
        assertEquals(-5, overload.movePenalty)
        assertEquals(-5, overload.dodgePenalty)
        assertTrue(overload.cannotRun)
        // Past 15 x BL the table prints no level: the load cannot be carried.
        assertNull(Encumbrance.levelFor(300.5, 20.0))
        assertThrows<IllegalArgumentException> { Encumbrance.levelFor(-1.0, 20.0) }
    }
}
