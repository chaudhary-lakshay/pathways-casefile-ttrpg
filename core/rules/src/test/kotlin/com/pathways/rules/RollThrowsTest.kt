package com.pathways.rules

import com.pathways.model.DiceExpr
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every die a roller takes from a [RandomSource] is reported back through
 * [RandomSource.thrown], so the app's roll log sees every throw: the success
 * rolls, the fright, ritual-critical and entity tables, and damage dice.
 *
 * Two independent counts, compared: the faces the source HANDED OUT, and the
 * faces the rollers REPORTED. A sixth roller that takes dice without
 * reporting them fails here.
 */
class RollThrowsTest {

    private class Recording(seed: Long) : RandomSource {
        private val dice = SeededRandom(seed)
        val handed = mutableListOf<Int>()
        val reported = mutableListOf<Int>()
        val targets = mutableListOf<Int?>()
        override fun roll(sides: Int): Int = dice.roll(sides).also { handed += it }
        override fun thrown(faces: List<Int>, target: Int?) {
            reported += faces
            targets += target
        }
    }

    /** [throwOnce] run over enough seeds to reach each engine's table branch. */
    private fun everyDieReported(throwOnce: (RandomSource) -> Unit) {
        repeat(300) { seed ->
            val source = Recording(seed.toLong())
            throwOnce(source)
            assertTrue(source.handed.isNotEmpty(), "seed $seed rolled nothing")
            assertEquals(source.handed, source.reported, "seed $seed")
        }
    }

    @Test
    fun `a success roll reports its three dice and its target`() {
        val source = Recording(1)
        val result = SuccessRoll.against(11, source)
        assertEquals(source.handed, source.reported)
        assertEquals(listOf<Int?>(11), source.targets)
        assertEquals(source.reported.sum(), result.total)
    }

    @Test
    fun `a fright check reports the effect table's dice too`() =
        everyDieReported { FrightCheck.rollAgainst(8, it) }

    @Test
    fun `a ritual reports the critical table's dice too`() =
        everyDieReported { RitualResolution.rollAgainst(4, it) }

    @Test
    fun `an entity's answer reports its dice`() =
        everyDieReported { Invocation.ask(it, orthodoxInGoodStanding = false) }

    @Test
    fun `damage reports its dice`() =
        everyDieReported { Damage.roll(DamageRoll(DiceExpr(2, 6, 1), "pi"), it) }

    @Test
    fun `a bare table throw reports its dice with no target`() {
        val source = Recording(3)
        source.threeDSix()
        assertEquals(source.handed, source.reported)
        assertEquals(listOf<Int?>(null), source.targets)
    }
}
