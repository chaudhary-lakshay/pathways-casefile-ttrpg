package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ch. 4's two universal Beyonder abilities: "Roll 3d6. The difficulty class
 * is based on however much current SPI you have -- if you have 1 SPI, you
 * need a critical roll (3-4); if you have 9 SPI, you need 9 or lower".
 */
class SpiritualSensesTest {

    private fun fixed(face: Int) = RandomSource { face }

    @Test
    fun `the book prints two, in order`() {
        assertEquals(
            listOf("Spiritual Intuition", "Spiritual Perception"),
            SpiritualSenses.all().map { it.ability },
        )
    }

    @Test
    fun `a sense rolls against current SPI and nothing of Per`() {
        assertEquals(9, AttributeRoll.targetFor(AttributeRoll.Kind.INTUITION, 9, emptyList()))
        val alert = listOf(Trait(TraitEffects.ALERTNESS + " 2", 10, TraitKind.ADVANTAGE))
        assertEquals(9, AttributeRoll.targetFor(AttributeRoll.Kind.PERCEPTION, 9, alert))
        assertEquals(listOf("SPI 9"), AttributeRoll.partsFor(AttributeRoll.Kind.PERCEPTION, 9, alert))
    }

    @Test
    fun `at 1 SPI only a critical succeeds`() {
        assertTrue(SuccessRoll.against(1, fixed(1)).outcome.isSuccess, "a rolled 3")
        assertFalse(SuccessRoll.against(1, fixed(2)).outcome.isSuccess, "a rolled 6")
    }

    /** "0 SPI -- Spiritual Exhaustion: All Beyonder abilities deactivate." */
    @Test
    fun `a sense is a Beyonder's, and goes out at 0 SPI`() {
        assertTrue(SpiritualSenses.isOffered(isBeyonder = true, currentSpi = 1))
        assertFalse(SpiritualSenses.isOffered(isBeyonder = true, currentSpi = 0))
        assertFalse(SpiritualSenses.isOffered(isBeyonder = true, currentSpi = -2))
        assertFalse(SpiritualSenses.isOffered(isBeyonder = false, currentSpi = 5))
    }

    /** Spiritual Attrition: "-3 to all SPI-based skill rolls and Beyonder ability checks". */
    @Test
    fun `Spiritual Attrition reaches a sense and not a Will roll`() {
        val attrited = Pools.spi(current = 4, max = 12)
        val sense = AttributeRoll.targetFor(AttributeRoll.Kind.INTUITION, 4, emptyList(), spi = attrited)
        assertEquals(1, sense)
        assertTrue(
            "Spiritual Attrition -3" in AttributeRoll.partsFor(AttributeRoll.Kind.INTUITION, 4, emptyList(), spi = attrited),
        )
        assertEquals(10, AttributeRoll.targetFor(AttributeRoll.Kind.WILL, 10, emptyList(), spi = attrited))
    }
}
