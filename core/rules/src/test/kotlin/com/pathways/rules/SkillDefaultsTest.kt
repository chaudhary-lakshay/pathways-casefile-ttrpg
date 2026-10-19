package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.Outcome
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.model.TraitKind
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
        traits: List<Trait> = emptyList(),
    ) = SkillDefaults.offered(owned, flat(level), bonus, traits)

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
     * Every printed row, less the Very Hard ones ("No default -- cannot
     * attempt untrained"), Wrestling ("No default") and Divination Arts,
     * whose SPI-4 is rolled from the Divine tile alone.
     */
    @Test
    fun `a character who owns nothing is offered 147 skills`() {
        val all = ContentLoader.skills()
        assertEquals(156, all.size)
        assertEquals(7, all.count { it.difficulty == Difficulty.VERY_HARD })
        val rows = offered()
        assertEquals(147, rows.size)
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
     * Medical Training (Informal), book 861: "Use Physician at IQ-2 without
     * purchasing the skill" (6gy). 6eq left it as "a gap, not a ruling":
     * no screen had a row for a skill a character does not own. 6et built
     * that list, and Physician (IQ/Hard) has stood on it at the table's IQ-6
     * for a holder ever since.
     *
     * The census is the cell's own phrase, so a second row granting a level
     * reddens here rather than being offered at the table's number.
     */
    @Test
    fun `Medical Training rolls Physician at IQ-2 where the table gives IQ-6`() {
        val granting = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .filter { "without purchasing the skill" in it.notes }
        assertEquals(listOf(SkillDefaults.MEDICAL_TRAINING), granting.map { it.name })
        assertTrue(
            granting.single().notes.startsWith("Use ${SkillDefaults.PHYSICIAN} at IQ-2 without"),
            granting.single().notes,
        )
        fun physician(traits: List<Trait>) =
            offered(level = 10, traits = traits).single { it.key.name == SkillDefaults.PHYSICIAN }
        assertEquals(4, physician(emptyList()).level)
        assertEquals("IQ-6", physician(emptyList()).source)
        val granted = physician(listOf(medicalTraining))
        assertEquals(8, granted.level)
        assertEquals("IQ-2, ${SkillDefaults.MEDICAL_TRAINING}", granted.source)
    }

    /**
     * **Not a default, so Versatile's +1 stays off it.** "Without purchasing
     * the skill" gives a level and never calls it a default, the reading
     * 6eq's design review declined to go past, and the Warrior's weapon "at
     * effective DX" takes none on the same ground. IQ-2 also beats the
     * table's IQ-6 with the +1, so the grant is never the worse number.
     */
    @Test
    fun `Versatile lifts a holder's other defaults and not the granted Physician`() {
        val rows = offered(level = 10, bonus = 1, traits = listOf(medicalTraining))
        val physician = rows.single { it.key.name == SkillDefaults.PHYSICIAN }
        assertEquals(8, physician.level)
        assertTrue(TraitEffects.VERSATILE !in physician.source, physician.source)
        val stealth = rows.single { it.key.name == "Stealth" }
        assertEquals(6, stealth.level)
        assertTrue(stealth.source.endsWith("+1 ${TraitEffects.VERSATILE}"), stealth.source)
    }

    /** "Without purchasing the skill": a Physician already held is not on the list twice, or at all. */
    @Test
    fun `a holder who owns Physician is offered no second row`() {
        val row = skill(SkillDefaults.PHYSICIAN)
        val owned = mapOf(SkillKey(row.name, row.specialization, row.attr, row.difficulty) to 12)
        val rows = offered(owned, traits = listOf(medicalTraining))
        assertEquals(emptyList<String>(), rows.map { it.key.name }.filter { it == SkillDefaults.PHYSICIAN })
    }

    private val medicalTraining = Trait(SkillDefaults.MEDICAL_TRAINING, 5, TraitKind.ADVANTAGE)

    /**
     * **Divination Arts is rolled from the Divine tile alone.** Its SPI-4
     * default is a divination, which pays its method's SPI before the roll
     * and takes the question's modifiers; a plain roll from this list would
     * skip both (Codex). So it is withheld, and the list says so.
     */
    @Test
    fun `Divination Arts is withheld for the Divine tile`() {
        assertTrue(offered().none { it.key.name == Divination.SKILL })
        assertTrue(Divination.SKILL in SkillDefaults.NOT_LISTED, SkillDefaults.NOT_LISTED)
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
        assertEquals(146, offered(owned).size)
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
        for (granted in listOf("Spiritual Intuition", Divination.SKILL, Conditions.RITUAL_SKILL)) {
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
     * from 147 rows reads as an oversight unless the screen says it was
     * withheld. The three names are the three ways a row is absent.
     */
    @Test
    fun `the list says what it leaves out and what it does not carry`() {
        for (absent in listOf("Very Hard", SkillDefaults.WRESTLING, Divination.SKILL)) {
            assertTrue(absent in SkillDefaults.NOT_LISTED, absent)
        }
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
        // At DX 2, Acrobatics' DX-6 is below zero.
        val perception = offered(level = 2).single { it.key.name == "Acrobatics" }
        assertEquals(-4, perception.level)
        val three = SkillRoll.at("x", perception.level, perception.key, RandomSource { 1 })
        assertEquals(-4, three.target)
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

    /**
     * **A sheet's spelling of a one-row skill is that skill.** Five premades
     * print "Mathematics" or "History (Occult)" where the book prints
     * "Mathematics (Applied)" and "History", and the first version of this
     * list offered each of them their own skill again, untrained, eight or
     * nine below what they roll it at (a probe of every premade through
     * every potion, after the diff review).
     *
     * The rule is the catalogue's own shape, not a list of the names seen:
     * where the book prints ONE row of a name, that row is not open, and one
     * of the two spellings has no specialization, there is nothing else the
     * owned skill could be. Two printed specializations that differ stay two
     * skills -- Fast-Draw (Revolver) is not the book's Fast-Draw (Pistol).
     */
    @Test
    fun `a sheet's spelling of a one-row skill covers that row`() {
        fun owning(name: String, specialization: String?) = offered(
            mapOf(SkillKey(name, specialization, ControllingAttribute.Buyable(Attr.IQ), Difficulty.HARD) to 12),
        ).filter { it.key.name == name }.map { it.key.specialization }

        // What the book prints, which is what makes each case what it is.
        assertEquals(listOf<String?>(null), ContentLoader.skills().filter { it.name == "History" }.map { it.specialization })
        assertTrue(!skill("History").specializable)
        assertEquals(listOf<String?>("Applied"), ContentLoader.skills().filter { it.name == "Mathematics" }.map { it.specialization })
        assertEquals(listOf<String?>("Pistol"), ContentLoader.skills().filter { it.name == "Fast-Draw" }.map { it.specialization })

        assertEquals(emptyList<String?>(), owning("History", "Occult"))
        assertEquals(emptyList<String?>(), owning("Mathematics", null))
        assertEquals(listOf<String?>("Pistol"), owning("Fast-Draw", "Revolver"))
        // An open row is never covered by an instance, however it is spelt.
        assertEquals(listOf<String?>(null), owning("Law", "Police"))
        // And a name the book prints three times is three skills: the bare
        // Navigation row is its own, not a spelling of Navigation (Land).
        assertEquals(listOf(null, "Sea"), owning("Navigation", "Land"))
    }

    /**
     * The same question asked of the real corpus: every premade as printed,
     * and one of them through every potion block. An offered row may share a
     * NAME with an owned skill only where the book makes them two skills --
     * an open row, or a name it prints more than once.
     *
     * **A placeholder grant covers nothing, on purpose.** Warrior grants
     * "Armoury (any)" and Hunter "Survival (any terrain)": the player chose
     * one and this app was not told which (M3's recorded placeholders), so
     * the open row stays on offer beside it.
     */
    @Test
    fun `nothing a premade or a potion owns is offered back to it`() {
        val printed = ContentLoader.skills()
        val blocks = ContentLoader.pathways()
        val base = Premades.all().map { it.toCharacter() }
        val walked = blocks.map {
            base.first().copy(beyonder = com.pathways.model.BeyonderState(it.pathway, it.sequence, 0))
        }
        val wrong = sortedSetOf<String>()
        for (character in base + walked) {
            val owned = (
                PotionDigestion.of(character, blocks)?.skillLevels ?: Derive.of(character).skillLevels
                ).keys
            for (row in SkillDefaults.offeredTo(character)) {
                val rows = printed.filter { it.name == row.key.name }
                val twoSkills = rows.size > 1 || rows.single().specializable ||
                    owned.none { it.name.equals(row.key.name, ignoreCase = true) && (it.specialization == null || row.key.specialization == null) }
                if (!twoSkills) wrong += row.key.display
            }
        }
        assertEquals(emptySet<String>(), wrong.toSet())
    }

    // ---- 6eu: the attack a default can make --------------------------------

    /**
     * **The untrained skills the Attack dialog can make an attack with**: the
     * ones with an unarmed table or a row in chapter 8's weapon table. That
     * is this app's boundary and not a rule about who may attack -- the book
     * prints a COMBAT SKILLS banner of nineteen, and that is not what decides
     * this list. Shield joined it with the Shield Bash rows (6ib). Stated as names,
     * so a tenth weapon row is a failing test and not a silent ninth entry.
     */
    @Test
    fun `the attacks supported here, for a character who owns nothing and carries everything`() {
        val rows = SkillDefaults.attacks(offered(), Weapons.all(), emptyMap())
        assertEquals(
            listOf(
                // Pistol arms the revolvers on the owner's ruling (Weapons.ALSO_ARMS).
                "Guns (Pistol)", "Guns (Revolver)", "Guns (Rifle)", "Guns (Shotgun)", "Brawling", "Boxing",
                "Knife", "Axe/Mace", "Broadsword", "Shield",
            ),
            rows.map { it.key.specialization?.let { s -> "${it.key.name} ($s)" } ?: it.key.name },
        )
        // Guns (Pistol) has a default, and the revolvers are its weapon rows.
        assertTrue(offered().any { it.key.specialization == "Pistol" && it.key.name == "Guns" })
    }

    /**
     * **Warrior's and Weapon Master's lines, applied where a weapon is in
     * hand** (6et named them and applied none, because a list of skills has
     * no weapon). "Every weapon is treated as known at effective DX (no
     * default penalty)": DX, and not a roll at a default, so Versatile's +1
     * does not come along. The fist keeps the default -- the line is about a
     * weapon -- which is why this asks whether one is held and not whether
     * the skill has one.
     *
     * **A FLOOR, and for a skill the character bought as well** (the diff
     * review's MEDIUM). One point in Axe/Mace is DX-1, and the first version
     * lifted only an untrained skill, so a Warrior who trained with the axe
     * swung it worse than one who had never held it. "Every weapon" is the
     * book's phrase. A level already at DX or above is left alone.
     */
    @Test
    fun `a weapon in a master's hand is wielded at no less than DX, and a fist is not`() {
        val knife = offered(level = 10, bonus = 1).single { it.key.name == "Knife" }
        assertEquals(7, knife.level)
        for (mastery in SkillDefaults.WeaponMastery.entries) {
            // Untrained, Versatile and all: DX and no more.
            assertEquals(12, SkillDefaults.mastered(knife.level, weaponInHand = true, mastery = mastery, dx = 12))
            // Bought one rung below DX.
            assertEquals(12, SkillDefaults.mastered(11, weaponInHand = true, mastery = mastery, dx = 12))
            // At DX or above the line changes nothing, and says so with null.
            assertNull(SkillDefaults.mastered(12, weaponInHand = true, mastery = mastery, dx = 12))
            assertNull(SkillDefaults.mastered(15, weaponInHand = true, mastery = mastery, dx = 12))
            // The fist.
            assertNull(SkillDefaults.mastered(knife.level, weaponInHand = false, mastery = mastery, dx = 12))
            assertTrue(mastery.source.startsWith("DX, ") && "no default penalty" in mastery.source, mastery.source)
            assertTrue(TraitEffects.VERSATILE !in mastery.source, mastery.source)
        }
        assertNull(SkillDefaults.mastered(knife.level, weaponInHand = true, mastery = null, dx = 12))
    }

    /**
     * **The level a carried weapon is wielded at, for its PARRY** (6ey). Line
     * 2468 prints "Weapon Skill ÷ 2 + 3", and a master's weapon is "treated as
     * known at effective DX": DX is the Weapon Skill operand, so a master's
     * broadsword parries untrained. A non-master's still needs a skill -- the
     * armed cell prints no "or DX" -- and with two skills arming one weapon
     * the higher wields it, on every screen that asks.
     */
    @Test
    fun `a carried weapon is wielded at its best skill, floored at DX in a master's hand`() {
        val broadsword = Weapons.named("Broadsword")
        val key = SkillKey("Broadsword", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE)
        val w = SkillDefaults.WeaponMastery.WARRIOR
        assertNull(SkillDefaults.wieldedAt(broadsword, emptyMap(), mastery = null, dx = 12))
        assertEquals(9, SkillDefaults.wieldedAt(broadsword, mapOf(key to 9), mastery = null, dx = 12))
        for (mastery in SkillDefaults.WeaponMastery.entries) {
            assertEquals(12, SkillDefaults.wieldedAt(broadsword, emptyMap(), mastery, dx = 12))
            assertEquals(12, SkillDefaults.wieldedAt(broadsword, mapOf(key to 9), mastery, dx = 12))
            assertEquals(14, SkillDefaults.wieldedAt(broadsword, mapOf(key to 14), mastery, dx = 12))
        }
        val knife = Weapons.named("Combat Knife")
        val easy = SkillKey("Knife", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.EASY)
        val average = easy.copy(difficulty = Difficulty.AVERAGE)
        assertEquals(12, SkillDefaults.wieldedAt(knife, mapOf(easy to 12, average to 11), mastery = null, dx = 10))
        assertEquals(9, Weapons.parryFor(broadsword, SkillDefaults.wieldedAt(broadsword, emptyMap(), w, dx = 12)!!))
    }

    /**
     * The Pugilist's reinforced Combat Mastery (book lines 6595-6598): melee
     * weapons "known at effective DX immediately (no warm-up needed)", and
     * "Ranged weapons still need one use to reach full proficiency (as the
     * Warrior's original ability)". Its text never says "no default
     * penalty", which is why the keyword census behind WEAPON_LINES missed
     * it. It changes no number -- the Warrior already wields at DX -- only
     * where the repeated-use clause is still said to be unapplied.
     */
    @Test
    fun `a Pugilist's melee weapon needs no warm-up and a ranged one still does`() {
        val pugilist = ContentLoader.pathways().single { it.name == "Pugilist" }.reinforcedAbilities
        assertTrue("limited to melee weapons only" in pugilist, pugilist)
        assertTrue("Ranged weapons still need one use" in pugilist, pugilist)

        val p = SkillDefaults.WeaponMastery.PUGILIST
        assertEquals(p, SkillDefaults.masteryFor("Twilight Giant", 8))
        assertNull(p.unappliedFor(isRanged = false))
        assertTrue("default +2" in p.unappliedFor(isRanged = true)!!)
        assertTrue("Warrior" in p.source, p.source)
        val w = SkillDefaults.WeaponMastery.WARRIOR
        assertEquals(w.unappliedFor(isRanged = false), w.unappliedFor(isRanged = true))
        assertNull(SkillDefaults.WeaponMastery.WEAPON_MASTER.unappliedFor(isRanged = true))
    }

    /**
     * Which mastery a walked chain holds. Weapon Master's block calls itself
     * "a strict upgrade to and replacement of the Warrior's Combat Mastery",
     * so it is one or the other and never both -- and the repeated-use clause
     * is the Warrior's alone.
     */
    @Test
    fun `Weapon Master replaces Warrior, and only Warrior counts repeated use`() {
        val warrior = ContentLoader.pathways().single { it.name == "Warrior" }.ability
        val master = ContentLoader.pathways().single { it.name == "Weapon Master" }.ability
        assertTrue("treated as known at effective DX (no default penalty)" in warrior, warrior)
        assertTrue("treated as known at default +2" in warrior, warrior)
        assertTrue("treated as known at effective DX (no default penalty)" in master, master)
        assertTrue("strict upgrade to and replacement of the Warrior" in master, master)

        val w = SkillDefaults.WeaponMastery.WARRIOR
        val m = SkillDefaults.WeaponMastery.WEAPON_MASTER
        assertEquals(w, SkillDefaults.masteryFor("Twilight Giant", 9))
        assertEquals(m, SkillDefaults.masteryFor("Twilight Giant", 7))
        assertEquals(m, SkillDefaults.masteryFor("Twilight Giant", 6))
        assertNull(SkillDefaults.masteryFor("Fool", 9))
        assertNull(SkillDefaults.masteryFor("Mother", 7))
        val note = w.unappliedFor(isRanged = false)!!
        assertTrue("default +2" in note && note.startsWith("Not applied here"), note)
        // The reason is the book's, not the app's (6fj): counting uses was
        // never the obstacle, since a GM could declare them. The Warrior's
        // own example puts "default +2" above the DX it starts at, and the
        // Weapon Master, "a strict upgrade", stops at DX.
        assertTrue("count uses" !in note, note)
        assertTrue("above DX" in note && "strict upgrade" in note, note)
        assertTrue("but needs several shots before reaching full proficiency" in warrior, warrior)
        assertTrue("treated as known at effective DX (no default penalty) instantly" in master, master)
        assertNull(m.unappliedFor(isRanged = false))
        // The Harvest Priest's line is about a tool the weapon table does not mark.
        assertNull(SkillDefaults.toolNote("Twilight Giant", 7))
        assertTrue("farming or gardening tool" in SkillDefaults.toolNote("Mother", 7)!!)
    }

    /**
     * A level can DROP inside one turn: put the weapon down and a master is
     * back at the default. A Deceptive Attack chosen under the higher ceiling
     * would then throw on Roll ([Attacks.modifiers] refuses it), so the plan
     * is moved to the new level and loses what no longer fits -- visibly, to
     * zero, never trimmed to a number the player did not pick.
     */
    @Test
    fun `a plan moved to a lower level drops a Deceptive Attack it cannot afford`() {
        val plan = AttackPlan(deceptive = 3, rapidStrikes = 1)
        assertEquals(3, Attacks.maxDeceptive(12))
        assertEquals(1, Attacks.maxDeceptive(6))
        assertEquals(plan, Attacks.atLevel(plan, 12))
        assertEquals(plan.copy(deceptive = 0), Attacks.atLevel(plan, 6))
        assertEquals(1, Attacks.atLevel(plan.copy(deceptive = 1), 6).deceptive)
        // And the moved plan is one Attacks will roll.
        Attacks.modifiers(Attacks.atLevel(plan, 6), 6)
    }

    private fun keyOf(name: String, specialization: String? = null) =
        skill(name, specialization).let { SkillKey(it.name, it.specialization, it.attr, it.difficulty) }

    /**
     * **The Attack picker offers the owned skills the book files under COMBAT
     * SKILLS.** Until this slice it offered every owned skill, Research and
     * Writing included. The banner and not the damage line: ten of its rows
     * (Shortsword, Polearm, Guns (Pistol)...) print no damage this
     * dialog can show and are still attacks, and the maneuvers are theirs too.
     */
    @Test
    fun `an owned skill is offered as an attack only under the COMBAT SKILLS banner`() {
        val owned = mapOf(
            keyOf("Research") to 13,
            keyOf("Writing") to 12,
            keyOf("Acrobatics") to 11,
            keyOf("Brawling") to 12,
            keyOf("Shortsword") to 11,
            keyOf("Guns", "Pistol") to 10,
        )

        val offered = SkillDefaults.ownedAttacks(owned)

        assertEquals(
            setOf("Brawling", "Shortsword", "Guns (Pistol)"),
            offered.keys.map { it.specialization?.let { s -> "${it.name} ($s)" } ?: it.name }.toSet(),
        )
        assertEquals(12, offered.getValue(keyOf("Brawling")), "the level travels with the key")
    }

    /**
     * The banner is read by NAME alone ([TraitEffects.categoryOf]), so a name
     * printed under COMBAT SKILLS and under a second banner would be filed by
     * whichever row came first. None is today; this says so (the design
     * review's MEDIUM), and pins the nineteen.
     */
    @Test
    fun `the COMBAT SKILLS banner is nineteen rows whose names no other banner prints`() {
        val rows = ContentLoader.skills()
        val combat = rows.filter { it.category == SkillDefaults.COMBAT_SKILLS }
        assertEquals(19, combat.size)
        val names = combat.map { it.name }.toSet()
        assertTrue(rows.none { it.name in names && it.category != SkillDefaults.COMBAT_SKILLS })
    }

    /**
     * A held skill with no banner would vanish from the Attack picker with no
     * way back. Every skill a potion grants, on a standard pathway or not,
     * names a catalogue row, so none can (the design review's MEDIUM).
     */
    @Test
    fun `every skill a potion grants is filed under some banner`() {
        val names = ContentLoader.skills().map { it.name }.toSet()
        val granted = (ContentLoader.pathways() + NonStandardPathways.all())
            .flatMap { block -> block.skills.map { it.name.substringBefore(" (") } }
        assertTrue(granted.isNotEmpty())
        assertEquals(emptySet<String>(), granted.filterNot { it in names }.toSet())
    }

    // An untrained weapon row is offered in Attack only for a weapon in hand
    // (play session 1: a Private Investigator was offered a Bolt-Action Rifle
    // they never carried, at Guns (Rifle)'s default).

    private fun carried(vararg names: String) = names.map(Weapons::named)

    private fun attackRows(vararg carried: String) =
        SkillDefaults.attacks(offered(), carried(*carried), emptyMap()).map { it.key.name to it.key.specialization }

    @Test
    fun `an untrained weapon skill is not offered to attack with a weapon not carried`() {
        assertTrue("Guns" to "Rifle" !in attackRows())
    }

    @Test
    fun `an untrained weapon skill is offered with only the weapon carried`() {
        assertTrue("Guns" to "Rifle" in attackRows("Hunting Rifle"))
        val rifle = offered().single { it.key.name == "Guns" && it.key.specialization == "Rifle" }
        assertEquals(
            listOf("Hunting Rifle"),
            SkillDefaults.attackWeapons(rifle.key, rifle.level, carried("Hunting Rifle"), emptyMap()).map { it.name },
        )
    }

    @Test
    fun `an untrained skill with a fist table of its own is offered with nothing carried`() {
        assertTrue(attackRows().any { Damage.attacksFor(it.first).isNotEmpty() })
    }

    @Test
    fun `untrained Guns (Pistol) is offered with a revolver carried`() {
        assertTrue("Guns" to "Pistol" !in attackRows())
        assertTrue("Guns" to "Pistol" in attackRows("Revolver (.38 cal)"))
    }

    @Test
    fun `an owned weapon skill still arms every catalogue weapon`() {
        assertEquals(
            listOf("Bolt-Action Rifle", "Hunting Rifle"),
            SkillDefaults.attackWeapons(keyOf("Guns", "Rifle"), null, emptyList(), emptyMap()).map { it.name },
        )
    }

    @Test
    fun `an owned skill's carried weapon comes first, the one the dialog picks`() {
        // Play session 5: Military Police carried the Hunting Rifle and the
        // dialog picked the Bolt-Action, first in the catalogue.
        assertEquals(
            listOf("Hunting Rifle", "Bolt-Action Rifle"),
            SkillDefaults.attackWeapons(keyOf("Guns", "Rifle"), null, carried("Hunting Rifle"), emptyMap()).map { it.name },
        )
    }

    /**
     * Play session 2: Mira, holding Guns (Revolver) 12 and a revolver, was
     * offered untrained Guns (Pistol) at 6 for the same gun. An untrained row
     * arms only a carried weapon no owned skill already wields at least as
     * well, and drops out when that leaves it nothing.
     */
    @Test
    fun `an untrained row does not arm a weapon an owned skill wields better`() {
        val revolver = keyOf("Guns", "Revolver")
        val owned = mapOf(revolver to 12)
        val rows = SkillDefaults.attacks(offered(owned), carried("Revolver (.38 cal)"), owned)
        assertTrue(rows.none { it.key.name == "Guns" && it.key.specialization == "Pistol" }, rows.toString())
        // Wielded WORSE than the default, the untrained row still arms it.
        val weak = mapOf(revolver to 1)
        val pistol = offered(weak).single { it.key.name == "Guns" && it.key.specialization == "Pistol" }
        assertEquals(
            listOf("Revolver (.38 cal)"),
            SkillDefaults.attackWeapons(pistol.key, pistol.level, carried("Revolver (.38 cal)"), weak).map { it.name },
        )
    }
}
