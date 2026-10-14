package com.pathways.app

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RitualDialogTest {

    @Test
    fun `a ritual paid for at the asking is not refused for the SPI it spent`() {
        // A caster with 2 SPI asked for a Light invocation and paid its 2 at
        // the asking (6hr). The Cast that follows owes nothing more, so the
        // 0 SPI left is not a refusal.
        assertNull(ritualRefusal(canSpeak = true, rejected = false, cost = 2, hpSacrificed = 0, spiAvailable = 0, paid = true))
    }

    @Test
    fun `an unpaid ritual is refused fuel its caster cannot pay`() {
        val refusal = ritualRefusal(canSpeak = true, rejected = false, cost = 4, hpSacrificed = 2, spiAvailable = 0, paid = false)
        assertTrue("3 SPI" in refusal!!, refusal)
        assertTrue("HP" in refusal, refusal)
        assertNull(ritualRefusal(canSpeak = true, rejected = false, cost = 4, hpSacrificed = 8, spiAvailable = 0, paid = false))
    }

    @Test
    fun `a forbidden tongue and a rejection refuse before anything is spent`() {
        assertTrue("forbids" in ritualRefusal(canSpeak = false, rejected = false, cost = 2, hpSacrificed = 0, spiAvailable = 9, paid = false)!!)
        assertTrue("rejected" in ritualRefusal(canSpeak = true, rejected = true, cost = 2, hpSacrificed = 0, spiAvailable = 9, paid = false)!!)
    }
}
