package com.pathways.rules

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `Special Effect on Hit` column (Ch. 5, p. 41).
 *
 * Stored on all nine rows since the table was extracted and read by nothing —
 * not by production, not by a test — while the damage screen already held
 * both sides of every threshold in it.
 */
class WoundEffectsTest {

    private fun arm() = Wounds.locationNamed("Arm")
    private fun hand() = Wounds.locationNamed("Hand")

    @Test
    fun `a threshold is strictly greater than the fraction, like a major wound`() {
        // "crippled at > 1/2 HP of limb (~ HP/3)" on a 10 HP character: HP/3
        // floors to 3, and the rule says MORE than that, so 4 cripples and 3
        // does not. Wounds.majorWoundThreshold reads its own "> 1/2" the same
        // way and its KDoc records the reading.
        assertEquals(4, Wounds.thresholdFor(arm(), "Crippled", maxHp = 10))
        assertTrue("Crippled" in Wounds.effectsAt(arm(), damage = 4, maxHp = 10).map { it.outcome })
        assertTrue("Crippled" !in Wounds.effectsAt(arm(), damage = 3, maxHp = 10).map { it.outcome })
    }

    @Test
    fun `a flat amount fires on the amount itself, not above it`() {
        // "Drop held item if damage >= 3" — at least, where crippling is more
        // than. Two conditions in one cell, read differently, because the
        // book writes them differently.
        assertTrue("Drop held item" in Wounds.effectsAt(arm(), damage = 3, maxHp = 10).map { it.outcome })
        assertTrue("Drop held item" !in Wounds.effectsAt(arm(), damage = 2, maxHp = 10).map { it.outcome })
    }

    @Test
    fun `a clause with no condition fires on any damage that lands`() {
        // The Hand's cell prints "Drop held item" bare where the Arm's prints
        // ">= 3". Losing that distinction would have the Hand keep its grip
        // on a hit the book says drops it.
        assertTrue("Drop held item" in Wounds.effectsAt(hand(), damage = 1, maxHp = 10).map { it.outcome })
        assertTrue("Crippled" !in Wounds.effectsAt(hand(), damage = 1, maxHp = 10).map { it.outcome })
    }

    @Test
    fun `the approximation is marked as one, because the book marks it`() {
        // Arm and Leg print the governing rule as "> 1/2 HP of LIMB" and give
        // "~ HP/3" as the book's OWN approximation of it in character HP.
        // Hand, Neck and Eye print a character-HP threshold directly.
        val crippled = Wounds.effectsAt(arm(), damage = 9, maxHp = 10).first { it.outcome == "Crippled" }
        assertTrue(crippled.isApproximate)
        val handCrippled = Wounds.effectsAt(hand(), damage = 9, maxHp = 10).first { it.outcome == "Crippled" }
        assertTrue(!handCrippled.isApproximate)
    }

    @Test
    fun `the four rows printing no damage threshold produce nothing at any damage`() {
        // Torso has no effect at all; the other three print effects this app
        // refuses for reasons recorded in production source — a knockdown
        // roll with no attribute printed to roll against (Shock), DR that
        // nothing here models (Equipment.UNMODELLED), and GM prose.
        for (target in listOf("Torso", "Face", "Skull", "Vitals")) {
            val location = Wounds.locationNamed(target)
            assertEquals(emptyList(), Wounds.effectsAt(location, damage = 99, maxHp = 10), target)
        }
    }

    @Test
    fun `the printed cell survives beside the structure`() {
        // The prose says things the thresholds cannot: "unusable until
        // healed" is the duration this app does not track, and it is the
        // reason the screen reports a trigger rather than applying a state.
        assertTrue("unusable until healed" in Wounds.cripplingRule())
        assertTrue("First Aid (skill 12+)" in Wounds.cripplingRule())
        assertTrue("bypasses head DR" in Wounds.locationNamed("Eye").effect)
    }

    @Test
    fun `exactly five of the nine rows price an effect, and they are named`() {
        // NOT `thresholds.isNotEmpty() == effectsAt(99).isNotEmpty()`, which
        // was the first version of this test and could not fail: effectsAt
        // FILTERS that same list, so a row silently losing its thresholds
        // makes both sides false and the assertion passes. The set is named
        // instead, so a row gaining or losing one reddens this.
        assertEquals(
            listOf("Arm", "Leg", "Hand", "Neck", "Eye"),
            Wounds.locations().filter { it.thresholds.isNotEmpty() }.map { it.target },
        )
        assertEquals(9, Wounds.locations().size)
        // And the count of clauses per row, which the list above does not fix.
        assertEquals(
            listOf(2, 2, 2, 1, 1),
            Wounds.locations().filter { it.thresholds.isNotEmpty() }.map { it.thresholds.size },
        )
    }
}
