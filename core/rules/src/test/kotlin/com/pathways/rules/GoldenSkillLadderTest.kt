package com.pathways.rules

import com.pathways.model.Difficulty
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `SkillLadder` against the table it was written from (Ch. 3, p. 25-26).
 *
 * That object has been production code since M0 and every skill level the app
 * displays goes through it -- 232 of them across the 23 published sheets --
 * and until this test nothing compared it to the book. `SkillLadderTest`
 * checks the behaviour it was given; this checks the behaviour it was
 * SUPPOSED to be given.
 *
 * It passes on the code as written. That is the expected outcome of marking
 * homework and is still worth having: the ladder is deliberately not vanilla
 * GURPS, and something fails now if either side drifts.
 */
class GoldenSkillLadderTest {

    private val table = ContentLoader.skillLadder()

    private fun difficultyNamed(printed: String) = when (printed) {
        "Easy" -> Difficulty.EASY
        "Average" -> Difficulty.AVERAGE
        "Hard" -> Difficulty.HARD
        "Very Hard" -> Difficulty.VERY_HARD
        else -> error("the book printed a difficulty this app has no case for: $printed")
    }

    /**
     * The oracle. Both worked examples walk their own point ladder and print
     * the level at each rung, and every step must come back out of
     * [SkillLadder.levelFor].
     */
    @Test
    fun `every worked example step comes back out of levelFor`() {
        assertEquals(2, table.examples.size)
        table.examples.forEach { example ->
            val difficulty = difficultyNamed(example.difficulty)
            example.steps.forEach { step ->
                assertEquals(
                    step.level,
                    SkillLadder.levelFor(step.points, difficulty, example.attributeLevel),
                    "${example.skill} at ${step.points} pts",
                )
            }
        }
        // Eight steps between them, across two difficulties and five rungs --
        // more of levelFor than any hand-written case had covered.
        assertEquals(8, table.examples.sumOf { it.steps.size })
    }

    /**
     * Each difficulty's whole printed row, not just its first rung. This is
     * what justifies [Difficulty.firstRungOffset] being ONE number: the row is
     * that offset plus the rung index, all the way along.
     */
    @Test
    fun `every cell of the printed grid comes back out of levelFor`() {
        table.rungs.forEach { row ->
            val difficulty = difficultyNamed(row.difficulty)
            row.offsets.forEachIndexed { rung, offset ->
                val points = table.cumulative[rung]
                assertEquals(
                    10 + offset,
                    SkillLadder.levelFor(points, difficulty, attributeLevel = 10),
                    "${row.difficulty} at $points pts",
                )
            }
        }
    }

    @Test
    fun `the first rung of each row is the difficulty's own offset`() {
        table.rungs.forEach { row ->
            assertEquals(difficultyNamed(row.difficulty).firstRungOffset, row.offsets.first())
        }
    }

    /**
     * Points between two rungs buy the lower one. The book calls the column
     * "cumulative totals" and says you "pay the difference to move from one
     * step to the next", so 3 points is 2 points' worth of skill and a point
     * that bought nothing.
     */
    @Test
    fun `points short of the next rung buy the rung below`() {
        val hard = Difficulty.HARD
        assertEquals(SkillLadder.levelFor(2, hard, 10), SkillLadder.levelFor(3, hard, 10))
        assertEquals(SkillLadder.levelFor(8, hard, 10), SkillLadder.levelFor(15, hard, 10))
    }

    /**
     * **A reading, pinned.** The cumulative ladder prints nine costs and the
     * difficulty grid only eight, so the 256-point rung has a cost and no
     * printed offset. `SkillLadder` continues the row by +1, which the prose
     * supports ("each level after the third costs twice as much as the level
     * before it") but the grid never shows.
     */
    @Test
    fun `the ninth rung is extrapolated because the grid stops at eight`() {
        assertEquals(9, table.cumulative.size)
        table.rungs.forEach { assertEquals(8, it.offsets.size) }
        assertEquals(256, table.cumulative.last())

        table.rungs.forEach { row ->
            assertEquals(
                10 + row.offsets.last() + 1,
                SkillLadder.levelFor(256, difficultyNamed(row.difficulty), 10),
                "${row.difficulty} at 256 pts is off the printed grid",
            )
        }
    }

    /** The incremental column is the gaps between the cumulative one. */
    @Test
    fun `the two printed cost columns agree with each other`() {
        val gaps = listOf(table.cumulative.first()) +
            table.cumulative.zipWithNext { a, b -> b - a }
        assertEquals(gaps.take(table.incremental.size), table.incremental)
    }

    /**
     * The ladder is NOT vanilla GURPS, and this is the assertion that says so.
     * Core 4e costs 1/2/4/8/12/16 and +4 per level past Attr+2; using those
     * would make 14 of the 23 sheets' 232 skill levels wrong.
     */
    @Test
    fun `the ladder doubles rather than following GURPS core costs`() {
        assertEquals(listOf(1, 2, 4, 8, 16, 32, 64, 128, 256), table.cumulative)
        assertTrue(12 !in table.cumulative) { "12 is a GURPS 4e rung, not this book's" }
        table.cumulative.zipWithNext { a, b -> assertEquals(a * 2, b) }
    }

    /** Untrained defaults are a different table and stay where they were. */
    @Test
    fun `the grid says nothing about untrained defaults`() {
        assertEquals(6, SkillLadder.defaultFor(Difficulty.EASY, 10))
        assertNull(SkillLadder.defaultFor(Difficulty.VERY_HARD, 10))
    }
}
