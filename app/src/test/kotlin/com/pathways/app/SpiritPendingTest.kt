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

    /** "The penalty applies to a single roll": a Double or Rapid Strike's first. */
    @Test
    fun `only the first of several attacks takes a pending spirit penalty`() {
        assertEquals(listOf(7, 10, 10), attackTargets(7, attacks = 3, spiritPending = 3))
        assertEquals(listOf(7, 7), attackTargets(7, attacks = 2, spiritPending = 0))
    }

    /** The footer names both targets when the spirit penalty takes only the first. */
    @Test
    fun `a multi-attack caption names the first target and the rest`() {
        assertEquals("3d6 ≤ 7 first, 3d6 ≤ 10 after", attackCaption(7, attacks = 3, spiritPending = 3))
        assertEquals("3d6 ≤ 7, each", attackCaption(7, attacks = 2, spiritPending = 0))
        assertEquals("3d6 ≤ 7", attackCaption(7, attacks = 1, spiritPending = 3))
    }
}
