package com.pathways.rules.content

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Glossary of Terms (p. 167-168) as content.
 *
 * It defines no rule the engine does not already implement, so nothing here
 * reaches the dice. What is asserted is that it stays REFERENCE a player can
 * trust: the book's own order, the book's own spelling, and definitions that
 * are whole sentences rather than a parse that stopped early.
 */
class GlossaryContentTest {

    private val entries = ContentLoader.glossary()

    /**
     * The canonical list, and the only assertion about terms in this file.
     *
     * Everything a second term-level assertion could say, this one already
     * forces: the order (which is the BOOK's and is not alphabetical --
     * `Sea of Collective Subconscious` prints after `Spirit Vision`, and
     * `3d6` between them and `Will`), the absence of `Examples`, which is a
     * label inside the Pathway entry rather than a term, and the presence of
     * the five terms this engine implements (CoR, Digestion, Pathway,
     * Sequence, Spirit Vision). Asserting any of those separately would be an
     * assertion no production change can fail on its own.
     */
    @Test
    fun `the glossary is the twenty-five terms the book prints, in its order`() {
        assertEquals(
            listOf(
                "Acting Method",
                "Astral Projection / Astral Body",
                "Backlund",
                "Beyonder",
                "Beyonder Weapon",
                "Body of Heart and Mind",
                "Character Points",
                "CoR",
                "Critical Success / Failure",
                "Digestion",
                "Ether Body",
                "FP (Fatigue Points)",
                "Game Master (GM)",
                "HP (Hit Points)",
                "Loen Kingdom",
                "Losing Control",
                "Margin of Success / Failure",
                "Pathway",
                "Potion",
                "Sequence",
                "SPI — Spirituality",
                "Spirit Vision",
                "Sea of Collective Subconscious",
                "3d6",
                "Will",
            ),
            entries.map { it.term },
        )
    }

    @Test
    fun `every definition is a whole sentence, not a parse that stopped early`() {
        // A blank definition cannot end in a full stop, so this covers both.
        val unterminated = entries.filterNot { it.definition.trimEnd().endsWith(".") }
        assertTrue(unterminated.isEmpty(), "definitions not ending in a full stop: $unterminated")
    }

    @Test
    fun `the wrapped entries kept the lines they wrap onto`() {
        val byTerm = entries.associate { it.term to it.definition }
        // Pathway's entry continues with a labelled line of its own, which is
        // text inside the definition and not a term of its own.
        assertTrue(byTerm.getValue("Pathway").endsWith("Darkness Pathway."))
        assertTrue("Examples: Fool Pathway" in byTerm.getValue("Pathway"))
        // Will is the last entry, and the closing note below it is not part of
        // its definition -- it read "...and CoR. Characters (4th Edition)."
        // until the extractor stopped at the note instead of skipping one line.
        assertTrue(byTerm.getValue("Will").endsWith("mind control, and CoR."))
    }
}
