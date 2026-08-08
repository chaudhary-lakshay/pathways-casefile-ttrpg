package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.rules.content.ContentLoader
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SkillLadderTest {
    @Test
    fun `the book's Research example holds`() {
        // Research (IQ/Hard), IQ 9: 1pt -> 7, 2pt -> 8, 4pt -> 9, 8pt -> 10, 16pt -> 11
        assertEquals(7,  SkillLadder.levelFor(1,  Difficulty.HARD, 9))
        assertEquals(8,  SkillLadder.levelFor(2,  Difficulty.HARD, 9))
        assertEquals(9,  SkillLadder.levelFor(4,  Difficulty.HARD, 9))
        assertEquals(10, SkillLadder.levelFor(8,  Difficulty.HARD, 9))
        assertEquals(11, SkillLadder.levelFor(16, Difficulty.HARD, 9))
    }

    @Test
    fun `the book's Brawling example holds`() {
        // Brawling (DX/Easy), DX 9: 1pt -> 9, 2pt -> 10, 4pt -> 11
        assertEquals(9,  SkillLadder.levelFor(1, Difficulty.EASY, 9))
        assertEquals(10, SkillLadder.levelFor(2, Difficulty.EASY, 9))
        assertEquals(11, SkillLadder.levelFor(4, Difficulty.EASY, 9))
    }

    @Test
    fun `every Occult Scholar skill reproduces at IQ 10`() {
        val iq = 10
        assertEquals(13, SkillLadder.levelFor(16, Difficulty.AVERAGE,   iq)) // Occultism
        assertEquals(12, SkillLadder.levelFor(8,  Difficulty.AVERAGE,   iq)) // Research
        assertEquals(11, SkillLadder.levelFor(8,  Difficulty.HARD,      iq)) // History (Occult)
        assertEquals(11, SkillLadder.levelFor(4,  Difficulty.AVERAGE,   iq)) // Hidden Lore (Spirits)
        assertEquals(10, SkillLadder.levelFor(1,  Difficulty.EASY,      iq)) // First Aid
        assertEquals(10, SkillLadder.levelFor(4,  Difficulty.HARD,      iq)) // Diagnosis
        assertEquals(9,  SkillLadder.levelFor(4,  Difficulty.VERY_HARD, iq)) // Alchemy
        assertEquals(10, SkillLadder.levelFor(2,  Difficulty.AVERAGE,   iq)) // Public Speaking
    }

    @Test
    fun `points between rungs do not advance the level`() {
        assertEquals(11, SkillLadder.levelFor(16, Difficulty.HARD, 9))
        assertEquals(11, SkillLadder.levelFor(31, Difficulty.HARD, 9))
        assertEquals(12, SkillLadder.levelFor(32, Difficulty.HARD, 9))
    }

    @Test
    fun `zero points buys nothing`() {
        assertFailsWith<IllegalArgumentException> { SkillLadder.levelFor(0, Difficulty.EASY, 9) }
    }

    @Test
    fun `untrained defaults follow the book`() {
        assertEquals(5, SkillLadder.defaultFor(Difficulty.EASY, 9))
        assertEquals(4, SkillLadder.defaultFor(Difficulty.AVERAGE, 9))
        assertEquals(3, SkillLadder.defaultFor(Difficulty.HARD, 9))
    }

    @Test
    fun `Very Hard skills cannot be attempted untrained`() {
        assertNull(SkillLadder.defaultFor(Difficulty.VERY_HARD, 9))
        assertNull(SkillLadder.defaultFor(Difficulty.VERY_HARD, 18))
    }

    @Test
    fun `difficulty codes match the sheets`() {
        assertEquals(Difficulty.VERY_HARD, Difficulty.fromCode("VH"))
        assertEquals(Difficulty.AVERAGE, Difficulty.fromCode("A"))
    }

    @Test
    fun `SPI-based skills cannot be bought with points`() {
        // Ch. 9 p. 85: "Exception -- SPI-based skills (Spiritual Intuition,
        // Spiritual Perception, Divination Arts) cannot be raised with
        // character points. They improve only through Sequence progression
        // and pathway bonuses."
        assertFalse(SkillLadder.isBuyableWithPoints(ControllingAttribute.Spi))
        listOf(
            ControllingAttribute.Buyable(Attr.IQ),
            ControllingAttribute.Buyable(Attr.DX),
            ControllingAttribute.Will,
            ControllingAttribute.Per,
        ).forEach { assertTrue(SkillLadder.isBuyableWithPoints(it), "$it") }
    }

    @Test
    fun `the sentence names three skills and the book prints exactly those three`() {
        // This exact match is the WARRANT for reading the rule as a property
        // of the controlling attribute rather than a list of three names. If
        // a fourth SPI skill ever appears, or one of these three stops being
        // SPI-based, the reading has to be made again rather than inherited
        // -- the standard 6ad set, and 6v is why it must be per-table.
        val spi = ContentLoader.skills().filter { it.attr == ControllingAttribute.Spi }
        assertEquals(
            listOf("Divination Arts", "Spiritual Intuition", "Spiritual Perception"),
            spi.map { it.name }.sorted(),
        )
    }

    @Test
    fun `and the potions grant them, so the rule gates rather than forbids`() {
        // Refusing the purchase would be a bug if nothing else could supply
        // these skills -- the Divine button and both spiritual senses would
        // become unreachable. Fifteen Sequence blocks grant one of the three,
        // so the book's own route is intact: a Beyonder gets them, a mortal
        // does not, which is the gate stated explicitly rather than left to
        // the arithmetic of SPI 0.
        val granted = ContentLoader.pathways()
            .flatMap { it.skills }
            .filter {
                it.name in setOf(
                    "Divination Arts", "Spiritual Intuition", "Spiritual Perception",
                )
            }
        assertTrue(granted.size >= 15, "only ${granted.size} potion grants of the SPI skills")
        assertTrue(granted.any { it.name == "Divination Arts" })
    }

    @Test
    fun `Ritualistic Magic stays buyable, because it is not SPI-based`() {
        // The ritual screen's whole gate is that no premade prints the skill
        // and it must be bought (6e, 6f). It is IQ/Very Hard, so this rule
        // does not touch it -- and confirming that is what stops the fix
        // above from quietly removing rituals along with divination.
        val ritual = ContentLoader.skills().single { it.name == "Ritualistic Magic" }
        assertEquals(ControllingAttribute.Buyable(Attr.IQ), ritual.attr)
        assertTrue(SkillLadder.isBuyableWithPoints(ritual.attr))
    }
}
