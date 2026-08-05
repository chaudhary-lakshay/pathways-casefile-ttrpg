package com.pathways.app

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.Character
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.Appearance
import com.pathways.rules.Derive
import com.pathways.rules.Wealth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A premade plus what the player has bought since.
 *
 * The invariant under all of it: the published sheet is never edited. An edit
 * is a delta, so the `computed vs asPublished` diff keeps comparing the engine
 * against the book rather than against the player's purchases.
 */
class EditedCharacterTest {

    private val mortal = Character(name = "test", attributes = Attributes(iq = 10))

    @Test
    fun `no edits leaves the character exactly as printed`() {
        assertEquals(mortal, SavedEdits().applyTo(mortal))
    }

    @Test
    fun `an unedited attribute keeps its published value`() {
        val edited = SavedEdits(iq = 13).applyTo(mortal)
        assertEquals(13, edited.attributes.iq)
        assertEquals(mortal.attributes.st, edited.attributes.st, "ST was never touched")
    }

    @Test
    fun `a bought skill resolves its attribute and difficulty from the book`() {
        // The save file stores a name and points only. Storing the controlling
        // attribute too would let a save contradict the book after a content
        // fix -- the book is the authority (CLAUDE.md 4.1).
        val edited = SavedEdits(skills = listOf(SavedSkill("Ritualistic Magic", points = 8)))
            .applyTo(mortal)
        val bought = edited.skills.single()
        assertEquals("Ritualistic Magic", bought.key.name)
        assertEquals(8, bought.points)
        val definition = definitionOf("Ritualistic Magic")
        assertNotNull(definition)
        assertEquals(definition!!.attr, bought.key.attr)
        assertEquals(definition.difficulty, bought.key.difficulty)
    }

    @Test
    fun `a bought skill reaches a level the ritual rules can use`() {
        val edited = SavedEdits(iq = 14, skills = listOf(SavedSkill("Ritualistic Magic", points = 8)))
            .applyTo(mortal)
        val level = skillLevelOf("Ritualistic Magic", Derive.of(edited).skillLevels)
        assertNotNull(level, "no premade prints this skill; buying it is the only way to cast")
        assertTrue(level!! > 0)
    }

    @Test
    fun `a bought skill is billed to the point budget`() {
        val before = Derive.of(mortal).budget.spent
        val bought = SavedEdits(skills = listOf(SavedSkill("Ritualistic Magic", points = 8)))
            .applyTo(mortal)
        assertEquals(before + 8, Derive.of(bought).budget.spent, "points bought in play are still points")
    }

    @Test
    fun `a skill the book does not print is dropped, never guessed at`() {
        // Inventing a controlling attribute and difficulty for it would be
        // making up a rule, and billing for a skill the sheet cannot show
        // would charge for nothing.
        val edited = SavedEdits(skills = listOf(SavedSkill("Sword Of Infinite Nonsense", points = 4)))
            .applyTo(mortal)
        assertTrue(edited.skills.isEmpty())
        assertNull(definitionOf("Sword Of Infinite Nonsense"))
    }

    @Test
    fun `buying a skill the sheet already prints does not duplicate it`() {
        // Character rejects two purchases of one key, and those points belong
        // on the existing entry rather than a second row.
        val printed = definitionOf("Occultism")!!
        val withPrinted = SavedEdits(skills = listOf(SavedSkill(printed.name, points = 2))).applyTo(mortal)
        val edited = SavedEdits(skills = listOf(SavedSkill(printed.name, points = 4))).applyTo(withPrinted)
        assertEquals(1, edited.skills.count { it.key.name == printed.name })
        assertEquals(2, edited.skills.single { it.key.name == printed.name }.points)
    }

    @Test
    fun `a specialization picks its own row, not the first one printed`() {
        // REGRESSION (Codex): five names carry several rows -- Guns has four,
        // Navigation three -- so resolving by name alone bought "Guns
        // (Pistol)" whichever variant was tapped.
        val rifle = definitionOf("Guns", "Rifle")
        val pistol = definitionOf("Guns", "Pistol")
        assertNotNull(rifle)
        assertEquals("Rifle", rifle!!.specialization)
        assertEquals("Pistol", pistol!!.specialization)

        val bought = SavedEdits(skills = listOf(SavedSkill("Guns", "Rifle", points = 2)))
            .applyTo(mortal).skills.single()
        assertEquals("Rifle", bought.key.specialization)
    }

    @Test
    fun `two specializations of one skill are two purchases`() {
        val edited = SavedEdits(
            skills = listOf(
                SavedSkill("Guns", "Rifle", points = 1),
                SavedSkill("Guns", "Shotgun", points = 1),
            ),
        ).applyTo(mortal)
        assertEquals(
            setOf("Rifle", "Shotgun"),
            edited.skills.map { it.key.specialization }.toSet(),
        )
    }

    @Test
    fun `an attribute edit is stored per attribute, not as a whole block`() {
        val edits = SavedEdits().withAttribute(Attr.DX, 12)
        assertEquals(12, edits.levelOf(Attr.DX))
        assertNull(edits.levelOf(Attr.ST), "an untouched attribute stays as printed")
    }

    @Test
    fun `an empty edit set is recognised as empty`() {
        assertTrue(SavedEdits().isEmpty)
        assertTrue(!SavedEdits(ht = 11).isEmpty)
        assertTrue(!SavedEdits(skills = listOf(SavedSkill("Brawling", points = 1))).isEmpty)
        assertTrue(!SavedEdits(wealth = "Wealthy").isEmpty)
    }

    @Test
    fun `a chosen wealth level replaces the one the sheet printed`() {
        // A character has one economic status. Left beside each other, the
        // printed level and the bought one would raise the budget and spend
        // from it at the same time, and both totals would look plausible.
        val poor = mortal.copy(traits = listOf(Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE)))
        val edited = SavedEdits(wealth = "Wealthy").applyTo(poor)

        assertEquals(listOf("Wealth: Wealthy"), edited.traits.map { it.name })
        assertEquals(
            Derive.of(poor).budget.surplus - 30,
            Derive.of(edited).budget.surplus,
            "15 points of disadvantage lost and 15 spent is a 30-point move",
        )
    }

    @Test
    fun `a sheet's other traits survive a wealth change`() {
        val printed = mortal.copy(
            traits = listOf(
                Trait("Skinny", 5, TraitKind.DISADVANTAGE),
                Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE),
            ),
        )
        assertEquals(
            listOf("Skinny", "Wealth: Comfortable"),
            SavedEdits(wealth = "Comfortable").applyTo(printed).traits.map { it.name },
        )
    }

    @Test
    fun `Average leaves no wealth trait behind`() {
        val poor = mortal.copy(traits = listOf(Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE)))
        assertTrue(SavedEdits(wealth = "Average").applyTo(poor).traits.isEmpty())
    }

    @Test
    fun `choosing Average is still a choice, and still shows as one`() {
        // REGRESSION (Codex): Average costs 0 pts, so Wealth.applied leaves no
        // trait behind. A display derived from the composed character read
        // "the sheet prints no wealth level" for a player who had deliberately
        // picked Average -- while the budget had moved by 15 points.
        val poor = mortal.copy(traits = listOf(Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE)))
        val edits = SavedEdits(wealth = "Average")

        assertTrue(edits.applyTo(poor).traits.isEmpty(), "and it really does leave no trait")
        assertEquals("Average", edits.wealthShownFor(edits.applyTo(poor))?.status)
    }

    @Test
    fun `an unchosen level falls back to the one the sheet printed`() {
        val poor = mortal.copy(traits = listOf(Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE)))
        assertEquals("Poor", SavedEdits().wealthShownFor(poor)?.status)
        // And a level the book does not print falls through the same way, so
        // the row agrees with what applyTo actually billed.
        assertEquals("Poor", SavedEdits(wealth = "Fabulously Rich").wealthShownFor(poor)?.status)
        assertNull(SavedEdits().wealthShownFor(mortal), "a sheet with no wealth row still has none")
    }

    @Test
    fun `a saved wealth level the book does not print is dropped, never guessed at`() {
        // Same rule as a saved skill the book no longer prints: a save file is
        // convenience, never authority. Answering "Average" for an unreadable
        // level would silently move the budget by up to 25 points.
        val poor = mortal.copy(traits = listOf(Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE)))
        assertEquals(poor.traits, SavedEdits(wealth = "Fabulously Rich").applyTo(poor).traits)
    }

    @Test
    fun `a chosen appearance level is billed like any other trait`() {
        val edited = SavedEdits(appearance = "Ugly").applyTo(mortal)
        assertEquals(listOf("Ugly"), edited.traits.map { it.name })
        assertEquals(
            Derive.of(mortal).budget.surplus + 8,
            Derive.of(edited).budget.surplus,
            "Ugly refunds 8 points",
        )
    }

    @Test
    fun `wealth and appearance are two choices, not one`() {
        // They resolve by different rules -- wealth by its "Wealth: " prefix,
        // appearance by the bare level name -- so one must not disturb the
        // other. Chapter 3's trait lists print none of the appearance levels,
        // which is what makes the bare name safe to match on.
        val printed = mortal.copy(traits = listOf(Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE)))
        val both = SavedEdits(wealth = "Comfortable", appearance = "Attractive").applyTo(printed)

        assertEquals(listOf("Wealth: Comfortable", "Attractive"), both.traits.map { it.name })
        assertEquals("Comfortable", Wealth.levelOf(both.traits)?.status)
        assertEquals("Attractive", Appearance.levelOf(both.traits)?.level)
    }

    @Test
    fun `clearing the choice returns the sheet's own looks`() {
        // No premade prints an appearance level, so on all 23 this is the way
        // back to ordinary. A tested setter that no screen can reach is not a
        // way back -- the picker's first row calls this.
        val ugly = SavedEdits(appearance = "Ugly").applyTo(mortal)
        assertEquals(listOf("Ugly"), ugly.traits.map { it.name })
        assertTrue(SavedEdits(appearance = null).applyTo(mortal).traits.isEmpty())
    }

    @Test
    fun `a saved appearance level the book does not print is dropped`() {
        // Same rule as wealth and as a bought skill. There is no nearest level
        // to fall back on here: the table prints no ordinary row at all.
        assertTrue(SavedEdits(appearance = "Radiant").applyTo(mortal).traits.isEmpty())
        assertNull(SavedEdits(appearance = "Radiant").appearanceLevel())
        assertTrue(!SavedEdits(appearance = "Ugly").isEmpty)
    }
}
