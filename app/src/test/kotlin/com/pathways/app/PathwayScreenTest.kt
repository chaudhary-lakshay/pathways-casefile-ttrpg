package com.pathways.app

import com.pathways.model.BeyonderState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PathwayScreenTest {

    private fun fool(digestion: Int) = BeyonderState("Fool", 9, digestion)

    @Test
    fun `the roll is printed with what advancing early costs`() {
        assertEquals("3d6 ≤ 8 — on success +1d6 CoR", pcrLine(fool(40), cor = 0))
        // v6.9i: -1 to the PCR per point of current CoR.
        assertEquals("3d6 ≤ 6 — on success +1d6 CoR", pcrLine(fool(40), cor = 2))
    }

    @Test
    fun `a fully digested potion prints no cost`() {
        assertEquals("3d6 ≤ 19", pcrLine(fool(100), cor = 0))
    }

    @Test
    fun `the Drink button prints the roll the tracker printed`() {
        // The tracker is drawn on the potion being digested and Drink on the
        // next block, so the roll was printed everywhere except beside the
        // button that makes it.
        val line = drinkLine(fool(40), cor = 0, ceilingAfter = 30)!!
        assertTrue(pcrLine(fool(40), cor = 0) in line, line)
        assertTrue("40% digested" in line, line)
        assertFalse("lost to corruption" in line, line)
    }

    @Test
    fun `an advancement whose cost reaches the ceiling says it ends the character`() {
        // The 1d6 CoR against the ceiling: the roll can be survived and the
        // character lost on the same tap. Even a 1 reaches it here.
        val line = drinkLine(fool(40), cor = 5, ceilingAfter = 6)!!
        assertTrue("Maximum CoR after this potion is 6" in line, line)
        // The total it names is the one after the least charge, not before.
        assertTrue("; 6 CoR reaches it" in line, line)
        assertTrue("lost to corruption even if the potion integrates" in line, line)
    }

    @Test
    fun `an advancement the die may push to the ceiling names the roll that does`() {
        val line = drinkLine(fool(40), cor = 3, ceilingAfter = 7)!!
        assertTrue("a 4 or more on the 1d6 reaches it" in line, line)
        assertFalse("even if the potion integrates" in line, line)
        // Out of the die's reach: nothing to warn about.
        assertFalse("Maximum CoR" in drinkLine(fool(40), cor = 3, ceilingAfter = 10)!!)
    }

    @Test
    fun `a ceiling below one is printed as the reason the potion is refused`() {
        // REGRESSION (review of 6ej): `Corruption` refuses a ceiling below 1
        // by throwing, and this line is built during composition -- so
        // BROWSING to the next block would crash. Drink refuses such a potion
        // now, and this line is the reason the button gives.
        val line = drinkLine(fool(40), cor = 0, ceilingAfter = 0)!!
        assertTrue(pcrLine(fool(40), cor = 0) in line, line)
        assertTrue("CoR ceiling after this potion would be 0" in line, line)
        assertTrue("cannot be drunk" in line, line)
        assertFalse("lost to corruption" in line, line)
        assertTrue("would be -3," in drinkLine(fool(100), cor = 0, ceilingAfter = -3)!!)
    }

    @Test
    fun `a first potion prints nothing unless it is refused`() {
        // A mortal makes no roll, so there is nothing to print -- except why
        // the button will not drink (Abyss Sequence 9 lowers Will).
        assertNull(drinkLine(null, cor = 0, ceilingAfter = 9))
        val refused = drinkLine(null, cor = 0, ceilingAfter = 0)!!
        assertTrue("cannot be drunk" in refused, refused)
        assertFalse("3d6" in refused, refused)
    }

    @Test
    fun `a potion that lowers the ceiling onto the total says so at no cost`() {
        val line = drinkLine(fool(100), cor = 9, ceilingAfter = 9)!!
        // 19 less the 9 CoR already carried.
        assertTrue("3d6 ≤ 10" in line, line)
        assertTrue("lost to corruption" in line, line)
        assertFalse("lost to corruption" in drinkLine(fool(100), cor = 8, ceilingAfter = 9)!!)
    }
}
