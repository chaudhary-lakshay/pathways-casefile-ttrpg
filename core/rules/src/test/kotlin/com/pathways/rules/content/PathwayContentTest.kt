package com.pathways.rules.content

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Guards over the T1 content (Ch. 9-12) that tools/extract_pathways.py
 * commits. The Python suite guards the extraction; this guards what the app
 * actually loads, and pins the place where the book disagrees with itself so
 * neither side can be silently repaired (CLAUDE.md 4.1).
 */
class PathwayContentTest {

    private val blocks = ContentLoader.pathways()

    @Test
    fun `every pathway has one block per sequence`() {
        assertEquals(88, blocks.size)
        val byPathway = blocks.groupBy { it.pathway }
        assertEquals(22, byPathway.size)
        byPathway.forEach { (pathway, entries) ->
            assertEquals(listOf(6, 7, 8, 9), entries.map { it.sequence }.sorted(), pathway)
        }
    }

    @Test
    fun `the Seer block loads exactly as the book prints it`() {
        val seer = blocks.single { it.sequence == 9 && it.pathway == "Fool" }

        assertEquals("Seer", seer.name)
        assertEquals(listOf(StatGain("SPI", 9.0)), seer.stats)
        assertEquals(
            listOf("Ritualistic Magic", "Divination Arts", "Occultism"),
            seer.skills.map { it.name },
        )
        val divination = seer.skills.single { it.name == "Divination Arts" }
        assertEquals(ControllingAttribute.Buyable(Attr.IQ), divination.attr)
        assertEquals(Difficulty.HARD, divination.difficulty)
        assertEquals(3, divination.bonus)
        assertTrue(seer.ability.startsWith("Danger Intuition: Passive."))
        // Eidetic Memory is an advantage row, kept as prose -- see SequenceBlock
        assertTrue(seer.advantages.startsWith("Eidetic Memory"))
    }

    /**
     * Three grants name no skill this book lists. They are printed all the
     * same, so they reach the screen as printed rather than vanishing (6gk).
     */
    @Test
    fun `a grant naming no skill is loaded as printed`() {
        val unresolved = blocks.filter { it.unresolvedSkills.isNotEmpty() }
            .associate { it.name to it.unresolvedSkills }
        assertEquals(
            mapOf(
                "Reader" to listOf("Any one knowledge skill (player's choice)/IQ [Average] +3"),
                "Savant" to listOf("Inventor!/IQ [Wildcard] +2"),
                "Potions Professor" to listOf("Poison/IQ [Hard] +3"),
            ),
            unresolved,
        )
    }

    @Test
    fun `a fractional Basic Speed gain survives as a fraction`() {
        // Seq 9 Assassin is the only +0.25 in the book; rounding it to an Int
        // would silently drop a quarter point of Basic Speed.
        val assassin = blocks.single { it.sequence == 9 && it.pathway == "Demoness" }
        val basicSpeed = assassin.stats.single { it.attr == "Basic Speed" }
        assertEquals(0.25, basicSpeed.delta)
    }

    @Test
    fun `a stat row naming no known attribute fails loudly`() {
        // The potion tables interleave attribute rows with advantage rows;
        // a parser that mistook an advantage name for an attribute must not
        // load as if it were one.
        assertFailsWith<IllegalArgumentException> { StatGain("Eidetic Memory", 1.0) }
    }

    @Test
    fun `every granted skill names a skill the chapter 3 list defines`() {
        val known = ContentLoader.skills().map { it.name }.toSet()
        // Potion tables specialize more finely than chapter 3 enumerates
        // ("Survival (any terrain)"), so the vocabulary check is on the base name.
        val unknown = blocks
            .flatMap { it.skills }
            .map { it.name.substringBefore(" (") }
            .filterNot { it in known }
            .distinct()
            .sorted()
        assertEquals(emptyList(), unknown)
    }

    @Test
    fun `the recorded difficulty disagreements really do disagree`() {
        // Guard against silent repair: these five skills carry a different
        // difficulty in the potion tables than in the chapter-3 skill list.
        // If this starts failing, the *data* changed, and that is the finding.
        val chapter3 = ContentLoader.skills()
            .groupBy({ it.name }, { it.attr to it.difficulty })
            .mapValues { (_, pairs) -> pairs.toSet() }

        val disagreeing = blocks
            .flatMap { it.skills }
            .filter { grant ->
                val base = grant.name.substringBefore(" (")
                chapter3[base]?.contains(grant.attr to grant.difficulty) == false
            }
            .map { it.name.substringBefore(" (") }
            .toSet()

        assertEquals(
            setOf("Herb Lore", "Herbal Medicine", "Jeweler", "Knife", "Leatherworking"),
            disagreeing,
        )
    }
}
