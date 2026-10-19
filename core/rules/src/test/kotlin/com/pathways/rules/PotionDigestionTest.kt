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
    fun `a granted skill with no points stands at its default plus its bonus`() {
        val digested = PotionDigestion.digest(mortal, seer)

        // v6.9i 4771-4774: "+N is a modifier applied on top of your purchased
        // skill level. It does NOT mean the skill is at Attribute + N." With
        // nothing bought the base is the untrained default (owner's ruling 1):
        // Occultism/IQ [Average] +2 at IQ 11 is IQ-5 + 2 = 8.
        assertEquals(8, digested.skillLevels.entries.single { it.key.name == "Occultism" }.value)
        // Divination Arts defaults to SPI-4 against the SPI the potion granted
        // (0 + 9): 5 + 3 = 8.
        assertEquals(9, digested.effectiveSpi)
        assertEquals(8, digested.skillLevels.entries.single { it.key.name == "Divination Arts" }.value)
        // Ritualistic Magic is Very Hard, with no default: no level until
        // bought, and its +3 waits on the grant.
        assertTrue(digested.skillLevels.keys.none { it.name == "Ritualistic Magic" })
        assertEquals(3, digested.grants.skillBonuses.entries.single { it.key.name == "Ritualistic Magic" }.value)
    }

    @Test
    fun `a potion's bonus on a default takes Versatile's +1, as every default does`() {
        val versatile = mortal.copy(
            traits = listOf(com.pathways.model.Trait(TraitEffects.VERSATILE, 5, com.pathways.model.TraitKind.ADVANTAGE)),
        )
        val digested = PotionDigestion.digest(versatile, seer)
        assertEquals(9, digested.skillLevels.entries.single { it.key.name == "Occultism" }.value)
    }

    @Test
    fun `a no-default skill takes its waiting bonus once bought`() {
        val key = SkillKey("Ritualistic Magic", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.VERY_HARD)
        val trained = mortal.copy(skills = listOf(PurchasedSkill(key, points = 4)))
        val bought = Derive.of(trained).skillLevels.getValue(key)
        assertEquals(bought + 3, PotionDigestion.digest(trained, seer).skillLevels.getValue(key))
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
    fun `a second potion's modifier stacks on the first's`() {
        // v6.9i 4781-4783: "the stat and skill modifiers from all potions stack."
        val key = SkillKey("Stealth", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE)
        val blocks = listOf(
            SequenceBlock(
                sequence = 9, name = "First", pathway = "Test",
                skills = listOf(SkillGrant("Stealth", key.attr, Difficulty.AVERAGE, bonus = 2)),
                ability = "test",
            ),
            SequenceBlock(
                sequence = 8, name = "Second", pathway = "Test",
                skills = listOf(SkillGrant("Stealth", key.attr, Difficulty.AVERAGE, bonus = 1)),
                ability = "test",
            ),
        )
        val walked = PotionDigestion.digestThrough(mortal, "Test", sequence = 8, blocks = blocks)
        // DX 9 - 5 = 4, then +2 +1.
        assertEquals(7, walked.skillLevels.getValue(key))
        assertEquals(3, walked.grants.skillBonuses.getValue(key))
    }

    @Test
    fun `a grant stacked on a bought skill rises with an attribute a later potion grants`() {
        // The owner's ruling, 2026-10-13: a bonus stacked on a BOUGHT skill
        // has points behind it, so it follows the attribute. It was frozen
        // with the grants that have none, and a boon's IQ +1 lifted every IQ
        // skill but the one a potion had touched.
        val key = SkillKey("Stealth", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE)
        val trained = mortal.copy(skills = listOf(PurchasedSkill(key, points = 4)))
        val bought = Derive.of(trained).skillLevels.getValue(key)
        val blocks = listOf(
            SequenceBlock(
                sequence = 9, name = "First", pathway = "Test",
                skills = listOf(SkillGrant("Stealth", key.attr, Difficulty.AVERAGE, bonus = 2)),
                ability = "test",
            ),
            SequenceBlock(
                sequence = 8, name = "Second", pathway = "Test",
                stats = listOf(StatGain("DX", 1.0)),
                skills = listOf(SkillGrant("Climbing", key.attr, Difficulty.AVERAGE, bonus = 1)),
                ability = "test",
            ),
        )

        val walked = PotionDigestion.digestThrough(trained, "Test", sequence = 8, blocks = blocks)

        assertEquals(bought + 1 + 2, walked.skillLevels.getValue(key))
        // v6.9i: a grant with no points behind it is a modifier on the
        // default, which follows the attribute too -- nothing is frozen.
        val unbought = SequenceBlock(
            sequence = 9, name = "First", pathway = "Test",
            skills = listOf(SkillGrant("Stealth", key.attr, Difficulty.AVERAGE, bonus = 2)),
            ability = "test",
        )
        val walkedUnbought = PotionDigestion.digestThrough(mortal, "Test", sequence = 8, blocks = listOf(unbought, blocks[1]))
        assertEquals(mortal.attributes.dx + 1 - 5 + 2, walkedUnbought.skillLevels.entries.single { it.key.name == "Stealth" }.value)
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
    fun `a bracketed grant is still the skill it names`() {
        // Demoness Sequence 7 is the one block in the book that grants the
        // ritual skill with a bracket: "Ritualistic Magic (Mirror, Dowsing)".
        // 6ba read that as unreachable by `skillLevelOf(RITUAL_SKILL)` and
        // left it as a slice; it is not, because [skillKey] splits the
        // bracket into a specialization, exactly as it does for "Guns
        // (Pistol)". The name a caller keys on is the bare one.
        val witch = PotionDigestion.digestThrough(
            mortal,
            pathway = "Demoness",
            sequence = 7,
            blocks = ContentLoader.pathways(),
        )

        val ritual = witch.grants.skillBonuses.entries.single { it.key.name == Conditions.RITUAL_SKILL }
        assertEquals("Mirror, Dowsing", ritual.key.specialization)
        assertEquals(4, ritual.value)
        // Very Hard, with no default: the +4 waits until it is bought.
        assertTrue(witch.skillLevels.keys.none { it.name == Conditions.RITUAL_SKILL })
    }

    @Test
    fun `every committed block digests without error`() {
        // The 88 blocks are the real input; a stat row this engine cannot
        // apply must fail here rather than in front of a player.
        ContentLoader.pathways().forEach { block -> PotionDigestion.digest(mortal, block) }
    }

    @Test
    fun `the unread ability prose is recorded as a gap, naming both fields`() {
        // UNPARSED exists so a sweep over Kotlin finds the
        // largest unbuilt thing in the repo. Both prose fields must be named:
        // reinforcedAbilities is a second printing of the same defect, and a
        // record covering only the first would read as complete.
        assertEquals(
            setOf("ability", "reinforcedAbilities"),
            PotionDigestion.UNPARSED.keys,
        )
        // The wording carries the distinction the KDoc argues for. A future
        // reader must not inherit this as a settled refusal of the
        // Equipment.UNMODELLED kind -- it is work nobody has done.
        assertTrue(
            PotionDigestion.UNPARSED.values.any { it.contains("not a refusal") },
            "the record must say this is unbuilt work rather than a refusal",
        )
    }

    @Test
    fun `the ability prose really does price abilities this engine never charges`() {
        // The claim the gap record rests on, measured rather than asserted.
        // If a future slice parses these costs, this test goes red and that is
        // the signal to update the record -- a gap that closes silently is how
        // a stale refusal outlives its reason.
        val blocks = ContentLoader.pathways()
        val priced = Regex("""\d+\s*SPI\b""")
        val mentions = blocks.sumOf {
            priced.findAll(it.ability + " " + it.reinforcedAbilities).count()
        }
        assertTrue(mentions > 200, "expected the prose to price abilities; found $mentions")

        // The scoping argument needs costs that sit OUTSIDE the named clause
        // shape, and comparing the two counts does not establish that: six
        // named clauses carry two prices each ("(2 SPI + 1 SPI/turn):"), so
        // the named shape accounts for more MENTIONS than it has CLAUSES.
        // Subtracting one count from the other credits those six to running
        // prose that does not exist. A review caught this: the assertion
        // passed, for a reason six cases weaker than it claimed.
        val named = Regex("""[A-Z][A-Za-z' -]{2,40}\s*\(\d+\s*SPI[^)]*\)\s*:""")
        val outsideNamed = blocks.sumOf { block ->
            val text = block.ability + " " + block.reinforcedAbilities
            val spans = named.findAll(text).map { it.range }.toList()
            priced.findAll(text).count { hit -> spans.none { hit.range.first in it } }
        }
        assertTrue(
            outsideNamed > 0,
            "the named-clause shape must not account for every printed SPI " +
                "cost, or a parser built on it alone would be sufficient and " +
                "the recorded gap overstates the problem",
        )
    }

    @Test
    fun `a per-turn boolean cannot carry what the book prints`() {
        // Why a per-turn boolean cost shape (the deleted Sealed Artifact
        // record's) was refused for these.
        // These abilities are priced per minute as well -- and one turn is one
        // second in this engine -- so the two are 60x apart and a boolean puts
        // them in the same bucket.
        val prose = ContentLoader.pathways().joinToString(" ") {
            it.ability + " " + it.reinforcedAbilities
        }
        assertTrue(prose.contains("SPI/turn"), "expected a per-turn price")
        assertTrue(
            prose.contains("per minute") || prose.contains("SPI/min"),
            "expected a per-minute price, which is the one a Boolean loses",
        )
        // And a per-UNIT price, which is neither and is what makes this a
        // question about kinds rather than about a longer enum.
        assertTrue(
            Regex("""per (raven|feature|fireball|hit|\d+ bullets)""").containsMatchIn(prose),
            "expected a per-unit price",
        )
    }
}
