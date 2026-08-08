package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.BASE_ATTRIBUTE
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Ch. 3's attribute cost table (p. 10-11) marking [AttributeCosts] -- 6ao.
 *
 * [AttributeCosts] has been production code since M0 and every point total the
 * app displays runs through it; nothing had ever compared it to the table it
 * was written from. `GoldenSkillLadderTest` (6ac) did this for skills and
 * `GoldenStudyPointsTest` (6an) for the study page's restatement of it. This
 * is the attribute half, and it is the one with real money on it: the grid is
 * twenty printed cells and the book works two totals in public.
 *
 * **The table prints INCREMENTAL costs; [AttributeCosts.costOf] returns the
 * CUMULATIVE total.** The two worked examples are the only place the book
 * gives a cumulative figure, which makes them the bridge between the two
 * quantities and the reason this test can check `costOf` at all. 6an's summary
 * test had to discover that distinction the hard way, comparing 10/20/40
 * against 10/30/70 and reading it as a disagreement.
 */
class GoldenAttributeTableTest {

    /**
     * The printed grid, source lines 468-499: one row per attribute, one
     * column per raise, INCREMENTAL cost in each cell.
     */
    private val printedGrid = mapOf(
        Attr.ST to listOf(10, 20, 40, 80, 160),
        Attr.DX to listOf(20, 40, 80, 160, 320),
        Attr.IQ to listOf(20, 40, 80, 160, 320),
        Attr.HT to listOf(10, 20, 40, 80, 160),
    )

    @Test
    fun `every printed cell comes back out of costOf as a difference`() {
        printedGrid.forEach { (attr, steps) ->
            steps.forEachIndexed { i, printed ->
                val to = BASE_ATTRIBUTE + 1 + i
                assertEquals(
                    printed,
                    AttributeCosts.costOf(attr, to) - AttributeCosts.costOf(attr, to - 1),
                    "$attr ${to - 1}->$to should cost $printed",
                )
            }
        }
    }

    @Test
    fun `the two worked totals reproduce, which is the cumulative check`() {
        // "Example -- DX 11: 9->10 costs 20 pts, 10->11 costs 40 pts.
        //  Total: 60 pts for DX 11."         (source line 500)
        assertEquals(60, AttributeCosts.costOf(Attr.DX, 11))
        // "Example -- ST 12: 9->10 costs 10 pts, 10->11 costs 20 pts,
        //  11->12 costs 40 pts. Total: 70 pts for ST 12."   (line 501)
        assertEquals(70, AttributeCosts.costOf(Attr.ST, 12))
    }

    @Test
    fun `nine is free and buying below it is refused, not refunded`() {
        // "A character who leaves an attribute at 9 pays nothing and receives
        //  no refund -- 9 is simply the starting point, not a discount."
        //  (line 502). A negative cost would hand out points for weakness,
        //  which is exactly what the sentence forbids, so the engine REFUSES
        //  rather than returning one -- the distinction a refusal draws over a
        //  plausible number, as `Detection.attributeFor` (6ah) already needed.
        Attr.entries.forEach { assertEquals(0, AttributeCosts.costOf(it, BASE_ATTRIBUTE), it.name) }
        assertThrows<IllegalArgumentException> {
            AttributeCosts.costOf(Attr.ST, BASE_ATTRIBUTE - 1)
        }
        assertEquals(0, AttributeCosts.totalCost(Attributes()))
    }

    @Test
    fun `ST and HT are the cheap pair and DX and IQ the dear one, at every rung`() {
        // The table's whole shape: two base rates, not four. Asserted across
        // the printed width rather than at one cell, because a single-cell
        // check passes for any ladder that happens to agree at that rung.
        (BASE_ATTRIBUTE + 1..BASE_ATTRIBUTE + 5).forEach { level ->
            assertEquals(
                AttributeCosts.costOf(Attr.ST, level),
                AttributeCosts.costOf(Attr.HT, level),
                "ST and HT part company at $level",
            )
            assertEquals(
                AttributeCosts.costOf(Attr.DX, level),
                AttributeCosts.costOf(Attr.IQ, level),
                "DX and IQ part company at $level",
            )
            assertEquals(
                2 * AttributeCosts.costOf(Attr.ST, level),
                AttributeCosts.costOf(Attr.DX, level),
                "the dear pair should be exactly twice the cheap pair at $level",
            )
        }
    }

    @Test
    fun `chapter 3's SPI ceiling is contradicted by its own third example`() {
        // Line 505-506: "mysticism-leaning pathways (e.g., Seer, Sleepless,
        // Monster) grant as much as +9, while reality-leaning pathways (e.g.,
        // Hunter, Warrior, Savant) grant as little as +1."
        //
        // The LOW half holds exactly -- all three named grant 1. The HIGH half
        // does not: Seer and Sleepless grant 9, and MONSTER GRANTS 12, so the
        // sentence's own third example exceeds the ceiling the same sentence
        // states. Ch. 4's paragraph describes the same data with no ceiling at
        // all and is consistent with it (6aj), which is what makes this
        // chapter 3's slip rather than chapter 9's.
        //
        // 4.1: the book wins and the disagreement is RECORDED. Nothing reads
        // this sentence, so there is nothing to repair -- what this test stops
        // is a later reader treating "+9" as a bound and trimming the data, or
        // "fixing" the sentence, either of which erases the conflict.
        fun spiAt9(rank: String) = ContentLoader.pathways()
            .single { it.sequence == 9 && it.name == rank }
            .stats.filter { it.attr == "SPI" }.sumOf { it.delta }

        listOf("Hunter", "Warrior", "Savant").forEach {
            assertEquals(1.0, spiAt9(it), "$it should grant the printed low end of +1")
        }
        assertEquals(9.0, spiAt9("Seer"))
        assertEquals(9.0, spiAt9("Sleepless"))
        assertEquals(
            12.0,
            spiAt9("Monster"),
            "Monster is chapter 3's own example of the +9 high end and grants 12",
        )
    }

    @Test
    fun `and no mortal pays for SPI, which is why the table prints four rows`() {
        // "All mortals begin at SPI 0, and it cannot be raised with character
        // points" (line 503). The table prints four rows, not five, and
        // `Attr` has four entries for the same reason -- 4.4's whole point.
        assertEquals(4, Attr.entries.size)
        assertTrue(Attr.entries.none { it.name == "SPI" })
    }
}
