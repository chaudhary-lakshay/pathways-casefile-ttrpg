package com.pathways.rules

import com.pathways.model.Difficulty
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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
}
