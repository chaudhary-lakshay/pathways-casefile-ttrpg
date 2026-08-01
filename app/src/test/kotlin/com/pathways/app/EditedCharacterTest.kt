package com.pathways.app

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.Character
import com.pathways.rules.Derive
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
    }
}
