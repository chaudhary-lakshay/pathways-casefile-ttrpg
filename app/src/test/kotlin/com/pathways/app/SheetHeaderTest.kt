package com.pathways.app

import com.pathways.model.BeyonderState
import com.pathways.model.ControllingAttribute
import com.pathways.model.Attr
import com.pathways.model.Difficulty
import com.pathways.model.SkillKey
import com.pathways.rules.Premades
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SheetHeaderTest {

    private val fool9 = BeyonderState("Fool", 9, digestion = 40)

    @Test
    fun `a mortal with no boon has no facts to print`() {
        assertEquals(emptyList<HeaderFact>(), headerFacts(null, boon = null, cor = 0, lost = false, corrupted = false))
    }

    @Test
    fun `a Beyonder's pathway is the stamp, digestion inked, CoR muted`() {
        assertEquals(
            listOf(
                HeaderFact("Fool · Seq 9", PillInk.STAMP),
                HeaderFact("Digested 40%", PillInk.INK),
                HeaderFact("CoR 0", PillInk.MUTE),
            ),
            headerFacts(fool9, boon = null, cor = 0, lost = false, corrupted = false),
        )
    }

    @Test
    fun `a mortal holding a boon prints the boon and CoR`() {
        assertEquals(
            listOf(HeaderFact("Boon · X · Seq 9", PillInk.INK), HeaderFact("CoR 2", PillInk.MUTE)),
            headerFacts(null, boon = "Boon · X · Seq 9", cor = 2, lost = false, corrupted = false),
        )
    }

    @Test
    fun `lost comes first and turns every fact, so a narrow screen cannot wrap it out of sight`() {
        val facts = headerFacts(fool9, boon = null, cor = 9, lost = true, corrupted = false)
        assertEquals("Lost to the Rampager", facts.first().text)
        assertEquals(listOf(PillInk.LOST), facts.map { it.ink }.distinct())
    }

    @Test
    fun `corruption reads as its own loss`() {
        val facts = headerFacts(fool9, boon = null, cor = 9, lost = false, corrupted = true)
        assertEquals(HeaderFact("Lost to corruption", PillInk.LOST), facts.first())
    }

    private val sheets = Premades.all()
    private val blank = sheets.single { isTemplate(it) }

    @Test
    fun `a file is numbered by its place among the people, from one`() {
        val people = sheets.filterNot(::isTemplate)
        assertEquals(1, fileNumber(people.first(), sheets))
        assertEquals(people.size, fileNumber(people.last(), sheets))
    }

    @Test
    fun `the blank sheet is no one's file`() {
        assertEquals(null, fileNumber(blank, sheets))
    }

    @Test
    fun `an own character is numbered after the premades`() {
        val own = sheets.first().copy(name = "Mira")
        assertEquals(sheets.count { !isTemplate(it) } + 1, fileNumber(own, sheets + own))
    }

    @Test
    fun `the file line pads the number and names the walk`() {
        assertEquals("File Nº 06 · Beyonder", fileLine(6, isBeyonder = true))
        assertEquals("File Nº 23 · Mortal", fileLine(23, isBeyonder = false))
        assertEquals("Mortal", fileLine(null, isBeyonder = false))
    }

    @Test
    fun `a dialog's form header names the open file, and the blank sheet none`() {
        assertEquals("File Nº 04", fileLabel(4))
        assertEquals(null, fileLabel(null))
    }

    @Test
    fun `a skill's ATTR column is its controlling attribute and difficulty, as the book writes them`() {
        assertEquals("IQ/H", attrCode(SkillKey("Physician", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.HARD)))
        assertEquals("Per/A", attrCode(SkillKey("Observation", null, ControllingAttribute.Per, Difficulty.AVERAGE)))
    }
}
