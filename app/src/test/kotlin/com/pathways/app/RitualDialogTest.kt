package com.pathways.app

import com.pathways.rules.PowerSources
import com.pathways.rules.RitualResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RitualDialogTest {

    @Test
    fun `an unpaid ritual is refused fuel its caster cannot pay`() {
        val refusal = ritualRefusal(canSpeak = true, cost = 4, hpSacrificed = 2, spiAvailable = 0)
        assertTrue("3 SPI" in refusal!!, refusal)
        assertTrue("HP" in refusal, refusal)
        assertNull(ritualRefusal(canSpeak = true, cost = 4, hpSacrificed = 8, spiAvailable = 0))
    }

    @Test
    fun `a forbidden tongue refuses before anything is spent`() {
        assertTrue("forbids" in ritualRefusal(canSpeak = false, cost = 2, hpSacrificed = 0, spiAvailable = 9)!!)
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
