package com.pathways.app

import com.pathways.rules.PowerSources
import com.pathways.rules.RitualResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RitualDialogTest {

    /**
     * v6.9i prints no floor on spending SPI (owner's ruling 11): a caster
     * short of fuel still casts, and the overspend is Soul Debt.
     */
    @Test
    fun `only a forbidden tongue refuses a ritual`() {
        assertNull(ritualRefusal(canSpeak = true))
        assertTrue("forbids" in ritualRefusal(canSpeak = false)!!)
    }

    /** v6.9i: the weight's own SPI cell is the fuel, open cells three past the floor. */
    @Test
    fun `the fuel offered is the weight's printed cell`() {
        val byName = RitualResolution.weights().associateBy { it.weight }
        assertEquals(listOf(2), spiChoices(byName.getValue("Light")))
        assertEquals(listOf(4, 5, 6), spiChoices(byName.getValue("Moderate")))
        assertEquals(listOf(12, 13, 14, 15), spiChoices(byName.getValue("Major")))
    }

    /** The orthodox toggle sets the bonus to +2; praying to oneself adds none. */
    @Test
    fun `the invocation applied is the toggle's or the GM's`() {
        assertNull(invocationApplied(invoked = false, orthodox = true, gmValue = 4))
        assertEquals(PowerSources.ORTHODOX_BONUS, invocationApplied(invoked = true, orthodox = true, gmValue = 4))
        assertEquals(4, invocationApplied(invoked = true, orthodox = false, gmValue = 4))
    }
}
