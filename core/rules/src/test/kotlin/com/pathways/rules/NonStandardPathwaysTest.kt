package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NonStandardPathwaysTest {

    @Test
    fun `ten Sequence 9 blocks, seven Sequence 8 and one Sequence 7`() {
        val all = NonStandardPathways.all()
        assertEquals(18, all.size)
        assertEquals(10, all.count { it.sequence == 9 })
        assertEquals(7, all.count { it.sequence == 8 })
        // v6.9i's Sublunary Eye Sequence 7, the Painter: a third rung.
        assertEquals(listOf("Sublunary Eye" to "Painter"), all.filter { it.sequence == 7 }.map { it.pathway to it.name })
        assertEquals(14, NonStandardPathways.pathways().size)
    }

    @Test
    fun `not one of them is drinkable`() {
        // Chapter 14: "Unlike standard Beyonders who consume potions brewed
        // from Beyonder Characteristics, followers of non-standard pathways
        // advance through boons." PotionDigestion walks whatever pathways.json
        // holds, so a name in both files would put a Dancer potion in the
        // pathway screen's list. Separate files is the whole guard.
        val drinkable = ContentLoader.pathways().map { it.pathway }.toSet()
        val granted = NonStandardPathways.pathways().toSet()
        assertTrue((drinkable intersect granted).isEmpty(), "in both: ${drinkable intersect granted}")
    }

    @Test
    fun `the ladder has holes, and a hole is not an unknown pathway`() {
        // Three pathways print a Sequence 8 whose Sequence 9 the book never
        // records -- the chapter intro warns of exactly this. A documented
        // pathway missing a rung answers null; a pathway the book never
        // prints is refused, because those are different questions.
        assertNotNull(NonStandardPathways.blockFor("Second Law", 8))
        assertNull(NonStandardPathways.blockFor("Second Law", 9))
        assertThrows<IllegalArgumentException> {
            NonStandardPathways.blockFor("Fool", 9)
        }
    }

    @Test
    fun `a pathway walked twice is listed from Sequence 9 down`() {
        // Patriarch is the Scrooge at 9 and the Sex Addict at 8.
        assertEquals(listOf(9, 8), NonStandardPathways.knownSequences("Patriarch"))
        assertEquals("Scrooge", NonStandardPathways.blockFor("Patriarch", 9)?.name)
        assertEquals("Sex Addict", NonStandardPathways.blockFor("Patriarch", 8)?.name)
    }

    @Test
    fun `the two chapters spell one pathway differently and both survive`() {
        // Ch. 14 prints "Eternal Aeon (Inevitability)", ch. 15 "Eternal Aeon".
        // Merging them would be a repair; they stay as printed, so the second
        // reads as a pathway whose Sequence 9 is unrecorded.
        val printed = NonStandardPathways.pathways()
        assertTrue("Eternal Aeon (Inevitability)" in printed)
        assertTrue("Eternal Aeon" in printed)
        assertNull(NonStandardPathways.blockFor("Eternal Aeon", 9))
    }

    @Test
    fun `the disagreeing spellings point at each other without merging`() {
        // REGRESSION (Codex): the picker lists printed names, so both
        // spellings appear -- and choosing the chapter-15 one at Sequence 9
        // read "the book records no Sequence 9" while the Dancer block sat
        // under the chapter-14 one. Merging them would be a repair and would
        // hide that the book prints two names, so the lookup stays exact and
        // the pair is named instead.
        assertEquals(
            "Eternal Aeon (Inevitability)",
            NonStandardPathways.alsoPrintedAs("Eternal Aeon"),
        )
        assertEquals(
            "Eternal Aeon",
            NonStandardPathways.alsoPrintedAs("Eternal Aeon (Inevitability)"),
        )
        assertNull(NonStandardPathways.alsoPrintedAs("Patriarch"))
        // Still exact: the two are not silently the same pathway.
        assertNull(NonStandardPathways.blockFor("Eternal Aeon", 9))
        assertEquals("Dancer", NonStandardPathways.blockFor("Eternal Aeon (Inevitability)", 9)?.name)
        assertEquals("Alms Monk", NonStandardPathways.blockFor("Eternal Aeon", 8)?.name)
    }

    @Test
    fun `three blocks print an Ability heading and nothing under it`() {
        // A source defect, not a parse loss: in all three the next line is a
        // page break or the following block's header. Asserting WHICH three
        // makes a silent repair impossible.
        val silent = NonStandardPathways.all()
            .filter { it.ability.isBlank() }
            .map { it.name to it.sequence }
            .toSet()
        assertEquals(
            setOf("Villain" to 9, "Astronomy Aficionado" to 9, "Secretary" to 8),
            silent,
        )
    }

    @Test
    fun `the grants parse the same way the potion blocks do`() {
        val dancer = NonStandardPathways.blockFor("Eternal Aeon (Inevitability)", 9)
        assertNotNull(dancer)
        assertEquals("Dancer", dancer!!.name)
        assertEquals(6, dancer.stats.single { it.attr == "SPI" }.delta.toInt())
        assertEquals(4, dancer.skills.single { it.name == "Dancing" }.bonus)
        assertTrue("Spiritual Dance" in dancer.ability)
    }
}
