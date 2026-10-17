package com.pathways.app

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.BeyonderState
import com.pathways.rules.Premades
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.GlossaryEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Case File slice 4: the gallery, New character and the glossary (frames 01, 05, 07). */
class GalleryFileTest {

    private val premades = Premades.all()
    private val blank = premades.single(::isTemplate)

    // --- the gallery row (frame 01) ---

    @Test
    fun `a row's note is its two traits and the skill count`() {
        val row = PlayedRow(Attributes(10, 10, 10, 10), skills = 13, chips = listOf("Single-Minded", "Eidetic Memory"))
        assertEquals("Single-Minded, Eidetic Memory · 13 sk.", galleryNote(row, spoken = false))
        assertEquals("Single-Minded, Eidetic Memory · 13 skills", galleryNote(row, spoken = true))
    }

    @Test
    fun `a row with no short trait still prints its count`() {
        assertEquals("4 sk.", galleryNote(PlayedRow(Attributes(10, 10, 10, 10), 4, emptyList()), spoken = false))
    }

    @Test
    fun `the stamp names the walk, or says mortal`() {
        assertEquals("Fool 9", walkStamp(BeyonderState("Fool", 9, 0)))
        assertEquals("Mortal", walkStamp(null))
    }

    @Test
    fun `a save is lost to the Rampager or to corruption, and the sheet reads the same`() {
        assertFalse(SavedSession().isLost)
        assertTrue(SavedSession(lost = true).isLost)
        assertTrue(SavedSession(corrupted = true).isLost)
        assertTrue(BeyonderSession(SavedSession(corrupted = true), premades.first()) {}.isLost)
    }

    @Test
    fun `an attribute at or above 10 is inked heavy`() {
        assertFalse(isStrong(9))
        assertTrue(isStrong(10))
        assertTrue(isStrong(14))
    }

    @Test
    fun `a filtered tab keeps each person's file number`() {
        val saved = mapOf("Journalist" to SavedSession(beyonder = BeyonderState("Error", 9, 0)))
        val people = premades.filterNot(::isTemplate)
        val walked = rosterOf(Roster.BEYONDER, people, saved).single()
        assertEquals(people.indexOfFirst { it.name == "Journalist" } + 1, fileNumber(walked, premades))
    }

    @Test
    fun `New character prints the number the created character then takes`() {
        val saved = LinkedHashMap<String, SavedSession>()
        saved["Elsie"] = SavedSession(basedOn = "Beat Cop")
        val printed = nextFileNumber(premades + ownSheets(premades, saved))
        saved["Mara"] = SavedSession(basedOn = BLANK_SHEET)
        val sheets = premades + ownSheets(premades, saved)
        assertEquals(printed, fileNumber(sheets.single { it.name == "Mara" }, sheets))
        assertEquals(premades.count { !isTemplate(it) } + 2, printed)
    }

    // --- New character (frame 05) ---

    @Test
    fun `the blank sheet's hint reads its own attributes`() {
        assertEquals(Attr.entries.associateWith { 9 }, blank.attributes)
        assertEquals("Blank sheet starts every attribute at 9.", blankHint(blank))
        assertEquals(
            "Blank sheet starts at ST 9, DX 10, IQ 11, HT 12.",
            blankHint(blank.copy(attributes = mapOf(Attr.ST to 9, Attr.DX to 10, Attr.IQ to 11, Attr.HT to 12))),
        )
    }

    @Test
    fun `the template list holds every premade but the blank sheet, filtered by name`() {
        assertEquals(premades.filterNot(::isTemplate), templateChoices(premades, ""))
        assertEquals(listOf("Beat Cop"), templateChoices(premades, "  beat ").map { it.name })
        assertEquals(emptyList<Any>(), templateChoices(premades, "blank"))
    }

    @Test
    fun `Create waits for a name, then for a template when Template is picked`() {
        assertEquals("Name the character.", createRefusal("", emptySet(), blank))
        assertEquals("Pick a template.", createRefusal("Mara", emptySet(), null))
        assertEquals("That name is taken.", createRefusal("beat cop", setOf("Beat Cop"), null))
        assertNull(createRefusal("Mara", emptySet(), blank))
    }

    // --- the glossary (frame 07) ---

    private val glossary = ContentLoader.glossary()

    @Test
    fun `the groups keep the book's order exactly`() {
        assertEquals(glossary, glossaryGroups(glossary, "").flatMap { it.entries })
    }

    @Test
    fun `the jump letters are the runs of initials, the dice under 3`() {
        assertEquals(
            listOf("A", "B", "C", "D", "E", "F", "G", "H", "L", "M", "P", "S", "3", "W"),
            glossaryGroups(glossary, "").map { it.letter },
        )
    }

    @Test
    fun `a letter met again later is a second group, not a reorder`() {
        val entries = listOf(GlossaryEntry("Beta", ""), GlossaryEntry("Alpha", ""), GlossaryEntry("Bravo", ""))
        assertEquals(listOf("B", "A", "B"), glossaryGroups(entries, "").map { it.letter })
    }

    @Test
    fun `search matches the term, case-insensitive, and may match nothing`() {
        assertEquals(listOf("Digestion"), glossaryGroups(glossary, " DIGEST").flatMap { it.entries }.map { it.term })
        assertEquals(emptyList<Any>(), glossaryGroups(glossary, "zzz"))
    }
}
