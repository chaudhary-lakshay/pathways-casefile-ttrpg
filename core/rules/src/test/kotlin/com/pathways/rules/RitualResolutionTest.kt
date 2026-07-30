package com.pathways.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RitualResolutionTest {

    private fun fixed(face: Int) = RandomSource { face }

    private fun scripted(vararg faces: Int): RandomSource {
        var next = 0
        return RandomSource { faces[next++.coerceAtMost(faces.size - 1)] }
    }

    private val heavy get() = RitualResolution.weights().single { it.weight == "Heavy" }
    private val light get() = RitualResolution.weights().single { it.weight == "Light" }

    @Test
    fun `the ritual's weight moves the target`() {
        // p. 67: Light +0, Moderate -2, Heavy -4.
        assertEquals(14, RitualResolution.targetFor(skillLevel = 14, weight = light, cor = 0))
        assertEquals(10, RitualResolution.targetFor(skillLevel = 14, weight = heavy, cor = 0))
    }

    @Test
    fun `corruption penalises the ritual roll and this is its first caller`() {
        // p. 68: "3 CoR -- All ritual rolls at -1 from spiritual instability."
        assertEquals(14, RitualResolution.targetFor(skillLevel = 14, weight = light, cor = 2))
        assertEquals(13, RitualResolution.targetFor(skillLevel = 14, weight = light, cor = 3))
        assertEquals(9, RitualResolution.targetFor(skillLevel = 14, weight = heavy, cor = 9))
    }

    @Test
    fun `a success costs nothing and consults no table`() {
        val outcome = RitualResolution.attempt(14, light, cor = 0, source = fixed(1))
        assertTrue(outcome.succeeded)
        assertNull(outcome.consequence)
        assertNull(outcome.critical)
        assertEquals(0, outcome.marginOfFailure)
    }

    @Test
    fun `a small miss is the mildest consequence`() {
        // 3+3+3 = 9 against a target of 8: failure by 1.
        val outcome = RitualResolution.attempt(8, light, cor = 0, source = fixed(3))
        assertFalse(outcome.succeeded)
        assertEquals(1, outcome.marginOfFailure)
        assertEquals("Failure by 1-4", outcome.consequence?.printed)
        assertNull(outcome.critical, "only a severe misfire reaches the critical table")
    }

    @Test
    fun `failure by five to nine is a wrong activation`() {
        // 5+5+5 = 15 against 8: failure by 7.
        val outcome = RitualResolution.attempt(8, light, cor = 0, source = fixed(5))
        assertEquals(7, outcome.marginOfFailure)
        assertEquals("Failure by 5-9", outcome.consequence?.printed)
        assertNull(outcome.critical)
    }

    @Test
    fun `a severe misfire rolls the critical table at minus two`() {
        // Check 5+5+5 = 15 vs target 4 -> failure by 11. Critical roll
        // 4+4+4 = 12, shifted to 10 -> the 9-10 band, not 11-12.
        val outcome = RitualResolution.attempt(4, light, cor = 0, source = scripted(5, 5, 5, 4, 4, 4))
        assertEquals(11, outcome.marginOfFailure)
        assertEquals("Failure by 10+", outcome.consequence?.printed)
        assertEquals(10, outcome.criticalRoll)
        assertEquals("9-10", outcome.critical?.printed)
    }

    @Test
    fun `a rolled seventeen consults the table with no shift`() {
        // p. 67 names this row by the ROLL -- "Critical Failure (roll 17-18)"
        // -- not by a margin, so it is entered before the by-10+ band even
        // though GURPS would call both a critical failure.
        // Check 6+6+5 = 17 vs 16 (margin 1). Critical roll 4+4+4 = 12, unshifted.
        val outcome = RitualResolution.attempt(16, light, cor = 0, source = scripted(6, 6, 5, 4, 4, 4))
        assertEquals("Critical Failure (roll 17-18)", outcome.consequence?.printed)
        assertEquals(12, outcome.criticalRoll)
        assertEquals("11-12", outcome.critical?.printed)
    }

    @Test
    fun `a rolled eighteen beats the by-ten band even when both apply`() {
        // 18 vs a target of 4 is a failure by 14 AND a rolled 18. The book's
        // own row is named for the roll, so it wins and the -2 is not applied.
        val outcome = RitualResolution.attempt(4, light, cor = 0, source = scripted(6, 6, 6, 4, 4, 4))
        assertEquals("Critical Failure (roll 17-18)", outcome.consequence?.printed)
        assertEquals(12, outcome.criticalRoll, "no -2 shift on this row")
    }

    @Test
    fun `a rolled eighteen fails even a target it did not overshoot`() {
        // REGRESSION (Codex): a master ritualist at skill 22 has a target of
        // 22, so an 18 comes in "under" it. Deciding success by margin
        // reported the book's own Critical Failure row as a success.
        val outcome = RitualResolution.attempt(22, light, cor = 0, source = scripted(6, 6, 6, 4, 4, 4))
        assertFalse(outcome.succeeded)
        assertEquals("Critical Failure (roll 17-18)", outcome.consequence?.printed)
        assertEquals(0, outcome.marginOfFailure, "nothing was overshot")
        assertEquals("11-12", outcome.critical?.printed)
    }

    @Test
    fun `the shift cannot push the roll off the bottom of the table`() {
        // 3d6 of 3, less 2, is 1 -- and the table starts at 3.
        val outcome = RitualResolution.attempt(4, light, cor = 0, source = scripted(5, 5, 5, 1, 1, 1))
        assertEquals(3, outcome.criticalRoll)
        assertEquals("3-4", outcome.critical?.printed)
    }

    @Test
    fun `every 3d6 result finds exactly one critical row`() {
        (3..18).forEach { roll ->
            val row = RitualResolution.criticalFor(roll)
            assertTrue(roll >= row.low && roll <= row.high, "roll $roll got ${row.printed}")
        }
    }

    @Test
    fun `a roll outside 3d6 is a bad call`() {
        assertThrows<IllegalArgumentException> { RitualResolution.criticalFor(2) }
        assertThrows<IllegalArgumentException> { RitualResolution.criticalFor(19) }
    }

    @Test
    fun `the critical table still says what the rules read off it`() {
        // Guard against silent repair: one row charges 1 CoR and one charges
        // two. If the content stops saying so, the data changed.
        assertTrue("1 CoR" in RitualResolution.criticalFor(13).outcome)
        assertTrue("2 CoR" in RitualResolution.criticalFor(17).outcome)
    }
}
