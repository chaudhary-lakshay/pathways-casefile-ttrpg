package com.pathways.model

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class SkillKeyTest {

    @Test
    fun `display renders the real controlling attribute for Will`() {
        val key = SkillKey("Intimidation", null, ControllingAttribute.Will, Difficulty.AVERAGE)
        assertEquals("Intimidation/Will", key.display)
    }

    @Test
    fun `display renders the real controlling attribute for SPI`() {
        val key = SkillKey("Spiritual Intuition", null, ControllingAttribute.Spi, Difficulty.HARD)
        assertEquals("Spiritual Intuition/SPI", key.display)
    }

    @Test
    fun `display still renders a buyable attribute as before`() {
        val key = SkillKey("Occultism", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE)
        assertEquals("Occultism/IQ", key.display)
    }

    @Test
    fun `display includes the specialization when present`() {
        val key = SkillKey("History", "Occult", ControllingAttribute.Buyable(Attr.IQ), Difficulty.HARD)
        assertEquals("History (Occult)/IQ", key.display)
    }
}
