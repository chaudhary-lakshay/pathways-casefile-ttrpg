package com.pathways.app

import com.pathways.model.BeyonderState
import com.pathways.rules.Premades
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The player's own characters: a save entry under a new name, [SavedSession.basedOn]
 * naming the premade it was copied from. No second file: the gallery lists
 * the premades and these entries.
 */
class CharactersTest {

    private val premades = Premades.all()
    private val blank = premades.single { it.name == BLANK_SHEET }

    @Test
    fun `an entry based on a premade is a sheet under its own name`() {
        val saved = mapOf(
            "Elsie" to SavedSession(basedOn = BLANK_SHEET),
            "Beat Cop" to SavedSession(cor = 2),
        )
        val own = ownSheets(premades, saved)
        assertEquals(listOf("Elsie"), own.map { it.name })
        assertEquals(blank.copy(name = "Elsie"), own.single())
    }

    @Test
    fun `an entry based on a premade the book no longer prints is left out`() {
        assertEquals(emptyList<Any>(), ownSheets(premades, mapOf("Ghost" to SavedSession(basedOn = "Nobody"))))
    }

    @Test
    fun `an entry whose base is gone still holds its name`() {
        // Not in the gallery, still in the file: a new character must not overwrite it.
        val saved = mapOf("Ghost" to SavedSession(basedOn = "Nobody"))
        assertTrue("Ghost" in takenNames(premades + ownSheets(premades, saved), saved))
    }

    @Test
    fun `an entry under a premade's own name is never a second sheet`() {
        assertEquals(emptyList<Any>(), ownSheets(premades, mapOf("Beat Cop" to SavedSession(basedOn = BLANK_SHEET))))
    }

    @Test
    fun `a new character needs a name no one else has`() {
        val taken = setOf("Beat Cop", "Elsie")
        assertEquals("Name the character.", newCharacterRefusal("  ", taken))
        assertEquals("That name is taken.", newCharacterRefusal("beat cop ", taken))
        assertNull(newCharacterRefusal("Klein", taken))
    }

    @Test
    fun `only the printed blank sheet is a template`() {
        assertTrue(isTemplate(blank))
        assertFalse(isTemplate(blank.copy(name = "Blank Slate")))
    }

    @Test
    fun `a cleared own character keeps what it was copied from`() {
        val played = SavedSession(beyonder = BeyonderState("Fool", 9), hpLost = 4, basedOn = "Beat Cop")
        assertEquals(SavedSession(basedOn = "Beat Cop"), played.cleared())
    }

    /**
     * Mira's build was most of what her entry held, and reset sent it back
     * to the blank (play session 2): an own character's purchases are its
     * sheet. What play added goes -- potions and wounds as before, and the
     * study, the boon and any disadvantage gained in play with its trait.
     */
    @Test
    fun `a cleared own character keeps its build and loses its play`() {
        val honor = SavedInstancedTrait("Code of Honor", null, "Detective", 10)
        val build = SavedEdits(
            st = 10,
            skills = listOf(SavedSkill("Observation", null, 2)),
            traits = listOf("Alertness", "Bad Temper"),
            instanced = listOf(honor),
            weapons = listOf("Revolver (.38 cal)"),
        )
        val played = SavedSession(
            beyonder = BeyonderState("Justiciar", 9),
            hpLost = 7,
            edits = build.copy(
                gained = listOf("Bad Temper"),
                studied = listOf(SavedSkill("Observation", null, 2)),
                boon = com.pathways.model.BoonState("Mother", 9),
            ),
            basedOn = BLANK_SHEET,
        )
        assertEquals(
            SavedSession(edits = build.copy(traits = listOf("Alertness")), basedOn = BLANK_SHEET),
            played.cleared(),
        )
    }

    @Test
    fun `a change in play keeps the character's base`() {
        val (session, last) = session(SavedSession(basedOn = "Beat Cop"))
        session.carryWeapon("Broadsword")
        assertEquals("Beat Cop", last().basedOn)
    }

    @Test
    fun `the base survives a save and a load`(@TempDir dir: File) {
        val store = SessionStore(File(dir, "s.json"))
        store.save(mapOf("Elsie" to SavedSession(basedOn = BLANK_SHEET)))
        assertEquals(BLANK_SHEET, store.load().getValue("Elsie").basedOn)
    }
}
