package com.pathways.rules

import com.pathways.model.SkillKey
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EquipmentTest {

    /** The catalogue's own row for a skill, so a test never invents a key. */
    private fun keyFor(name: String, specialization: String? = null): SkillKey {
        val row = ContentLoader.skills().first {
            it.name == name && it.specialization == specialization
        }
        return SkillKey(row.name, row.specialization, row.attr, row.difficulty)
    }

    @Test
    @DisplayName("the whole table loads, in the book's printed order")
    fun `the whole table loads`() {
        val all = Equipment.all()
        assertEquals(77, all.size)
        assertEquals("Lantern (oil)", all.first().name)
        assertEquals("Heavy tankard (pewter)", all.last().name)
        assertTrue(all.all { it.notes.isNotBlank() })
    }

    @Test
    @DisplayName("a signed clause joins a skill and a bare mention does not")
    fun `a mention is not a clause`() {
        val lockpicking = keyFor("Lockpicking")
        val joined = Equipment.forSkill(lockpicking).map { it.name }
        assertEquals(listOf("Lockpick set (basic)"), joined)

        // The row directly above it names the same skill and grants nothing.
        val picks = Equipment.all().first { it.name == "Lock picks (set)" }
        assertTrue(picks.notes.contains("Lockpicking"))
        assertTrue(picks.skills.isEmpty())
    }

    @Test
    @DisplayName("a specialization is part of the identity, not a decoration")
    fun `the bare row and the specialized row are different skills`() {
        // "Navigation (Land) +1 when exploring unfamiliar terrain".
        assertEquals(
            listOf("Compass (brass)"),
            Equipment.forSkill(keyFor("Navigation", "Land")).map { it.name },
        )
        // A character rolling the generic row is rolling something else.
        assertTrue(Equipment.forSkill(keyFor("Navigation")).isEmpty())
        assertTrue(Equipment.forSkill(keyFor("Navigation", "Sea")).isEmpty())
    }

    @Test
    @DisplayName("a bare clause reaches an instance of a skill the book prints once")
    fun `a template skill's instance still sees its gear`() {
        // "Survival" is one printed row, specializable, "specify terrain".
        // Hunter-Tracker's sheet buys Survival (Woodlands), which is an
        // INSTANCE of that row rather than a rival to it, so the bedroll's
        // bare "+1 to Survival when rested" reaches it. Strict identity
        // matching hid the bedroll from the one character who would use it.
        val template = ContentLoader.skills()
            .first { it.name == "Survival" && it.specialization == null }
        assertTrue(template.specializable)

        val woodlands = SkillKey("Survival", "Woodlands", template.attr, template.difficulty)
        assertTrue(Equipment.forSkill(woodlands).any { it.name == "Bedroll" })
        // and the bare row still sees it
        assertTrue(Equipment.forSkill(keyFor("Survival")).any { it.name == "Bedroll" })
    }

    @Test
    @DisplayName("the fixture that owns the instance reaches its gear through its own key")
    fun `hunter-tracker's survival roll surfaces the bedroll`() {
        // Not a hand-built key: this is the SkillKey the sheet screen passes
        // to Equipment.referenceFor, straight off the published sheet. The
        // bug this pins was invisible to a test that constructed the key
        // itself, because the specialization is what the fixture supplies.
        val tracker = Premades.all().first { it.name == "Hunter-Tracker" }
        val survival = tracker.skills.first { it.name == "Survival" }
        assertEquals("Woodlands", survival.specialization)

        val shown = Equipment.referenceFor(survival.key())
        assertTrue(
            shown.any { it.startsWith("Bedroll") },
            "the one sheet that buys Survival saw no gear: $shown",
        )
    }

    @Test
    @DisplayName("a clause naming one instance reaches that instance and no sibling")
    fun `an instance-scoped clause is scoped to the instance`() {
        // The mirror of the bedroll: there the CLAUSE was bare and the
        // character held an instance; here the clause names the instance
        // itself ("+1 to Savoir-Faire (High Society)") and four sheets hold
        // four different classes. Until the extractor learned that an open
        // row is named by its instance, the clause resolved to nothing at
        // all and none of the four saw the row -- the same predicate as
        // `isTemplate`, answered on the writing side of the join.
        val fine = "Fine clothes (quality)"
        val teller = Premades.all().first { it.name == "Fortune Teller" }
        val theirs = teller.skills.first { it.name == "Savoir-Faire" }
        assertEquals("High Society", theirs.specialization)
        assertTrue(
            Equipment.referenceFor(theirs.key()).any { it.startsWith(fine) },
            "the one sheet with High Society saw no fine clothes",
        )

        // And the scoping is the book's: three other sheets buy the same
        // skill in another class and the bonus does not reach them.
        val others = Premades.all()
            .mapNotNull { p -> p.skills.firstOrNull { it.name == "Savoir-Faire" } }
            .filter { it.specialization != "High Society" }
        assertTrue(others.size >= 3, "expected other Savoir-Faire classes, got $others")
        others.forEach {
            assertTrue(
                Equipment.forSkill(it.key()).none { item -> item.name == fine },
                "${it.specialization} was offered a High Society bonus",
            )
        }
    }

    @Test
    @DisplayName("an enumerated specialization is still a different skill")
    fun `a non-template specialization keeps strict matching`() {
        // Navigation is the opposite case: specializable = false, and the
        // book prints Sea and Land as their own rows. The compass names Land,
        // so the bare row and the Sea row must not see it -- otherwise the
        // fix above would erase the distinction this table draws itself.
        val bare = ContentLoader.skills()
            .first { it.name == "Navigation" && it.specialization == null }
        assertFalse(bare.specializable)
        assertTrue(Equipment.forSkill(keyFor("Navigation", "Land")).isNotEmpty())
        assertTrue(Equipment.forSkill(keyFor("Navigation")).isEmpty())
        assertTrue(Equipment.forSkill(keyFor("Navigation", "Sea")).isEmpty())
    }

    @Test
    @DisplayName("one bonus may reach two skills, and both of them see the row")
    fun `a conjunction reaches both halves`() {
        val chest = "Portable medicine chest"
        assertTrue(Equipment.forSkill(keyFor("First Aid")).any { it.name == chest })
        assertTrue(Equipment.forSkill(keyFor("Surgery")).any { it.name == chest })
    }

    @Test
    @DisplayName("the reference line is the book's sentence, with no number pulled out")
    fun `the reference line is verbatim`() {
        val lines = Equipment.referenceFor(keyFor("Search"))
        assertEquals(
            listOf(
                "Magnifying glass (3s) — +1 to Search for fine details",
                "Spectacles (reading) (5s) — Corrects vision; -3 to Search without " +
                    "them if shortsighted",
            ),
            lines,
        )
        // Both rows are conditional, and the condition survives intact. An
        // extractor that kept only the number would have shipped a permanent
        // bonus the book scopes to a circumstance.
        assertTrue(lines.all { it.contains("fine details") || it.contains("shortsighted") })
    }

    @Test
    @DisplayName("a skill the table never names gets nothing rather than a near match")
    fun `an unnamed skill gets nothing`() {
        assertTrue(Equipment.forSkill(keyFor("Brawling")).isEmpty())
        assertTrue(Equipment.referenceFor(keyFor("Brawling")).isEmpty())
    }

    @Test
    @DisplayName("no row resolves a roll the book prices without printing as a skill")
    fun `an unprinted roll is not guessed into an existing skill`() {
        // "Dark clothing: -1 to spot at night" and "Trap wire: -2 to spot if
        // camouflaged". "spot" is not a skill this book prints, and guessing
        // it into Observation or Search would be authoring content -- and
        // would land a penalty aimed at the observer on the wearer's own roll.
        val dark = Equipment.all().first { it.name == "Dark clothing (set)" }
        assertTrue(dark.notes.contains("-1 to spot at night"))
        assertTrue(dark.skills.isEmpty())
        for (skill in listOf("Observation", "Search")) {
            assertFalse(Equipment.forSkill(keyFor(skill)).any { it.name == dark.name })
        }
    }

    @Test
    @DisplayName("the two skills chapter 8 names and chapter 3 never prints stay unjoined")
    fun `a chapter versus chapter disagreement is recorded not repaired`() {
        val printed = ContentLoader.skills().map { it.name }.toSet()
        for (absent in listOf("Forced Entry", "Gather Information")) {
            assertFalse(printed.contains(absent), "chapter 3 now prints $absent")
        }
        val boots = Equipment.all().first { it.name == "Heavy boots (leather)" }
        assertTrue(boots.notes.contains("Forced Entry"))
        assertTrue(boots.skills.isEmpty())
        val badge = Equipment.all().first { it.name == "Press badge" }
        assertTrue(badge.notes.contains("Gather Information"))
        assertTrue(badge.skills.isEmpty())
    }

    @Test
    @DisplayName("the book's own Fortune spelling seam is not normalised away")
    fun `the hyphenated spelling joins nothing`() {
        // Chapter 8 writes "Fortune-Telling"; chapter 3's list prints
        // "Fortune Telling". Matching across the hyphen would decide what the
        // book meant on resemblance alone.
        assertTrue(ContentLoader.skills().any { it.name == "Fortune Telling" })
        assertTrue(Equipment.forSkill(keyFor("Fortune Telling")).isEmpty())
        for (deck in listOf("Tarot deck (common)", "Tarot deck (quality)")) {
            val row = Equipment.all().first { it.name == deck }
            assertTrue(row.notes.contains("Fortune-Telling"))
            assertTrue(row.skills.isEmpty())
        }
        // The crystal ball prints "or Psychology", and that half IS printed.
        assertTrue(
            Equipment.forSkill(keyFor("Psychology")).any { it.name == "Crystal ball (glass)" }
        )
    }

    @Test
    @DisplayName("the rest of chapter 8 is refused in source, with a reason per table")
    fun `the unmodelled tables carry their reasons`() {
        assertEquals(
            setOf("Armour", "Currency", "Legal Licenses & Permits", "Hired Allies & Services"),
            Equipment.UNMODELLED.keys,
        )
        assertTrue(Equipment.UNMODELLED.values.all { it.isNotBlank() })
        // The refusal names the mechanism, not just the absence: each reason
        // has to say what would consume the table if it were read.
        assertTrue(Equipment.UNMODELLED.getValue("Armour").contains("DR"))
        assertTrue(Equipment.UNMODELLED.getValue("Currency").contains("STRING"))
    }

    @Test
    @DisplayName("no equipment row can be stacked into a modifier, because none is one")
    fun `nothing exposes a number to add`() {
        // The two disguise kits and the two cameras name the same skill with
        // different values. They are reference lines, so "both selected" is
        // not a state this app can reach.
        val disguise = Equipment.referenceFor(keyFor("Disguise"))
        assertEquals(2, disguise.size)
        assertTrue(disguise.any { it.contains("+1 to Disguise") })
        assertTrue(disguise.any { it.contains("+2 to Disguise") })
        val photography = Equipment.referenceFor(keyFor("Photography"))
        assertTrue(photography.any { it.contains("-2 to Photography") })
        assertTrue(photography.any { it.contains("Photography +1") })
    }
}
