package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.Attributes
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
    fun `every committed block digests without error`() {
        // The 88 blocks are the real input; a stat row this engine cannot
        // apply must fail here rather than in front of a player.
        ContentLoader.pathways().forEach { block -> PotionDigestion.digest(mortal, block) }
    }
}
