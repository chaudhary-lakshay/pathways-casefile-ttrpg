package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.SkillKey
import com.pathways.model.SourceKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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
    fun `reeling reaches every skill roll`() {
        // p. 40: "1/3 HP remaining -- Reeling: -1 to all rolls". Named for the
        // reach this function HAS: it answers for a SkillKey, so "every roll"
        // was a claim about the other three RollKinds it cannot see.
        val mods = Conditions.forSkill(dxSkill, Pools.hp(3, 10), rested)
        assertEquals(listOf(ModEffect.Flat(-1)), mods.map { it.effect })
        assertEquals(SourceKind.STATE, mods.single().source.kind)
    }

    @Test
    fun `reeling and fatigue reach the three rolls the ledger never sees`() {
        // The same sentence, p. 40 and p. 42: "-1 to all rolls". A defence
        // value, a bare attribute roll and the Major Wound's own HT roll are
        // rolls this app makes outside SkillKey, and "all" contains them.
        val hurt = Pools.hp(3, 10)
        val tired = Pools.fp(3, 12)
        for (kind in listOf(RollKind.ACTIVE_DEFENSE, RollKind.ATTRIBUTE, RollKind.MAJOR_WOUND_HT)) {
            assertEquals(-2, Conditions.penaltyFor(kind, hurt, tired), "$kind takes both")
        }
        assertEquals(0, Conditions.penaltyFor(RollKind.ACTIVE_DEFENSE, healthy, rested))
    }

    @Test
    fun `the Major Wound HT roll takes the pool penalties shock is refused`() {
        // Shock excludes that roll because the roll CREATES the shock. A
        // character was already reeling before the blow landed, so the
        // exclusion is about WHEN and does not transfer.
        assertEquals(0, Shock.penaltyFor(RollKind.MAJOR_WOUND_HT, shocked = true, traits = emptyList()))
        assertEquals(-1, Conditions.penaltyFor(RollKind.MAJOR_WOUND_HT, Pools.hp(3, 10), rested))
    }

    @Test
    fun `a skill roll is refused this number because the ledger already carries it`() {
        // The guard, not the feature: a caller that took both would charge a
        // reeling character twice, and nothing else in the app would notice.
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            Conditions.penaltyFor(RollKind.SKILL, Pools.hp(3, 10), rested)
        }
        assertTrue(
            thrown.message!!.contains("forSkill"),
            "the guard should name the path that already carries it, was: ${thrown.message}",
        )
    }

    @Test
    fun `a scoped penalty stays out of the unscoped number`() {
        // Attrition prints "all SPI-based SKILL rolls" (p. 35) and the CoR
        // ladder "all ritual rolls" (p. 68). Both name a scope, so neither is
        // in a number handed to a defence value that has no skill at all.
        val drained = Pools.spi(2, 9)
        assertEquals(-3, drained.penalty, "the SPI penalty exists")
        assertEquals(0, Conditions.penaltyFor(RollKind.ACTIVE_DEFENSE, healthy, rested))
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
