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
    fun `three critical rows charge corruption and the rest charge none`() {
        assertEquals(1, RitualResolution.criticalFor(13).corGained)
        assertEquals(1, RitualResolution.criticalFor(15).corGained)
        assertEquals(2, RitualResolution.criticalFor(17).corGained)
        // Catastrophe is the worst row and charges no CoR at all -- what it
        // costs is the GM's invention, which is not a number.
        assertNull(RitualResolution.criticalFor(18).corGained)
        assertNull(RitualResolution.criticalFor(3).corGained)
    }

    @Test
    fun `the critical table still says what the rules read off it`() {
        // Guard against silent repair: one row charges 1 CoR and one charges
        // two. If the content stops saying so, the data changed.
        assertTrue("1 CoR" in RitualResolution.criticalFor(13).outcome)
        assertTrue("2 CoR" in RitualResolution.criticalFor(17).outcome)
    }

    // --- preparation and cost ----------------------------------------------

    private fun weight(name: String) = RitualResolution.weights().first { it.weight == name }

    @Test
    fun `every ritual costs SPI, and two of the cells are not one number`() {
        // The app charged nothing for a ritual until this slice, which made
        // one repeatable forever -- the opposite of what SPI is here for.
        assertEquals(2, RitualResolution.spiFor(weight("Light")))
        assertEquals(4, RitualResolution.spiFor(weight("Moderate")))
        assertEquals(6, RitualResolution.spiFor(weight("Moderate"), chosen = 6))
        assertThrows<IllegalArgumentException> {
            RitualResolution.spiFor(weight("Moderate"), chosen = 7)
        }
        assertThrows<IllegalArgumentException> {
            RitualResolution.spiFor(weight("Light"), chosen = 1)
        }
    }

    @Test
    fun `a heavy ritual's cost is open at the top`() {
        // "8+ SPI": the printed number is a floor, so 20 is legal and 7 is not.
        val heavy = weight("Heavy")
        assertTrue(heavy.spiOpen)
        assertEquals(8, RitualResolution.spiFor(heavy))
        assertEquals(20, RitualResolution.spiFor(heavy, chosen = 20))
        assertThrows<IllegalArgumentException> { RitualResolution.spiFor(heavy, chosen = 7) }
    }

    @Test
    fun `an open cost cannot be pushed past what an Int holds`() {
        // REGRESSION (Codex): the open cell used Int.MAX_VALUE as its ceiling,
        // so chosen + burned wrapped NEGATIVE -- and a negative cost would
        // have been paid back as SPI rather than charged.
        val heavy = weight("Heavy")
        assertThrows<IllegalArgumentException> {
            RitualResolution.spiFor(heavy, chosen = Int.MAX_VALUE, burned = 1)
        }
        assertThrows<IllegalArgumentException> { RitualResolution.spiFor(heavy, burned = -1) }
    }

    @Test
    fun `preparation moves the target, and none of it is required`() {
        val light = weight("Light")
        val excellent = RitualResolution.preparationNamed("Excellent")
        assertEquals(3, excellent.modifier)
        assertEquals(14, RitualResolution.targetFor(14, light, cor = 0))
        assertEquals(17, RitualResolution.targetFor(14, light, cor = 0, preparation = excellent))
        // The quick mode has no Phase I at all, so null is a real answer.
        assertEquals(14, RitualResolution.targetFor(14, light, cor = 0, preparation = null))
    }

    @Test
    fun `the ranged and open-ended preparation rows keep both ends`() {
        val good = RitualResolution.preparationNamed("Good")
        assertEquals(1, good.modifier)
        assertEquals(2, good.best)
        val none = RitualResolution.preparationNamed("None")
        assertEquals(-4, none.modifier)
        assertTrue(none.openEnded, "'-4 or worse' is a floor, not a value")
        assertThrows<IllegalArgumentException> { RitualResolution.preparationNamed("Perfect") }
    }

    @Test
    fun `a botched preparation can cost more than the ritual's own weight`() {
        // -4 preparation against a Heavy ritual's -4 is -8 before corruption:
        // worth pinning, because it is the case a GM will not expect.
        val target = RitualResolution.targetFor(
            skillLevel = 14,
            weight = weight("Heavy"),
            cor = 0,
            preparation = RitualResolution.preparationNamed("None"),
        )
        assertEquals(6, target)
    }

    @Test
    fun `speaking the ritual in an accented mystical tongue costs two`() {
        // Ch. 3, p. 14: "Rituals using the language at -2". The clause had
        // been printed in a table nothing read until the Languages slice.
        fun target(language: com.pathways.rules.content.LanguageProficiency?) =
            RitualResolution.targetFor(
                skillLevel = 12, weight = weight("Light"), cor = 0, language = language,
            )

        assertEquals(12, target(null), "no language stated leaves the target alone")
        assertEquals(12, target(Languages.proficiency(Languages.MYSTICAL, "Native")))
        assertEquals(10, target(Languages.proficiency(Languages.MYSTICAL, "Accented")))
    }

    @Test
    fun `a caster who cannot speak the language does not roll at a penalty`() {
        // "Cannot perform rituals in the language" is a refusal, not a
        // modifier. Rolling anyway at some invented penalty would let a
        // character do what the book forbids, and succeed at it.
        assertThrows<IllegalArgumentException> {
            RitualResolution.targetFor(
                skillLevel = 12, weight = weight("Light"), cor = 0,
                language = Languages.proficiency(Languages.MYSTICAL, "Broken"),
            )
        }
        // A common language is refused for the other reason: its table says
        // nothing about rituals at all.
        assertThrows<IllegalArgumentException> {
            RitualResolution.targetFor(
                skillLevel = 12, weight = weight("Light"), cor = 0,
                language = Languages.proficiency(Languages.COMMON, "Native"),
            )
        }
    }

    @Test
    fun `the language penalty stacks with weight, corruption and preparation`() {
        // Four independent sources on one target, which is the case a GM will
        // not add up by hand: 12 - 4 (Heavy) - 1 (CoR 3) + 3 (Excellent)
        // - 2 (Accented).
        val target = RitualResolution.targetFor(
            skillLevel = 12,
            weight = weight("Heavy"),
            cor = 3,
            preparation = RitualResolution.preparationNamed("Excellent"),
            language = Languages.proficiency(Languages.MYSTICAL, "Accented"),
        )
        assertEquals(8, target)
    }
}
