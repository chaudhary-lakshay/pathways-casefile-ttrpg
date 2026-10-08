package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.Outcome
import com.pathways.model.SkillKey
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The untrained roll (6et): ch. 2's table, and the lines that depart from it.
 *
 * `SkillLadder.defaultFor` had no production caller from M0 until this
 * object, so every number below was a number no screen could show.
 */
class SkillDefaultsTest {

    private fun skill(name: String, specialization: String? = null) =
        ContentLoader.skills().single { it.name == name && it.specialization == specialization }

    /** Every attribute at [level], which is all the table needs. */
    private fun flat(level: Int): (ControllingAttribute) -> Int = { level }

    private fun offered(
        owned: Map<SkillKey, Int> = emptyMap(),
        level: Int = 10,
        bonus: Int = 0,
    ) = SkillDefaults.offered(owned, flat(level), bonus)

    /**
     * The book's own worked example, p. 10: "A character with IQ 9 ... tries
     * to pick a lock (Lockpicking, IQ/Average) without any training. Their
     * default is IQ - 5 = 4."
     */
    @Test
    fun `the book's own example - Lockpicking at IQ 9 is 4`() {
        val row = offered(level = 9).single { it.key.name == "Lockpicking" }
        assertEquals(Difficulty.AVERAGE, row.key.difficulty)
        assertEquals(4, row.level)
        assertEquals("IQ-5", row.source)
    }

    /**
     * The table, for every row that takes it, stated here and not asked of
     * the code: attribute -4 / -5 / -6 by difficulty.
     */
    @Test
    fun `every other skill takes the table's penalty for its difficulty`() {
        val table = mapOf(Difficulty.EASY to -4, Difficulty.AVERAGE to -5, Difficulty.HARD to -6)
        val special = setOf(SkillDefaults.BRAWLING, SkillDefaults.WRESTLING, Divination.SKILL)
        val rows = offered(level = 11).associateBy { it.key.name to it.key.specialization }
        for (printed in ContentLoader.skills()) {
            if (printed.name in special || printed.difficulty == Difficulty.VERY_HARD) continue
            val row = rows.getValue(printed.name to printed.specialization)
            assertEquals(11 + table.getValue(printed.difficulty), row.level, printed.name)
        }
    }

    /**
     * 158 printed, less the 7 Very Hard rows ("No default -- cannot attempt
     * untrained"), Wrestling ("No default") and Divination Arts, whose one
     * default needs a skill this character does not hold.
     */
    @Test
    fun `a character who owns nothing is offered 149 skills`() {
        val all = ContentLoader.skills()
        assertEquals(158, all.size)
        assertEquals(7, all.count { it.difficulty == Difficulty.VERY_HARD })
        val rows = offered()
        assertEquals(149, rows.size)
        assertTrue(rows.none { it.key.difficulty == Difficulty.VERY_HARD })
        assertTrue(rows.none { it.key.name == SkillDefaults.WRESTLING })
        assertTrue(rows.none { it.key.name == Divination.SKILL })
    }

    /**
     * **Ch. 5's own lines beat ch. 2's table** (p. 38-39): "Brawling ...
     * Default: DX-2 if untrained" against Easy's -4, "Boxing ... Default DX-5"
     * agreeing with Average, and Wrestling's "No default" against Average's
     * -5. The agreement is what shows the other two are overrides.
     */
    @Test
    fun `Brawling is DX-2, Boxing DX-5 and Wrestling has none`() {
        val rows = offered(level = 10).associateBy { it.key.name }
        assertEquals(8, rows.getValue(SkillDefaults.BRAWLING).level)
        assertEquals("DX-2, the book's own line for Brawling", rows.getValue("Brawling").source)
        assertEquals(5, rows.getValue("Boxing").level)
        assertNull(rows[SkillDefaults.WRESTLING])
        // The table would have said otherwise of both, or these are not overrides.
        assertEquals(Difficulty.EASY, skill("Brawling").difficulty)
        assertEquals(Difficulty.AVERAGE, skill("Wrestling").difficulty)
    }

    /** "+1 to defaults skill rolls" (p. 13): once, and said. */
    @Test
    fun `Versatile's +1 is inside the level and named in the source`() {
        val plain = offered(level = 10).single { it.key.name == "Stealth" }
        val lifted = offered(level = 10, bonus = 1).single { it.key.name == "Stealth" }
        assertEquals(plain.level + 1, lifted.level)
        assertEquals("${plain.source}, +1 ${TraitEffects.VERSATILE}", lifted.source)
        // A skill with a line of its own is still rolled at a default.
        fun brawling(bonus: Int) =
            offered(level = 10, bonus = bonus).single { it.key.name == SkillDefaults.BRAWLING }.level
        assertEquals(brawling(0) + 1, brawling(1))
    }

    /**
     * **Divination Arts has one default, and it is the Divine tile's.** Line
     * 3559 prints "Divination Arts defaults to Ritualistic Magic-2 ... A
     * character with one skill at 12+ can attempt the other at -2", read here
     * as Brawling's line is: the skill's own line replaces the table's, so
     * SPI-6 is not offered beside it. One resolver, so the two screens cannot
     * print two numbers for one roll.
     */
    @Test
    fun `Divination Arts defaults from Ritualistic Magic at 12 and from nothing else`() {
        fun ritual(level: Int) = mapOf(
            SkillKey(
                Divination.SIBLING_SKILL, null,
                ControllingAttribute.Buyable(Attr.IQ), Difficulty.VERY_HARD,
            ) to level,
        )
        assertNull(offered(ritual(11)).singleOrNull { it.key.name == Divination.SKILL })
        val row = offered(ritual(13), bonus = 1).single { it.key.name == Divination.SKILL }
        assertEquals(12, row.level)
        assertEquals(Divination.divinationLevelFor(null, 13, 1)!!.level, row.level)
        assertEquals(
            "${Divination.SIBLING_SKILL} 13 -2, +1 ${TraitEffects.VERSATILE}",
            row.source,
        )
    }

    /**
     * Owned means the same NAME AND SPECIALIZATION, whatever the difficulty:
     * five potion grants print a different difficulty from chapter 3's
     * (`PathwayContentTest`), and a Knife the potion gave is not a second
     * skill to roll untrained. Safe because the catalogue prints no name and
     * specialization twice -- which is pinned here, since the day it does this
     * rule merges two skills.
     */
    @Test
    fun `an owned skill is not offered, whatever difficulty it was granted at`() {
        val printed = ContentLoader.skills().map { it.name to it.specialization }
        assertEquals(printed.size, printed.toSet().size)
        val knife = skill("Knife")
        assertTrue(offered().any { it.key.name == "Knife" })
        val other = Difficulty.entries.first { it != knife.difficulty }
        val owned = mapOf(SkillKey("Knife", null, knife.attr, other) to 12)
        assertTrue(offered(owned).none { it.key.name == "Knife" })
        assertEquals(148, offered(owned).size)
    }

    /**
     * An open row is a different skill from an instance of it: Hidden Lore
     * (Beyonders) says nothing about Hidden Lore (Demons). So the open row
     * stays on offer, and one specialization does not cover its siblings.
     */
    @Test
    fun `an instance of an open row leaves the open row on offer`() {
        val open = skill("Hidden Lore")
        assertTrue(open.specializable)
        val owned = mapOf(SkillKey("Hidden Lore", "Beyonders", open.attr, open.difficulty) to 12)
        assertTrue(offered(owned).any { it.key.name == "Hidden Lore" && it.key.specialization == null })
        val pistol = skill("Guns", "Pistol")
        val shooter = mapOf(SkillKey("Guns", "Pistol", pistol.attr, pistol.difficulty) to 12)
        val guns = offered(shooter).filter { it.key.name == "Guns" }.map { it.key.specialization }
        assertEquals(listOf("Revolver", "Rifle", "Shotgun"), guns)
    }

    /**
     * **Three potion blocks print "no default penalty" for weapons, and none
     * is applied.** Which skill a weapon uses is a property of the weapon in
     * hand, which this list does not ask about, so the list says so to the
     * characters it is true of. The census is the book's phrase over every
     * block, so a fourth block reddens rather than going unsaid.
     */
    @Test
    fun `the three weapon lines are named to the characters who walked them`() {
        val printing = ContentLoader.pathways().filter {
            "default penalty" in (it.ability + " " + it.reinforcedAbilities)
        }
        assertEquals(
            SkillDefaults.WEAPON_LINES,
            printing.map { it.pathway to it.name }.toSet(),
        )
        assertEquals(emptyList<String>(), SkillDefaults.weaponLinesFor("Fool", 9))
        assertEquals(listOf("Warrior"), SkillDefaults.weaponLinesFor("Twilight Giant", 9))
        assertEquals(
            listOf("Warrior", "Weapon Master"),
            SkillDefaults.weaponLinesFor("Twilight Giant", 7),
        )
        assertEquals(listOf("Harvest Priest"), SkillDefaults.weaponLinesFor("Mother", 6))
        assertEquals(emptyList<String>(), SkillDefaults.weaponLinesFor("Mother", 8))
    }

    /**
     * **The character as PLAYED**: a potion that lifts an attribute lifts
     * every default hanging off it, and a skill a potion granted is owned.
     * The expectations are read off the digested character, never off
     * [SkillDefaults], so the same levels a bought skill rolls from are what
     * this must agree with.
     */
    @Test
    fun `a Beyonder's defaults come off the digested character`() {
        val mortal = Premades.byName("Beat Cop").toCharacter()
        val seer = mortal.copy(beyonder = com.pathways.model.BeyonderState("Fool", 9, 0))
        val walked = PotionDigestion.of(seer, ContentLoader.pathways())!!
        assertEquals(9, walked.effectiveSpi - mortal.spi)

        val before = SkillDefaults.offeredTo(mortal).associateBy { it.key.name to it.key.specialization }
        val after = SkillDefaults.offeredTo(seer).associateBy { it.key.name to it.key.specialization }
        // Spiritual Perception is SPI/Average and no Seer block grants it.
        val perception = "Spiritual Perception" to null
        assertEquals(mortal.spi - 5, before.getValue(perception).level)
        assertEquals(walked.effectiveSpi - 5, after.getValue(perception).level)
        // The potion's own grants are owned now, so they are not on offer.
        for (granted in listOf("Spiritual Intuition", Divination.SKILL, Divination.SIBLING_SKILL)) {
            assertTrue(walked.skillLevels.keys.any { it.name == granted }, granted)
            assertNull(after[granted to null], granted)
        }
        // And nothing the sheet prints is offered to either.
        for (owned in Derive.of(mortal).skillLevels.keys) {
            assertNull(before[owned.name to owned.specialization], owned.display)
        }
        // The trait is read off the character, not handed in by the screen.
        val versatile = mortal.copy(
            traits = mortal.traits +
                com.pathways.model.Trait(TraitEffects.VERSATILE, 5, com.pathways.model.TraitKind.ADVANTAGE),
        )
        assertEquals(
            before.getValue(perception).level + 1,
            SkillDefaults.offeredTo(versatile).single { it.key.name == perception.first }.level,
        )
        // A potion that moves DX and Per moves every default hanging off
        // them: the Red Priest's Hunter is DX +2, Per +1. Per is read off the
        // SECONDARY and never off IQ (4.4).
        val hunter = mortal.copy(beyonder = com.pathways.model.BeyonderState("Red Priest", 9, 0))
        val hunted = PotionDigestion.of(hunter, ContentLoader.pathways())!!
        assertEquals(2, hunted.effectiveAttributes.dx - mortal.attributes.dx)
        val asHunter = SkillDefaults.offeredTo(hunter).associateBy { it.key.name to it.key.specialization }
        val dx = ControllingAttribute.Buyable(Attr.DX)
        for (attr in listOf(dx, ControllingAttribute.Per)) {
            val printed = ContentLoader.skills().first {
                it.attr == attr && (it.name to it.specialization) in asHunter &&
                    (it.name to it.specialization) in before && it.name != SkillDefaults.BRAWLING
            }
            val key = printed.name to printed.specialization
            val level = if (attr == dx) hunted.effectiveAttributes.dx else hunted.secondaries.per
            assertEquals(level + printed.difficulty.defaultPenalty!!, asHunter.getValue(key).level, printed.name)
            assertEquals(
                if (attr == dx) 2 else 1,
                asHunter.getValue(key).level - before.getValue(key).level,
                printed.name,
            )
        }
    }

    /**
     * What the list leaves out is said on the list, by name: a skill missing
     * from 149 rows reads as an oversight unless the screen says it was
     * withheld. The three names are the three ways a row is absent.
     */
    @Test
    fun `the list says what it leaves out and what it does not carry`() {
        for (absent in listOf("Very Hard", SkillDefaults.WRESTLING, Divination.SKILL)) {
            assertTrue(absent in SkillDefaults.NOT_LISTED, absent)
        }
        assertTrue("12" in SkillDefaults.NOT_LISTED, SkillDefaults.NOT_LISTED)
        assertNull(SkillDefaults.weaponNote("Fool", 9))
        val note = SkillDefaults.weaponNote("Twilight Giant", 7)!!
        assertTrue(note.startsWith("Not applied here: Warrior's \"no default penalty\" with any"), note)
        assertTrue("Weapon Master's" in note, note)
        // The Harvest Priest's line is narrower than the other two, and a note
        // that dropped the restriction would promise the wider exemption.
        val priest = ContentLoader.pathways().single { it.name == "Harvest Priest" }
        assertTrue(
            "any farming or gardening tool as a weapon at no default penalty" in
                priest.reinforcedAbilities,
            priest.reinforcedAbilities,
        )
        val narrow = SkillDefaults.weaponNote("Mother", 7)!!
        assertTrue("farming or gardening tool" in narrow, narrow)
        assertTrue("any weapon" !in narrow, narrow)
    }

    /**
     * **Through the roll, not only as a number.** A mortal's SPI is 0, so an
     * SPI default is negative -- and [SuccessRoll] still gives a 3 or a 4 its
     * critical success, which is this app's standing reading and not a floor
     * this object adds. A default also takes what the trait rows add to the
     * skill it names, because it IS a roll of that skill.
     */
    @Test
    fun `an untrained row rolls through the shared path, negative or modified`() {
        val mortal = Premades.byName("Beat Cop").toCharacter()
        val perception = SkillDefaults.offeredTo(mortal).single { it.key.name == "Spiritual Perception" }
        assertEquals(-5, perception.level)
        val three = SkillRoll.at("x", perception.level, perception.key, RandomSource { 1 })
        assertEquals(-5, three.target)
        assertEquals(Outcome.CRITICAL_SUCCESS, three.roll!!.outcome)
        val ten = SkillRoll.at("x", perception.level, perception.key, RandomSource { 3 })
        assertTrue(!ten.roll!!.outcome.isSuccess)

        val acrobatics = offered(level = 10).single { it.key.name == "Acrobatics" }
        val balanced = listOf(
            com.pathways.model.Trait(TraitEffects.PERFECT_BALANCE, 15, com.pathways.model.TraitKind.ADVANTAGE),
        )
        val lifted = SkillRoll.at(
            "x", acrobatics.level, acrobatics.key, RandomSource { 3 },
            mods = TraitEffects.modifiers(balanced, acrobatics.key),
        )
        assertEquals(4, acrobatics.level)
        assertEquals(6, lifted.target)
    }
}
