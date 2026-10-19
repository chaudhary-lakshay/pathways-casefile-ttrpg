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

    /**
     * v6.9i drops the old ch. 9 "SPI-based skills cannot be raised with
     * character points" and prints no SPI-controlled skill at all: Divination
     * Arts is IQ/Hard, and the two senses are universal abilities. So every
     * printed skill is bought the same way.
     */
    @Test
    fun `no printed skill is SPI-controlled`() {
        assertTrue(ContentLoader.skills().none { it.attr == ControllingAttribute.Spi })
    }

    /**
     * The book prints cumulative totals, "you pay the difference to move from
     * one step to the next" (line 1573), so the builder steps a bought skill
     * from rung to rung. Its one-point "+" sold 3 points that bought nothing
     * over 2 (play session 2).
     */
    @Test
    fun `a step up buys the difference to the next rung`() {
        assertEquals(2, SkillLadder.stepped(bought = 1, studied = 0, up = true))
        assertEquals(4, SkillLadder.stepped(bought = 2, studied = 0, up = true))
        assertEquals(8, SkillLadder.stepped(bought = 4, studied = 0, up = true))
        // Off a rung (an older save): up to the next one, never past it.
        assertEquals(4, SkillLadder.stepped(bought = 3, studied = 0, up = true))
        // Study points count toward the rung and are never bought.
        assertEquals(6, SkillLadder.stepped(bought = 2, studied = 2, up = true))
        // Past the printed grid there is nothing to buy.
        assertEquals(256, SkillLadder.stepped(bought = 256, studied = 0, up = true))
    }

    @Test
    fun `a step down sells back to the rung below`() {
        assertEquals(4, SkillLadder.stepped(bought = 8, studied = 0, up = false))
        assertEquals(1, SkillLadder.stepped(bought = 2, studied = 0, up = false))
        // 3 points sits on the 2-point rung, so the rung below is 1.
        assertEquals(1, SkillLadder.stepped(bought = 3, studied = 0, up = false))
        // At one point, down un-buys the skill.
        assertEquals(0, SkillLadder.stepped(bought = 1, studied = 0, up = false))
        assertEquals(2, SkillLadder.stepped(bought = 6, studied = 2, up = false))
    }

    /**
     * Where study alone already holds the rung below, the step keeps one
     * bought point rather than un-buying the skill: un-buying discards its
     * study too (`BeyonderSession.spendOnSkill`).
     */
    @Test
    fun `a step down never un-buys a skill that holds more than one point`() {
        assertEquals(1, SkillLadder.stepped(bought = 4, studied = 3, up = false))
        assertEquals(0, SkillLadder.stepped(bought = 1, studied = 3, up = false))
    }
}
