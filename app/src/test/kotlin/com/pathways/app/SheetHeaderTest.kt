package com.pathways.app

import com.pathways.model.BeyonderState
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
}
