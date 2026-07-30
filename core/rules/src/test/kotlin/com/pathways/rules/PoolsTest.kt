package com.pathways.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PoolsTest {

    @Test
    fun `a healthy pool carries no penalty`() {
        val hp = Pools.hp(10, 10)
        assertEquals("HP to 1", hp.level)
        assertEquals(0, hp.penalty)
        assertFalse(hp.rollsToStayConscious)
    }

    @Test
    fun `one third HP remaining is reeling`() {
        // p. 40: "1/3 HP remaining -- Reeling: -1 to all rolls". Thirds floor,
        // like every other division in this codebase (CLAUDE.md 4.3).
        assertEquals(0, Pools.hp(4, 10).penalty)
        assertEquals(-1, Pools.hp(3, 10).penalty)
        assertEquals("1/3 HP remaining", Pools.hp(3, 10).level)
        assertEquals(PenaltyScope.ALL_ROLLS, Pools.hp(3, 10).scope)
    }

    @Test
    fun `reeling does not stop applying once HP goes negative`() {
        // The book states the penalty at 1/3 and never withdraws it; a
        // character at -2 HP is not less impaired than one at 3.
        assertEquals(-1, Pools.hp(-2, 10).penalty)
    }

    @Test
    fun `zero HP rolls each turn to stay conscious`() {
        val hp = Pools.hp(0, 10)
        assertEquals("0 HP", hp.level)
        assertTrue(hp.rollsToStayConscious)
        assertFalse(hp.rollsToSurvive)
        assertFalse(hp.isDead)
    }

    @Test
    fun `negative full HP rolls against death`() {
        // p. 40: "Death occurs at -HP ... roll vs. HT to survive."
        val hp = Pools.hp(-10, 10)
        assertEquals("-HP", hp.level)
        assertTrue(hp.rollsToSurvive)
        assertFalse(hp.isDead)
    }

    @Test
    fun `five times negative HP is dead with no roll`() {
        val hp = Pools.hp(-50, 10)
        assertEquals("-5×HP", hp.level)
        assertTrue(hp.isDead)
        // "Dead -- no roll required": offering a roll here would contradict
        // the row's own text.
        assertFalse(hp.rollsToSurvive)
        assertFalse(hp.rollsToStayConscious)
    }

    @Test
    fun `FP below zero penalises one per point, worse than the flat third`() {
        // Two prose statements, two different penalties, both in force: p. 42
        // "At 1/3 FP or below: -1 to all rolls", p. 41 "-1 to all rolls per 1
        // FP below 0". The book never says they stack, so the worse applies.
        assertEquals(0, Pools.fp(5, 12).penalty)
        assertEquals(-1, Pools.fp(4, 12).penalty)
        assertEquals(-1, Pools.fp(0, 12).penalty)
        assertEquals(-3, Pools.fp(-3, 12).penalty)
    }

    @Test
    fun `FP below minus max is unconscious`() {
        // p. 41: "If FP drops below -1xMaxFP, you fall unconscious."
        assertFalse(Pools.fp(-12, 12).isUnconscious)
        assertTrue(Pools.fp(-13, 12).isUnconscious)
    }

    @Test
    fun `spiritual attrition penalises SPI rolls only`() {
        // p. 35: "-3 to all SPI-based skill rolls and Beyonder ability
        // checks" -- NOT to all rolls. Same scoping mistake as the ritual CoR
        // ladder; the scope travels with the number.
        val spi = Pools.spi(3, 9)
        assertEquals(-3, spi.penalty)
        assertEquals(PenaltyScope.SPI_ROLLS, spi.scope)
        assertEquals("<= 1/3 Max SPI", spi.level)
    }

    @Test
    fun `spiritual exhaustion shuts the abilities off`() {
        val spi = Pools.spi(0, 9)
        assertEquals("0 SPI", spi.level)
        assertTrue(spi.abilitiesOffline)
        assertTrue("Spirit Vision shuts off" in spi.effect)
    }

    @Test
    fun `soul debt is one CoR per point below zero`() {
        // p. 35: "Soul Debt: +1 CoR per 1 point below 0 (e.g. SPI -4 = +4 CoR)"
        assertEquals(0, Pools.spi(0, 9).corGained)
        assertEquals(4, Pools.spi(-4, 9).corGained)
        assertEquals("Below 0 SPI", Pools.spi(-4, 9).level)
    }

    @Test
    fun `SPI loss never kills`() {
        // p. 35: "Unlike HP depletion, there is no risk of death from SPI loss."
        assertFalse(Pools.spi(-20, 9).isDead)
    }

    @Test
    fun `a mortal has no SPI pool to report on`() {
        // p. 41: "Non-Beyonders (SPI 0) ... have no spirituality to sacrifice."
        assertThrows<IllegalArgumentException> { Pools.spi(0, 0) }
    }

    @Test
    fun `the levels this object switches on are still the levels the book prints`() {
        // Guard against silent repair: Pools compares numbers, the content
        // carries the printed labels, and the two must not drift.
        val hp = Pools.thresholds(Pool.HP).map { it.level }
        assertEquals(listOf("HP to 1", "1/3 HP remaining", "0 HP", "-HP", "-5×HP"), hp)
        val spi = Pools.thresholds(Pool.SPI).map { it.level }
        assertEquals(listOf("SPI to 1", "<= 1/3 Max SPI", "0 SPI", "Below 0 SPI"), spi)
        // FP is prose in the book, so it has no rows at all.
        assertEquals(emptyList<String>(), Pools.thresholds(Pool.FP).map { it.level })
    }

    @Test
    fun `every reported status quotes the book row it came from`() {
        assertTrue("Reeling" in Pools.hp(3, 10).effect)
        assertTrue("Soul Debt" in Pools.spi(-1, 9).effect)
        // FP has no printed row; its text is Pools' own, marked as such.
        assertTrue(Pools.fp(-3, 12).effect.isNotEmpty())
    }

    @Test
    fun `a maximum below one is a bad call`() {
        assertThrows<IllegalArgumentException> { Pools.hp(0, 0) }
        assertThrows<IllegalArgumentException> { Pools.fp(0, -2) }
    }
}
