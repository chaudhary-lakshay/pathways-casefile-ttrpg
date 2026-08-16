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

    @Test
    fun `voluntary SPI spending stops at zero`() {
        // Ch. 7's Power Source A: "SPI cannot be reduced below 0." A caster
        // with 2 SPI can fund a 2 SPI ritual and not a 3 SPI one.
        assertTrue(Pools.canAffordSpi(2, current = 2))
        assertFalse(Pools.canAffordSpi(3, current = 2))
        assertEquals(2, Pools.spendableSpi(2))
    }

    @Test
    fun `a mortal can spend nothing, and a free action is still free`() {
        // SPI 0 is the pool table's Spiritual Exhaustion row -- "cannot
        // activate any SPI-cost abilities". A cost of zero is not an SPI-cost
        // ability, so it stays available: the book charges nothing for it.
        assertEquals(0, Pools.spendableSpi(0))
        assertFalse(Pools.canAffordSpi(1, current = 0))
        assertTrue(Pools.canAffordSpi(0, current = 0))
    }

    @Test
    fun `a character already in Soul Debt may spend nothing, not a negative`() {
        // Spirit body damage can leave the pool negative -- that IS the route
        // the book sends below 0. Spending from there floors at nothing
        // rather than returning -4, which would make canAffordSpi nonsense.
        assertEquals(0, Pools.spendableSpi(-4))
        assertFalse(Pools.canAffordSpi(1, current = -4))
        assertEquals(0, Pools.SPI_VOLUNTARY_FLOOR)
    }

    @Test
    fun `the pool table still reports the states spending can no longer reach`() {
        // The floor is on SPENDING, not on the pool. Soul Debt remains real
        // and reportable, because spirit body damage still goes there -- and
        // 6m's charge for it is untouched.
        assertEquals(3, Pools.spi(-3, 9).corGained)
        assertTrue(Pools.spi(0, 9).penalty != null || Pools.spi(0, 9).corGained == 0)
    }

    @Test
    fun `a negative cost is refused, not treated as a refund`() {
        assertThrows<IllegalArgumentException> { Pools.canAffordSpi(-1, current = 5) }
    }

    @Test
    fun `the fatigue threshold is one predicate, and the printed sentence follows it`() {
        // The row is "At 1/3 FP or below", and until this slice three sites
        // re-derived it: fp(), fpLevel() and fpEffect(). A number the sheet
        // prints and a number the defence screen shows must not be able to
        // disagree about whether the character is tired.
        assertTrue(Pools.isFatigued(Pools.fp(4, 12)))
        assertFalse(Pools.isFatigued(Pools.fp(5, 12)))
        // 0 FP and below are "1/3 FP or below" too: the row's own words. The
        // effect line switches to p. 41's harsher rule there, which is why
        // the predicate cannot be read off the printed text.
        assertTrue(Pools.isFatigued(Pools.fp(0, 12)))
        assertTrue(Pools.isFatigued(Pools.fp(-3, 12)))
    }

    @Test
    fun `only an FP status can be fatigued`() {
        // Reeling is 1/3 HP and Spiritual Attrition is 1/3 SPI, and neither
        // halves anything. Passing the wrong pool in is a caller's mistake
        // that would silently halve a Dodge for the wrong reason.
        assertThrows<IllegalArgumentException> { Pools.isFatigued(Pools.hp(3, 10)) }
        assertThrows<IllegalArgumentException> { Pools.halvedForFatigue(9, Pools.spi(2, 9)) }
    }

    @Test
    fun `at one third FP a Dodge is halved, rounded up`() {
        // p. 42: "At 1/3 FP or below: -1 to all rolls; Move and Dodge halved."
        // The row prints NO rounding direction -- the book prints "rounded
        // down" (line 2450) and "round up" (line 6050) where it cares -- so
        // rounding up is this app's recorded choice, being the reading that
        // cannot leave a character worse off than the sentence states.
        assertEquals(5, Pools.halvedForFatigue(9, Pools.fp(4, 12)))
        assertEquals(4, Pools.halvedForFatigue(8, Pools.fp(4, 12)))
        assertEquals(9, Pools.halvedForFatigue(9, Pools.fp(5, 12)))
    }

    @Test
    fun `the halving does not lift when FP goes below zero`() {
        // "At 1/3 FP or below ... Until above threshold": a character at -2 FP
        // is still below it. The p. 41 penalty replaces the -1, not the
        // halving, and the effect line no longer naming it is a display fact.
        assertEquals(5, Pools.halvedForFatigue(9, Pools.fp(-2, 12)))
        // The pairing is the assertion: the rule still applies below zero and
        // the printed line has stopped naming it, because p. 41's harsher
        // penalty takes that branch. Asserting only the tired case would pass
        // even if the -2 line wrongly claimed the halving too.
        assertTrue("Move and Dodge halved" in Pools.fp(4, 12).effect)
        assertFalse("Move and Dodge halved" in Pools.fp(-2, 12).effect)
    }
}
