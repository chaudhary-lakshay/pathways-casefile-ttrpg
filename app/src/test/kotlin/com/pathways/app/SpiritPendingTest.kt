package com.pathways.app

import com.pathways.rules.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Spirit body damage's "-1 per point ... to the next roll ... and then
 * clears" (v6.9i 2818-2819): a roll with a target clears it; a table or a
 * damage throw, which has none, is not "a roll the player makes" against
 * anything, and leaves it.
 */
class SpiritPendingTest {

    @Test
    fun `the next roll with a target clears the pending penalty`() {
        val scene = SheetScene()
        scene.spiritPending.value = 3
        val dice = scene.clearingSpiritOn(RandomSource { 4 })
        dice.thrown(listOf(2, 3), null)
        assertEquals(3, scene.spiritPending.value)
        dice.thrown(listOf(4, 4, 4), 9)
        assertEquals(0, scene.spiritPending.value)
        assertEquals(4, dice.roll(6))
    }
}
