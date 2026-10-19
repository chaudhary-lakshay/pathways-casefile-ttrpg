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
 * Ch. 2's attribute cost table marking [AttributeCosts] -- 6ao, re-anchored
 * to v6.9i, whose table is FLAT: "each level above the baseline costs a flat
 * rate that does not increase" (book 540-541). The book prints it twice
 * (353-366 and 542-550) and works two cumulative totals (552-553).
 */
class GoldenAttributeTableTest {

    /** The printed rate, one cell per attribute, at every rung. */
    private val printedRate = mapOf(Attr.ST to 10, Attr.DX to 20, Attr.IQ to 20, Attr.HT to 20)

    @Test
    fun `every printed cell comes back out of costOf as a difference`() {
        printedRate.forEach { (attr, rate) ->
            (BASE_ATTRIBUTE + 1..BASE_ATTRIBUTE + 5).forEach { to ->
                assertEquals(
                    rate,
                    AttributeCosts.costOf(attr, to) - AttributeCosts.costOf(attr, to - 1),
                    "$attr ${to - 1}->$to should cost $rate",
                )
            }
        }
    }

    @Test
    fun `the two worked totals reproduce, which is the cumulative check`() {
        // "Example -- DX 11: Two levels (9->10, 10->11) at 20 pts each.
        //  Total: 40 pts for DX 11."          (source line 552)
        assertEquals(40, AttributeCosts.costOf(Attr.DX, 11))
        // "Example -- ST 12: Three levels (9->10, 10->11, 11->12) at 10 pts
        //  each. Total: 30 pts for ST 12."     (line 553)
        assertEquals(30, AttributeCosts.costOf(Attr.ST, 12))
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
    fun `ST is the one cheap attribute and HT costs what DX and IQ do`() {
        // v6.9i moved HT from ST's 10 to 20; the shape is one cheap row now.
        (BASE_ATTRIBUTE + 1..BASE_ATTRIBUTE + 5).forEach { level ->
            assertEquals(AttributeCosts.costOf(Attr.DX, level), AttributeCosts.costOf(Attr.HT, level), "HT at $level")
            assertEquals(AttributeCosts.costOf(Attr.DX, level), AttributeCosts.costOf(Attr.IQ, level), "IQ at $level")
            assertEquals(2 * AttributeCosts.costOf(Attr.ST, level), AttributeCosts.costOf(Attr.DX, level), "ST at $level")
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
