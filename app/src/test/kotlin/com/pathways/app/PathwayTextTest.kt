package com.pathways.app

import com.pathways.rules.NonStandardPathways
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The ability text is the book's, so the screen may lay it out and never
 * reword it: every split must put the stored string back together (6gk).
 */
class PathwayTextTest {

    private val texts = (ContentLoader.pathways() + NonStandardPathways.all())
        .flatMap { listOf(it.ability, it.reinforcedAbilities) }
        .filter { it.isNotEmpty() }

    @Test
    fun `every ability text is laid out without a character lost or changed`() {
        assertTrue(texts.size > 100, "${texts.size}")
        for (text in texts) {
            assertEquals(text, rejoined(abilityParagraphs(text)))
        }
    }

    @Test
    fun `the Seer's text is two abilities, and the active one lists what it sees`() {
        val seer = ContentLoader.pathways().single { it.pathway == "Fool" && it.sequence == 9 }
        val paragraphs = abilityParagraphs(seer.ability)
        assertEquals(listOf("Spirit Vision", "Danger Intuition"), paragraphs.map { it.heading })
        val vision = paragraphs.first()
        // "While active" opens the list, and is not a heading: its second
        // word is lowercase, so it names nothing.
        assertEquals(4, vision.items.size)
        assertTrue(vision.items.first().startsWith("While active: sees non-physical entities"), vision.items.first())
        assertTrue(vision.lead.endsWith("(costs 1 SPI per minute)."), vision.lead)
        // Two clauses are a sentence, not a list.
        assertTrue(paragraphs[1].items.isEmpty())
    }

    /** Seen on the tablet: Paper Weaponry's damage table sits in one parenthesis. */
    @Test
    fun `a semicolon inside brackets does not start a list`() {
        val magician = ContentLoader.pathways().single { it.pathway == "Fool" && it.sequence == 7 }
        val paper = abilityParagraphs(magician.ability).single { it.heading == "Paper Weaponry (1 SPI)" }
        assertTrue(paper.items.isEmpty(), paper.items.toString())
        assertTrue(paper.lead.contains("(knife: thr-1 cut / sw-2 imp; short sword:"), paper.lead)
    }

    /** The diff review: a sentence end inside brackets is not where the list starts. */
    @Test
    fun `the lead-in ends at a sentence end outside brackets`() {
        val text = "Ward: Lasts a scene (see Ch. 5). Blocks spirits (Seq. 9 and up); slows ghosts; dims light."
        val ward = abilityParagraphs(text).single()
        assertEquals("Lasts a scene (see Ch. 5).", ward.lead)
        assertEquals(listOf("Blocks spirits (Seq. 9 and up)", "slows ghosts", "dims light."), ward.items)
        assertEquals(text, rejoined(listOf(ward)))
    }

    @Test
    fun `a grouped name is one heading, and a page reference is none`() {
        val sun = ContentLoader.pathways().single { it.pathway == "Sun" && it.sequence == 9 }
        assertEquals("Singing — Buff Effects", abilityParagraphs(sun.ability).first().heading)
        val justiciar = ContentLoader.pathways().single { it.pathway == "Justiciar" && it.sequence == 7 }
        assertTrue(abilityParagraphs(justiciar.ability).none { it.heading == "B420" })
    }

    @Test
    fun `text that opens with no name keeps its first paragraph unnamed`() {
        val tower = ContentLoader.pathways().single { it.pathway == "White Tower" && it.sequence == 8 }
        assertNull(abilityParagraphs(tower.ability).first().heading)
    }

    @Test
    fun `costs, distances and modifiers are the terms picked out`() {
        val text = "costs 1 SPI per minute; within roughly 10 meters at -2 to hit, 2d+1 damage"
        val picked = keyTerms(text).map { text.substring(it) }
        assertEquals(listOf("1 SPI", "10 meters", "-2", "2d+1"), picked)
    }
}
