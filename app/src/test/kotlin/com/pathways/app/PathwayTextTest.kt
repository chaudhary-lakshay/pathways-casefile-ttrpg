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
    fun `grouping loses and reorders nothing`() {
        for (text in texts) {
            assertEquals(abilityParagraphs(text), abilities(text).flatMap { listOf(it.head) + it.parts })
        }
    }

    /** Seen on the tablet: Success, Failure and Critical Failure drawn as three abilities. */
    @Test
    fun `the Cryptologist's roll outcomes sit under Decryption`() {
        val block = ContentLoader.pathways().single { it.name == "Cryptologist" }
        val grouped = abilities(block.ability)
        assertEquals(listOf("Decryption (IQ / 1 SPI)", "Beyonder Knowledge (Passive)"), grouped.map { it.head.heading })
        assertEquals(listOf("Success", "Failure", "Critical Failure"), grouped.first().parts.map { it.heading })
    }

    @Test
    fun `every bare Passive sits under the power it follows`() {
        val block = NonStandardPathways.all().first { it.ability.contains("Midoro's Worship") }
        val grouped = abilities(block.ability)
        assertTrue(grouped.none { it.head.heading == "Passive" }, grouped.map { it.head.heading }.toString())
        assertEquals(9, grouped.sumOf { a -> a.parts.count { it.heading == "Passive" } })
        // A longer name that starts with the word is a power of its own.
        assertTrue(texts.flatMap { abilities(it) }.any { it.head.heading == "Passive Luck" })
    }

    /** The condition the nesting rests on: a part with nothing above it would be orphaned. */
    @Test
    fun `no text opens with an outcome or an aspect`() {
        val parts = texts.flatMap { text -> abilities(text).flatMap { it.parts } }.mapNotNull { it.heading }.toSet()
        assertTrue(parts.containsAll(listOf("Success", "Failure", "Critical Failure", "Passive", "Duration")), "$parts")
        // The test's own words, not the grouping's: a text that opened with
        // one would be a head there, and so never in `parts` (the diff review).
        val label = Regex("""(Critical )?(Success|Failure).*|Passive|Duration|Range|Examples?|Note""")
        for (text in texts) {
            val first = abilityParagraphs(text).first().heading
            assertTrue(first == null || !label.matches(first), "$first opens: ${text.take(60)}")
        }
        assertEquals("Failure", abilities("Failure: nothing. Ward (1 SPI): a wall.").first().head.heading)
    }

    @Test
    fun `a heading's closing bracket is its tag`() {
        assertEquals("Decryption" to "IQ / 1 SPI", titleOf("Decryption (IQ / 1 SPI)"))
        assertEquals("Treasure Sense" to null, titleOf("Treasure Sense"))
        assertEquals("Singing — Buff Effects" to null, titleOf("Singing — Buff Effects"))
    }

    /** Seen on the tablet as two boxes: the PDF's bullet is stored as U+007F. */
    @Test
    fun `the book's bullets are lines of their own`() {
        val block = ContentLoader.pathways().single { it.name == "Cryptologist" }
        val knowledge = abilityParagraphs(block.ability).single { it.heading == "Beyonder Knowledge (Passive)" }
        val lines = bullets(knowledge.lead)
        assertEquals("The Cryptologist has deep knowledge of:", lines.first())
        assertEquals(3, lines.size)
        assertTrue(lines[1].startsWith("Mystical objects"), lines[1])
        assertTrue(lines.none { '\u007f' in it })
        assertEquals(listOf("No bullet here."), bullets("No bullet here."))
    }

    /** Seen on the tablet: the Apothecary's five potions, each named inside its bullet. */
    @Test
    fun `a bullet that opens with a name gives it up as its label`() {
        assertEquals(
            "Libido Potion (Pharmacy -1)" to "Powdered mummy and aromatic herbs.",
            named("Libido Potion (Pharmacy -1): Powdered mummy and aromatic herbs."),
        )
        assertEquals(null to "sees non-physical entities: ghosts", named("sees non-physical entities: ghosts"))
    }

    /** Seen on the tablet: a three-part honorific cut into bullets, the third running into the next name. */
    @Test
    fun `a semicolon inside quotation marks does not start a list`() {
        val block = ContentLoader.pathways().single { it.pathway == "Hanged Man" && it.sequence == 9 }
        val knowledge = abilityParagraphs(block.ability).single { it.heading == "Knowledge (Honorifics)" }
        assertTrue(knowledge.items.isEmpty(), knowledge.items.toString())
        assertTrue(knowledge.lead.contains("\"The Lord that Created Everything; The Lord who Reigns"), knowledge.lead)
    }

    @Test
    fun `a skill's note loses the dash the table cell opens with`() {
        assertEquals("decent knowledge of rituals", noteOf("— decent knowledge of rituals"))
        assertEquals("(Nature, Herbs)", noteOf("(Nature, Herbs)"))
        assertEquals("", noteOf(""))
    }

    @Test
    fun `costs, distances and modifiers are the terms picked out`() {
        val text = "costs 1 SPI per minute; within roughly 10 meters at -2 to hit, 2d+1 damage"
        val picked = keyTerms(text).map { text.substring(it) }
        assertEquals(listOf("1 SPI", "10 meters", "-2", "2d+1"), picked)
    }
}
