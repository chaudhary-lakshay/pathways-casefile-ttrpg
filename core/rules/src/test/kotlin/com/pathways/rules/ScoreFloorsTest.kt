package com.pathways.rules

import com.pathways.model.Attributes
import com.pathways.model.SecondaryDelta
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The lowest score each guarded row may stand at. The book prices every
 * secondary "±N pts per level" and prints no floor (Ch. 2, pp. 8-9), so these
 * are the app's: the lowest values the rules it models can compute on, and a
 * zero under Basic Speed and Basic Move that is policy.
 */
class ScoreFloorsTest {

    private val nine = Attributes()
    private val fortitude = Trait("Spiritual Fortitude", 5, TraitKind.ADVANTAGE)

    private fun at(d: SecondaryDelta, traits: List<Trait> = emptyList()) =
        ScoreFloors.shortfall(SecondaryCharacteristics.of(nine, d), traits)

    @Test
    fun `a character at every floor falls short of nothing`() {
        assertEquals(SecondaryDelta(), at(SecondaryDelta(hp = -8, fp = -8, will = -8, per = -9)))
    }

    @Test
    fun `each row below its floor is short by the steps it needs back`() {
        // Attributes of 9: HP 9 - 9 = 0, FP 9 - 11 = -2, Will 9 - 9 = 0, Per 9 - 10 = -1.
        assertEquals(
            SecondaryDelta(hp = 1, fp = 3, will = 1, per = 1),
            at(SecondaryDelta(hp = -9, fp = -11, will = -9, per = -10)),
        )
    }

    @Test
    fun `Basic Speed and Basic Move stand at zero and no lower`() {
        // Attributes of 9: Speed 18 quarters, Move 4. Speed sold to 0 takes
        // Move's base to 0 with it, so nothing more can come off Move there.
        assertEquals(SecondaryDelta(), at(SecondaryDelta(basicSpeedQuarters = -18)))
        assertEquals(SecondaryDelta(), at(SecondaryDelta(basicMove = -4)))
        assertEquals(SecondaryDelta(basicSpeedQuarters = 1), at(SecondaryDelta(basicSpeedQuarters = -19)))
        assertEquals(SecondaryDelta(basicMove = 1), at(SecondaryDelta(basicMove = -5)))
        // A Speed step that only Move feels: 3.75 is legal, a Move of -1 is not.
        assertEquals(SecondaryDelta(basicMove = 1), at(SecondaryDelta(basicSpeedQuarters = -3, basicMove = -4)))
    }

    @Test
    fun `Move is short by what is left after Speed is bought back`() {
        // Speed -82 quarters drags Move's base to -21, so Move reads -121.
        // Buying Speed back to 0 returns the 21; charging Move for them too
        // would leave it at 21 -- and 121 is past what a SecondaryDelta holds.
        assertEquals(
            SecondaryDelta(basicSpeedQuarters = 82, basicMove = 100),
            at(SecondaryDelta(basicSpeedQuarters = -100, basicMove = -100)),
        )
    }

    @Test
    fun `the Will floor is a CoR ceiling, so Spiritual Fortitude moves it`() {
        // Will -2 under Fortitude is a ceiling of 1: legal. Without it, -2 is
        // three steps short -- which is what selling the trait would cost.
        assertEquals(SecondaryDelta(), at(SecondaryDelta(will = -11), listOf(fortitude)))
        assertEquals(SecondaryDelta(will = 3), at(SecondaryDelta(will = -11)))
    }

    @Test
    fun `the engine refuses exactly what the floors refuse`() {
        // The floors are the constants the guards read; one step under each
        // throws, and the floor itself does not.
        Pools.hp(ScoreFloors.MIN_POOL_MAX, ScoreFloors.MIN_POOL_MAX)
        assertThrows<IllegalArgumentException> { Pools.hp(0, ScoreFloors.MIN_POOL_MAX - 1) }
        assertThrows<IllegalArgumentException> { Pools.fp(0, ScoreFloors.MIN_POOL_MAX - 1) }
        Corruption.clamp(0, ScoreFloors.MIN_COR_CEILING)
        assertThrows<IllegalArgumentException> { Corruption.clamp(0, ScoreFloors.MIN_COR_CEILING - 1) }
    }

    @Test
    fun `no potion lowers anything but Will`() {
        // Why Drink checks the CoR ceiling alone: every other guarded score
        // can only rise along a pathway. If a block ever lowers HP, FP, Per or
        // an attribute, Drink needs the whole shortfall, not one row of it.
        val lowered = ContentLoader.pathways().flatMap { it.stats }.filter { it.delta < 0 }.map { it.attr } +
            NonStandardPathways.all().flatMap { it.stats }.filter { it.delta < 0 }.map { it.attr }
        assertEquals(setOf("Will"), lowered.toSet())
    }
}
