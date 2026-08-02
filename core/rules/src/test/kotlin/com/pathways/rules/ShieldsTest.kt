package com.pathways.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * What carrying a shield does to a defence (Ch. 5, p. 37).
 *
 * The two things worth pinning are that the Defense Bonus reaches all three
 * defences rather than Block alone, and that the two rows carrying a Block
 * penalty do not carry the same one.
 */
class ShieldsTest {

    private fun shield(prefix: String) = Shields.all().first { it.type.startsWith(prefix) }

    @Test
    fun `the four printed shields load with their columns`() {
        val medium = shield("Medium")
        assertEquals(2, medium.db)
        assertEquals(5, medium.coverDr)
        assertEquals(20, medium.hp)
        assertEquals(4, Shields.all().size)
    }

    @Test
    fun `the defense bonus reaches dodge and parry, not block alone`() {
        // "its DB (Defense Bonus) adds to all active defenses (Dodge, Parry,
        // and Block)". Applying it only to Block is the obvious wrong reading:
        // Block is the only defence whose FORMULA mentions a shield.
        val heavy = shield("Heavy")
        assertEquals(10, Shields.withBonus(7, heavy), "Dodge 7 behind a +3 shield")
        assertEquals(7, Shields.withBonus(7, null), "and unchanged without one")
    }

    @Test
    fun `block adds the shield's bonus to the skill formula`() {
        // Shield/2 + 3, then the DB: skill 10 is 8, and +2 makes 10.
        assertEquals(10, Shields.blockFor(shield("Medium"), shieldSkillLevel = 10))
        assertEquals(8, ParryBlock.blockFor(10, shieldDb = 0))
    }

    @Test
    fun `a light shield is worse at blocking a shot, and only a shot`() {
        // "-1 to Block vs. ranged" -- the penalty carries its scope, like
        // Spiritual Attrition and the prone target's melee value.
        val light = shield("Light")
        assertEquals(0, Shields.blockPenalty(light, isRanged = false))
        assertEquals(-1, Shields.blockPenalty(light, isRanged = true))
        assertEquals(9, Shields.blockFor(light, shieldSkillLevel = 10))
        assertEquals(8, Shields.blockFor(light, shieldSkillLevel = 10, isRanged = true))
    }

    @Test
    fun `an improvised shield is worse at blocking anything`() {
        // "-2 to Block", with no scope attached, so it applies in reach too.
        val improvised = shield("Improvised")
        assertEquals(-2, Shields.blockPenalty(improvised, isRanged = false))
        assertEquals(-2, Shields.blockPenalty(improvised, isRanged = true))
    }

    @Test
    fun `the rows the book gives no block penalty take none`() {
        listOf("Medium", "Heavy").forEach {
            assertEquals(0, Shields.blockPenalty(shield(it), isRanged = true), it)
        }
    }

    @Test
    fun `the penalties are still printed in the cells they were read from`() {
        // The numbers live in Kotlin, so the book's own words are what keeps
        // them honest -- the same split the maneuver table settled.
        assertTrue(shield("Light").notes.contains("-1 to Block vs. ranged"))
        assertTrue(shield("Improvised").notes.contains("-2 to Block"))
    }

    @Test
    fun `an unknown shield is refused, not treated as bare-handed`() {
        assertThrows<IllegalArgumentException> { Shields.named("Kite") }
    }

    @Test
    fun `the three bullets under the table are carried as prose`() {
        val rules = Shields.rules()
        assertEquals(3, rules.size)
        assertTrue(rules.any { it.rule.startsWith("Shield Bash") })
        // Their numbers are deliberately not modelled: a shield bash is thr-2
        // to thr, which is Damage's table and a weapon this book never lists.
        assertTrue(rules.any { it.rule.contains("thr-2 cr for light") })
    }
}
