package com.pathways.rules

import com.pathways.model.*
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class DeriveTest {

    private fun willSkill(points: Int = 8) =
        PurchasedSkill(SkillKey("Intimidation", null, ControllingAttribute.Will, Difficulty.AVERAGE), points)

    private fun spiSkill(points: Int = 8) =
        PurchasedSkill(SkillKey("Spiritual Intuition", null, ControllingAttribute.Spi, Difficulty.HARD), points)

    @Test
    fun `a Will-controlled skill keys off raised Will, not base IQ`() {
        // KNOWN_ISSUES #5 regression: with the old Attr.IQ mapping, raising
        // Will via SecondaryDelta had no effect on a Will-controlled skill's
        // level. This test must FAIL against that mapping.
        val base = Character(name = "base", attributes = Attributes(iq = 10), skills = listOf(willSkill()))
        val raised = Character(
            name = "raised", attributes = Attributes(iq = 10),
            secondaryDelta = SecondaryDelta(will = 2), skills = listOf(willSkill()),
        )

        val baseLevel = Derive.of(base).skillLevels.getValue(willSkill().key)
        val raisedLevel = Derive.of(raised).skillLevels.getValue(willSkill().key)

        assertEquals(baseLevel + 2, raisedLevel)
    }

    @Test
    fun `an SPI-controlled skill keys off the character's SPI`() {
        val c = Character(name = "medium", attributes = Attributes(iq = 10), spi = 3, skills = listOf(spiSkill()))
        val d = Derive.of(c)
        val expected = SkillLadder.levelFor(8, Difficulty.HARD, attributeLevel = 3)
        assertEquals(expected, d.skillLevels.getValue(spiSkill().key))
    }

    @Test
    fun `SPI defaults to zero and is not billed to the point budget`() {
        val c = Character(name = "mortal", attributes = Attributes())
        assertEquals(0, c.spi)
        val budget = Derive.of(c).budget
        assertEquals(0, budget.spent)
    }

    // --- Phase D: direct coverage of skillLevels itself (KNOWN_ISSUES:
    // "Derive.skillLevels is untested"). The golden suite only ever calls
    // SkillLadder.levelFor directly and never reads this map, even though
    // it is the API a future sheet screen will actually call. ---

    @Test
    fun `skillLevels contains one entry per distinct purchased skill, keyed by SkillKey`() {
        val occultism = PurchasedSkill(
            SkillKey("Occultism", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE), 16,
        )
        val research = PurchasedSkill(
            SkillKey("Research", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE), 8,
        )
        val c = Character(name = "scholar", attributes = Attributes(iq = 10), skills = listOf(occultism, research))

        val levels = Derive.of(c).skillLevels

        assertEquals(setOf(occultism.key, research.key), levels.keys)
        assertEquals(SkillLadder.levelFor(16, Difficulty.AVERAGE, attributeLevel = 10), levels.getValue(occultism.key))
        assertEquals(SkillLadder.levelFor(8, Difficulty.AVERAGE, attributeLevel = 10), levels.getValue(research.key))
    }

    @Test
    fun `skillLevels is empty for a character with no purchased skills`() {
        assertEquals(emptyMap(), Derive.of(Character(name = "blank")).skillLevels)
    }
}
