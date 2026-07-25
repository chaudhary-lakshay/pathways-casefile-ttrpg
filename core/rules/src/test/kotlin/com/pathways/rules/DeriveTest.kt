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
}
