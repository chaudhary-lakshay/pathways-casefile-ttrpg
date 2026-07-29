package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.BeyonderState
import com.pathways.model.canDrink
import com.pathways.model.Character
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.PurchasedSkill
import com.pathways.model.SkillKey
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SequenceBlock
import com.pathways.rules.content.SkillGrant
import com.pathways.rules.content.StatGain
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PotionDigestionTest {

    private val mortal = Character(name = "Klein", attributes = Attributes(st = 9, dx = 9, iq = 11, ht = 9))
    private val seer = ContentLoader.pathways().single { it.sequence == 9 && it.pathway == "Fool" }

    @Test
    fun `a granted skill lands at the controlling attribute plus its bonus`() {
        val digested = PotionDigestion.digest(mortal, seer)

        // Occultism/IQ [Average] +2 with IQ 11 -> 13. Read as "attribute + N",
        // not as two rungs up the ladder from default (p. 85).
        val occultism = digested.skillLevels.entries.single { it.key.name == "Occultism" }
        assertEquals(13, occultism.value)
        // Divination Arts/SPI [Hard] +3 resolves against the SPI the potion
        // just granted (0 + 9), not the SPI the character walked in with.
        assertEquals(9, digested.effectiveSpi)
        assertEquals(12, digested.skillLevels.entries.single { it.key.name == "Divination Arts" }.value)
    }

    @Test
    fun `a grant for a skill already known stacks on the level already paid for`() {
        // p. 86: "if your character already has Stealth/DX [Average] at level
        // 12, and a potion grants Stealth +2, your Stealth becomes 14 (not
        // DX+2)". The match is on name and difficulty, not on attribute.
        val key = SkillKey("Stealth", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE)
        val trained = mortal.copy(skills = listOf(PurchasedSkill(key, points = 4)))
        val purchasedLevel = Derive.of(trained).skillLevels.getValue(key)

        val block = SequenceBlock(
            sequence = 9, name = "Test", pathway = "Test",
            skills = listOf(SkillGrant("Stealth", key.attr, Difficulty.AVERAGE, bonus = 2)),
            ability = "test",
        )
        val digested = PotionDigestion.digest(trained, block)

        assertEquals(purchasedLevel + 2, digested.skillLevels.getValue(key))
        // and the skill is listed once, at the stacked level -- not twice
        assertEquals(1, digested.skillLevels.keys.count { it.name == "Stealth" })
    }

    @Test
    fun `a purchased skill rises with an attribute the potion granted`() {
        // A skill level is "controlling attribute + rungs", so a DX+1 potion
        // lifts every DX skill already bought -- not only the ones the potion
        // also grants. Reading purchased levels off the undigested character
        // left them at their mortal level.
        val key = SkillKey("Stealth", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE)
        val trained = mortal.copy(skills = listOf(PurchasedSkill(key, points = 4)))
        val before = Derive.of(trained).skillLevels.getValue(key)

        val block = SequenceBlock(
            sequence = 9, name = "Test", pathway = "Test",
            stats = listOf(StatGain("DX", 1.0)),
            ability = "test",
        )
        assertEquals(before + 1, PotionDigestion.digest(trained, block).skillLevels.getValue(key))
    }

    @Test
    fun `a stacked grant listed under a different attribute does not duplicate the skill`() {
        // The p. 86 match keys on name and difficulty only, so the grant's
        // SkillKey can differ from the purchased one by attribute. Merging by
        // map key alone left both entries in the sheet.
        val purchasedKey = SkillKey("Observation", null, ControllingAttribute.Per, Difficulty.AVERAGE)
        val trained = mortal.copy(skills = listOf(PurchasedSkill(purchasedKey, points = 4)))

        val block = SequenceBlock(
            sequence = 9, name = "Test", pathway = "Test",
            skills = listOf(
                SkillGrant("Observation", ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE, bonus = 2),
            ),
            ability = "test",
        )
        val digested = PotionDigestion.digest(trained, block)

        assertEquals(1, digested.skillLevels.keys.count { it.name == "Observation" })
    }

    @Test
    fun `potion gains are free and never reach the point budget`() {
        val digested = PotionDigestion.digest(mortal, seer)

        assertEquals(Derive.of(mortal).budget, digested.budget)
        // the purchased state itself is untouched, so a second digestion of
        // the same base cannot compound
        assertEquals(mortal, digested.base)
        assertEquals(digested.skillLevels, PotionDigestion.digest(mortal, seer).skillLevels)
    }

    @Test
    fun `a quarter-point Basic Speed grant survives as one quarter step`() {
        val assassin = ContentLoader.pathways().single { it.sequence == 9 && it.pathway == "Demoness" }
        val digested = PotionDigestion.digest(mortal, assassin)

        assertEquals(1, digested.grants.secondary.basicSpeedQuarters)
        assertEquals(
            SecondaryCharacteristics.of(digested.effectiveAttributes, mortal.secondaryDelta).basicSpeedQuarters + 1,
            digested.secondaries.basicSpeedQuarters,
        )
        // the free quarter is not written into the character's own delta,
        // which is what PointBudget bills
        assertEquals(0, digested.base.secondaryDelta.basicSpeedQuarters)
    }

    @Test
    fun `a Basic Speed grant that is not a whole quarter fails loudly`() {
        val block = SequenceBlock(
            sequence = 9, name = "Test", pathway = "Test",
            stats = listOf(StatGain("Basic Speed", 0.3)),
            ability = "test",
        )
        assertFailsWith<IllegalArgumentException> { PotionDigestion.digest(mortal, block) }
    }

    @Test
    fun `advancing to a sequence carries every potion before it`() {
        // A Sequence 8 Fool drank the Sequence 9 potion first: Seer grants
        // SPI +9 and Clown another +2, so the Clown stands at 11, not 2.
        // Digesting the Clown block alone describes one potion, not a Beyonder.
        val blocks = ContentLoader.pathways()
        val clownAlone = PotionDigestion.digest(mortal, blocks.single { it.sequence == 8 && it.pathway == "Fool" })
        val throughClown = PotionDigestion.digestThrough(mortal, "Fool", sequence = 8, blocks = blocks)

        assertEquals(2, clownAlone.effectiveSpi)
        assertEquals(11, throughClown.effectiveSpi)
        assertEquals(8, throughClown.sequence)
        // Seer's own grants are still there
        assertEquals(1, throughClown.grants.skillLevels.keys.count { it.name == "Occultism" })
    }

    @Test
    fun `a second potion granting a known skill stacks on the level the first one gave`() {
        // Spiritual Intuition/SPI [Hard] +3 from Seer, then +3 again from
        // Clown. The second grant reads as "current level + 3" (p. 86), and
        // current includes what the first potion granted.
        val blocks = ContentLoader.pathways()
        val seer = PotionDigestion.digestThrough(mortal, "Fool", sequence = 9, blocks = blocks)
        val clown = PotionDigestion.digestThrough(mortal, "Fool", sequence = 8, blocks = blocks)

        val at9 = seer.grants.skillLevels.entries.single { it.key.name == "Spiritual Intuition" }.value
        val at8 = clown.grants.skillLevels.entries.single { it.key.name == "Spiritual Intuition" }.value
        assertEquals(at9 + 3, at8)
    }

    @Test
    fun `advancing to a sequence the pathway lacks fails instead of reporting another one`() {
        // Partial content must not silently answer for Sequence 9 when
        // Sequence 8 was asked for -- the totals would be a real Beyonder's,
        // just the wrong one.
        val seerOnly = ContentLoader.pathways().filter { it.pathway == "Fool" && it.sequence == 9 }
        assertFailsWith<IllegalArgumentException> {
            PotionDigestion.digestThrough(mortal, "Fool", sequence = 8, blocks = seerOnly)
        }
    }

    @Test
    fun `a stored pathway and sequence digest to the same thing as asking directly`() {
        val blocks = ContentLoader.pathways()
        val beyonder = mortal.copy(beyonder = BeyonderState("Fool", 8))

        val fromState = PotionDigestion.of(beyonder, blocks)
        val asked = PotionDigestion.digestThrough(beyonder, "Fool", sequence = 8, blocks = blocks)

        assertEquals(asked.skillLevels, fromState?.skillLevels)
        assertEquals(asked.effectiveSpi, fromState?.effectiveSpi)
        // storing the choice cannot change what was paid for
        assertEquals(Derive.of(mortal).budget, fromState?.budget)
    }

    @Test
    fun `a mortal digests to nothing rather than to an empty Beyonder`() {
        assertEquals(null, PotionDigestion.of(mortal, ContentLoader.pathways()))
    }

    @Test
    fun `advancing walks down the pathway and stops at the strongest sequence held`() {
        assertEquals(BeyonderState("Fool", 8), BeyonderState("Fool", 9).advanced())
        assertEquals(null, BeyonderState("Fool", 6).advanced())
        assertFailsWith<IllegalArgumentException> { BeyonderState("Fool", 5) }
    }

    @Test
    fun `a pathway is walked one potion at a time`() {
        // Without this, a mortal could store "Sequence 6" and be granted the
        // whole chain as though the three skipped potions had been drunk.
        val seer = BeyonderState("Fool", 9)
        val clown = BeyonderState("Fool", 8)

        assertTrue(null.canDrink(seer))
        assertFalse(null.canDrink(clown))
        assertTrue(seer.canDrink(clown))
        assertFalse(seer.canDrink(BeyonderState("Fool", 7)))
        assertFalse(seer.canDrink(seer))
        // a different pathway is a different Beyonder, not an advancement
        assertFalse(seer.canDrink(BeyonderState("Error", 8)))
    }

    @Test
    fun `every pathway advances to sequence 6 without error`() {
        val blocks = ContentLoader.pathways()
        blocks.map { it.pathway }.distinct().forEach { pathway ->
            PotionDigestion.digestThrough(mortal, pathway, sequence = 6, blocks = blocks)
        }
    }

    @Test
    fun `every committed block digests without error`() {
        // The 88 blocks are the real input; a stat row this engine cannot
        // apply must fail here rather than in front of a player.
        ContentLoader.pathways().forEach { block -> PotionDigestion.digest(mortal, block) }
    }
}
