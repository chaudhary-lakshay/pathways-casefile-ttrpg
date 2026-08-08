package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.Difficulty
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ch. 3's Study Points page (p. 32) marking the engine's homework -- 6an.
 *
 * The page prints THREE things and only one of them is new content. Its
 * "Cumulative CP" grid (source lines 2206-2230) is a truncated second printing
 * of the skill cost grid `skill_ladder.json` already carries from p. 25-26,
 * and its Character Creation Summary (2238-2244) restates constants
 * [PointBudget] and [AttributeCosts] have held since M0. Both are checked here
 * rather than stored: a second copy of a table this repo already commits is
 * the silent merge 4.1 forbids, and the AGREEMENT is the only thing a second
 * printing is good for -- the standard 6y's two preparation tables and 6aa's
 * two SPI ladders set.
 *
 * The genuinely new content is the time-and-money table (2186-2200, "6 hrs +
 * 4s" with a tutor against "12 hrs + 1s" self-study, Very Hard self-study
 * printed "Not possible"). It is deliberately NOT extracted, because nothing
 * consumes it: study points are a separate currency earned over downtime, and
 * this app holds no clock -- the reason Appendix A's SPI recovery (6ad) and a
 * technique's duration (6ah) are both unmodelled. Extracting it would produce
 * a content file read by nothing, which is the defect 6ai, 6al and 6am each
 * found in turn.
 */
class GoldenStudyPointsTest {

    /**
     * The four rungs p. 32 prints, transcribed from source lines 2206-2230.
     *
     * Cumulative CP down the left, one column per difficulty. The page stops
     * at 8 CP where p. 25-26's grid runs to 256, so this is a PREFIX and is
     * asserted as one -- treating it as the whole ladder would silently cap
     * every skill at Attribute+3.
     */
    private val printedGrid = listOf(
        1 to listOf(
            Difficulty.EASY to 0, Difficulty.AVERAGE to -1,
            Difficulty.HARD to -2, Difficulty.VERY_HARD to -3,
        ),
        2 to listOf(
            Difficulty.EASY to 1, Difficulty.AVERAGE to 0,
            Difficulty.HARD to -1, Difficulty.VERY_HARD to -2,
        ),
        4 to listOf(
            Difficulty.EASY to 2, Difficulty.AVERAGE to 1,
            Difficulty.HARD to 0, Difficulty.VERY_HARD to -1,
        ),
        8 to listOf(
            Difficulty.EASY to 3, Difficulty.AVERAGE to 2,
            Difficulty.HARD to 1, Difficulty.VERY_HARD to 0,
        ),
    )

    private fun label(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> "Easy"
        Difficulty.AVERAGE -> "Average"
        Difficulty.HARD -> "Hard"
        Difficulty.VERY_HARD -> "Very Hard"
    }

    @Test
    fun `page 32's grid agrees with the engine at every printed cell`() {
        val attributeLevel = 10
        printedGrid.forEach { (points, row) ->
            row.forEach { (difficulty, offset) ->
                assertEquals(
                    attributeLevel + offset,
                    SkillLadder.levelFor(points, difficulty, attributeLevel),
                    "$points CP, $difficulty should be Attr$offset",
                )
            }
        }
    }

    @Test
    fun `and that agreement is what licenses reading it as one ladder`() {
        // Two printings, one engine. If this ever breaks, p. 32 has become a
        // second opinion rather than a restatement, and 4.1 would put the
        // question of which page wins back on the table.
        val grid = ContentLoader.skillLadder()
        printedGrid.forEach { (points, row) ->
            val rung = grid.cumulative.indexOf(points)
            assertTrue(rung >= 0, "p.25 grid prints no $points CP rung")
            row.forEach { (difficulty, offset) ->
                assertEquals(
                    offset,
                    grid.rungs.single { it.difficulty == label(difficulty) }.offsets[rung],
                    "p.25 and p.32 disagree at $points CP, $difficulty",
                )
            }
        }
    }

    @Test
    fun `the page stops at eight CP and is a prefix, not the whole ladder`() {
        // 8 CP is Attribute+3 for an Easy skill here; the fuller grid runs on
        // to 256. Reading this page as complete would cap every skill three
        // levels above its attribute.
        assertEquals(13, SkillLadder.levelFor(8, Difficulty.EASY, 10))
        assertEquals(17, SkillLadder.levelFor(128, Difficulty.EASY, 10))
    }

    @Test
    fun `the worked example reproduces, including the default it starts from`() {
        // "Tomas (DX 10) wants Guns (Pistol) -- DX/Average, default DX-5 = 5.
        //  He hires a tutor: 12 hrs + 8s earns 1 study CP -> Guns-9 (Attr-1).
        //  He studies 12 more hours (8s) for 1 more CP -> Guns-10 (Attr+0).
        //  Another 24 hours (16s) for 2 more CP -> Guns-11 (Attr+1) -- the cap."
        //
        // A third worked example beside 6ac's two, from a different page, and
        // the only one in the book that exercises the untrained default and
        // the ladder in the same breath.
        val dx = 10
        assertEquals(5, SkillLadder.defaultFor(Difficulty.AVERAGE, dx))
        assertEquals(9, SkillLadder.levelFor(1, Difficulty.AVERAGE, dx))
        assertEquals(10, SkillLadder.levelFor(2, Difficulty.AVERAGE, dx))
        // 1 + 1 + 2 = 4 CP, which the example reaches by spending twice more.
        assertEquals(11, SkillLadder.levelFor(4, Difficulty.AVERAGE, dx))
    }

    @Test
    fun `the example's own cap is Attribute plus one, which the Limits repeat`() {
        // "A skill cannot exceed Attribute+1 through study alone" (line 2235),
        // and the example stops at Guns-11 calling it "the cap" -- so the page
        // demonstrates its own limit. Nothing enforces it, because nothing
        // studies; what this pins is that the cap is the engine's Attr+1 rung
        // and not a fifth number the page invented.
        val dx = 10
        assertEquals(dx + 1, SkillLadder.levelFor(4, Difficulty.AVERAGE, dx))
        // The rung ABOVE it exists and is reachable by ordinary purchase,
        // which is exactly why the limit has to say "through study alone".
        assertEquals(dx + 2, SkillLadder.levelFor(8, Difficulty.AVERAGE, dx))
    }

    @Test
    fun `Very Hard prints no self-study row and no untrained default either`() {
        // The time table's Very Hard self-study cell reads "Not possible"
        // (line 2200), and Difficulty.VERY_HARD has carried a null default
        // since M0. Two different rules, both REFUSING rather than pricing --
        // recorded side by side, not merged: one is about learning alone, the
        // other about rolling untaught, and neither implies the other.
        assertNull(Difficulty.VERY_HARD.defaultPenalty)
        assertNull(SkillLadder.defaultFor(Difficulty.VERY_HARD, 10))
    }

    @Test
    fun `the Character Creation Summary agrees with the constants it summarises`() {
        // Lines 2239-2244, bullet by bullet. Every one is a live constant,
        // which is what makes the summary checkable rather than authoritative
        // -- 6v's rule, and the reason it is not extracted either.
        assertEquals(70, PointBudget.STARTING_TOTAL)
        assertEquals(40, PointBudget.MAX_DISADVANTAGES)
        // "each level doubles the previous: ST: 10 -> 20 -> 40 |
        //  DX/IQ: 20 -> 40 -> 80 | HT: 10 -> 20 -> 40"
        //
        // The summary prints the cost of EACH level; `costOf` returns the
        // running total from the free baseline of 9. They agree once diffed
        // -- ST reads 10/30/70 cumulatively, whose steps are 10/20/40 -- and
        // comparing the two shapes directly is how a reader concludes the
        // engine disagrees with the book when it does not. Written as a diff
        // so the assertion says which quantity it is checking.
        fun steps(attr: Attr) = (10..12).map {
            AttributeCosts.costOf(attr, it) - AttributeCosts.costOf(attr, it - 1)
        }
        assertEquals(listOf(10, 20, 40), steps(Attr.ST))
        assertEquals(listOf(20, 40, 80), steps(Attr.DX))
        assertEquals(listOf(20, 40, 80), steps(Attr.IQ))
        assertEquals(listOf(10, 20, 40), steps(Attr.HT))
        // And the baseline the doubling starts from: "All attributes fixed at
        // 9 | No points refunded for baseline."
        Attr.entries.forEach { assertEquals(0, AttributeCosts.costOf(it, 9), it.name) }
    }
}
