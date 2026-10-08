package com.pathways.app

import com.pathways.model.BeyonderState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PathwayScreenTest {

    private fun fool(digestion: Int) = BeyonderState("Fool", 9, digestion)

    @Test
    fun `the roll is printed with what advancing early costs`() {
        assertEquals(
            "3d6 ≤ 8 — on success +10 CoR minimum; the GM may charge more",
            pcrLine(fool(40)),
        )
    }

    @Test
    fun `a fully digested potion prints no cost`() {
        assertEquals("3d6 ≤ 19", pcrLine(fool(100)))
    }

    @Test
    fun `the Drink button prints the roll the tracker printed`() {
        // The tracker is drawn on the potion being digested and Drink on the
        // next block, so the roll was printed everywhere except beside the
        // button that makes it.
        val line = drinkLine(fool(40), cor = 0, ceilingAfter = 30)
        assertTrue(pcrLine(fool(40)) in line, line)
        assertTrue("40% digested" in line, line)
        assertFalse("lost to corruption" in line, line)
    }

    @Test
    fun `an advancement whose cost reaches the ceiling says it ends the character`() {
        // The book's floor of 10 CoR against a Will of 10: the roll can be
        // survived and the character lost on the same tap.
        val line = drinkLine(fool(40), cor = 0, ceilingAfter = 10)
        assertTrue("Maximum CoR after this potion is 10" in line, line)
        assertTrue("lost to corruption" in line, line)
        assertFalse("lost to corruption" in drinkLine(fool(40), cor = 0, ceilingAfter = 11))
        // The total it names is the one after the charge, not the one before.
        assertTrue("; 13 CoR reaches it" in drinkLine(fool(40), cor = 3, ceilingAfter = 12))
    }

    @Test
    fun `a potion that lowers the ceiling onto the total says so at no cost`() {
        val line = drinkLine(fool(100), cor = 9, ceilingAfter = 9)
        assertTrue("3d6 ≤ 19" in line, line)
        assertTrue("lost to corruption" in line, line)
        assertFalse("lost to corruption" in drinkLine(fool(100), cor = 8, ceilingAfter = 9))
    }
}
