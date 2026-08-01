package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.SkillKey
import com.pathways.model.SourceKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The trackers reaching the dice.
 *
 * Every number here already existed in [Pools] and [Corruption]; what is under
 * test is whether it reaches the right roll and only the right roll.
 */
class ConditionsTest {

    private val dxSkill = SkillKey("Brawling", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.EASY)
    private val spiSkill = SkillKey("Spiritual Intuition", null, ControllingAttribute.Spi, Difficulty.HARD)
    private val ritual = SkillKey("Ritualistic Magic", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.VERY_HARD)

    private val healthy = Pools.hp(10, 10)
    private val rested = Pools.fp(10, 10)

    @Test
    fun `a healthy character carries nothing`() {
        assertTrue(Conditions.forSkill(dxSkill, healthy, rested).isEmpty())
    }

    @Test
    fun `reeling reaches every roll`() {
        // p. 40: "1/3 HP remaining -- Reeling: -1 to all rolls".
        val mods = Conditions.forSkill(dxSkill, Pools.hp(3, 10), rested)
        assertEquals(listOf(ModEffect.Flat(-1)), mods.map { it.effect })
        assertEquals(SourceKind.STATE, mods.single().source.kind)
    }

    @Test
    fun `fatigue and injury stack as two separate modifiers`() {
        // The book gives no rule collapsing them, and the resolver is what
        // decides how two penalties combine -- not this list.
        val mods = Conditions.forSkill(dxSkill, Pools.hp(3, 10), Pools.fp(-3, 12))
        assertEquals(2, mods.size)
        assertEquals(listOf(-1, -3), mods.map { (it.effect as ModEffect.Flat).n })
    }

    @Test
    fun `spiritual attrition reaches SPI skills only`() {
        // p. 35: "-3 to all SPI-based skill rolls and Beyonder ability
        // checks" -- not to a punch.
        val drained = Pools.spi(2, 9)
        assertTrue(Conditions.forSkill(dxSkill, healthy, rested, drained).isEmpty())
        val onSpi = Conditions.forSkill(spiSkill, healthy, rested, drained)
        assertEquals(listOf(-3), onSpi.map { (it.effect as ModEffect.Flat).n })
    }

    @Test
    fun `a mortal has no SPI penalty to carry`() {
        // Pools.spi refuses a maximum of zero, so a mortal passes null.
        assertTrue(Conditions.forSkill(spiSkill, healthy, rested, spi = null).isEmpty())
    }

    @Test
    fun `corruption reaches ritual rolls only`() {
        // p. 68 files the ladder under "Corruption from Ritual Work".
        assertTrue(Conditions.forSkill(dxSkill, healthy, rested, cor = 9).isEmpty())
        val onRitual = Conditions.forSkill(ritual, healthy, rested, cor = 9)
        assertEquals(listOf(-1), onRitual.map { (it.effect as ModEffect.Flat).n })
    }

    @Test
    fun `corruption below the first threshold costs nothing`() {
        assertTrue(Conditions.forSkill(ritual, healthy, rested, cor = 2).isEmpty())
    }

    @Test
    fun `a dying ritualist carries every penalty that applies at once`() {
        val mods = Conditions.forSkill(ritual, Pools.hp(0, 10), Pools.fp(-2, 12), Pools.spi(1, 9), cor = 5)
        // Reeling, fatigue and corruption -- but NOT attrition, because
        // Ritualistic Magic is an IQ skill.
        assertEquals(listOf(-1, -2, -1), mods.map { (it.effect as ModEffect.Flat).n })
    }

    @Test
    fun `a penalty is tagged so a future cancel can find it`() {
        val mods = Conditions.forSkill(dxSkill, Pools.hp(3, 10), rested)
        assertTrue(mods.all { it.source.kind == SourceKind.STATE })
    }
}
