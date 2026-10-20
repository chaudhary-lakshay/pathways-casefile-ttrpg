package com.pathways.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Issue #4: the Spend button names every CoR the tap charges. */
class AbilitySpendTest {

    @Test
    fun `a spend in debt names the debt and the drained use`() {
        assertEquals("Spend 1 SPI · +2 CoR (Soul Debt, drained)", spendLabel(price = 1, left = -3))
    }

    @Test
    fun `a spend that crosses zero names its soul debt`() {
        assertEquals("Spend 2 SPI · +1 CoR (Soul Debt)", spendLabel(price = 2, left = 1))
    }

    @Test
    fun `a spend that stays above zero costs no CoR`() {
        assertEquals("Spend 1 SPI", spendLabel(price = 1, left = 5))
    }
}
