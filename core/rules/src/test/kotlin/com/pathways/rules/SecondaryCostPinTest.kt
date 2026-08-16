package com.pathways.rules

import com.pathways.model.Attributes
import com.pathways.model.SecondaryDelta
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Joins the book's printed Cost to Modify column to what the engine charges.
 *
 * [SecondaryCharacteristics]' six cost constants were literals with no tie to
 * any printed line: `SecondaryCharacteristicsTest` pins them against an EDIT
 * (change one and its arithmetic reddens) but nothing pinned them against the
 * BOOK. A re-extraction that moved a price would have left `PointBudget`
 * billing the old one with every test green.
 *
 * The one table that prints these prices was skipped by `extract_core_rules`
 * on the recorded grounds that chapter 3 printed them already. It does not:
 * chapter 3's `pts/level` lines are advantage rows.
 */
class SecondaryCostPinTest {

    private fun row(key: String) = ContentLoader.secondaryCosts().first { it.key == key }

    /** One step of the characteristic [key] costs, as the ENGINE charges it. */
    private fun engineCostOf(key: String): Int = SecondaryCharacteristics.costOf(
        when (key) {
            "hp" -> SecondaryDelta(hp = 1)
            "fp" -> SecondaryDelta(fp = 1)
            "will" -> SecondaryDelta(will = 1)
            "per" -> SecondaryDelta(per = 1)
            // One quarter, because that is the step the book prices.
            "basicSpeed" -> SecondaryDelta(basicSpeedQuarters = 1)
            "basicMove" -> SecondaryDelta(basicMove = 1)
            else -> throw IllegalArgumentException("$key is not a buyable characteristic")
        }
    )

    @Test
    @DisplayName("every price the book prints is the price the engine charges")
    fun `the printed column and the engine agree`() {
        val priced = ContentLoader.secondaryCosts().filter { it.pointsPerStep != null }
        assertEquals(6, priced.size)
        for (r in priced) {
            assertEquals(
                r.pointsPerStep,
                engineCostOf(r.key),
                "${r.characteristic}: the book prints \"${r.cost}\" and the engine charges " +
                    "${engineCostOf(r.key)} for one ${r.step}",
            )
        }
    }

    @Test
    @DisplayName("Basic Speed is priced per quarter, and the engine counts quarters")
    fun `the one row with a different unit keeps it`() {
        val speed = row("basicSpeed")
        assertEquals("0.25", speed.step)
        assertEquals(5, speed.pointsPerStep)
        // The unit is the whole point: a full point of Basic Speed is four
        // quarters and costs four times the printed number. A parser that
        // dropped "per 0.25" would have made a point of Speed cost 5.
        assertEquals(20, SecondaryCharacteristics.costOf(SecondaryDelta(basicSpeedQuarters = 4)))
        // Every OTHER priced row is per level. Asserted as equality rather
        // than as "null or level": the OR was satisfied by all five rows
        // regressing to null, which is the corruption this test exists for.
        val otherPriced = ContentLoader.secondaryCosts()
            .filter { it.key != "basicSpeed" && it.pointsPerStep != null }
        assertEquals(5, otherPriced.size)
        assertTrue(otherPriced.all { it.step == "level" })
    }

    @Test
    @DisplayName("the three rows the table refuses to price cannot be bought at all")
    fun `an unpriced row is a refusal and not a zero`() {
        for (key in listOf("spi", "cor", "digestion")) {
            val r = row(key)
            assertNull(r.pointsPerStep, "${r.characteristic} acquired a price")
            assertTrue(r.cost.isNotBlank())
        }
        // SecondaryDelta has no field for any of the three, so the refusal is
        // structural rather than a check someone has to remember. This is
        // section 4.4's rule about SPI, printed a second time one chapter over.
        assertEquals("Fixed at 0; see Chapter 4", row("spi").cost)
        assertEquals("Tracked in play; never bought", row("cor").cost)
    }

    @Test
    @DisplayName("the formulas the engine reproduces are the ones the table prints")
    fun `the derivations agree with the printed column`() {
        assertEquals("= ST (9)", row("hp").formula)
        assertEquals("= HT (9)", row("fp").formula)
        // Both from IQ. Deriving Per from anything else is the defect that
        // ControllingAttribute exists to make unrepresentable (4.4).
        assertEquals("= IQ (9)", row("will").formula)
        assertEquals("= IQ (9)", row("per").formula)
        assertTrue(row("basicSpeed").formula.contains("(HT + DX) / 4"))

        // ... and the ENGINE is asked, not just the string. Comparing two
        // pieces of JSON would pin the file to itself: if `of` derived HP
        // from DX tomorrow, every assertion above would still pass. Distinct
        // attribute values, so no two derivations can agree by coincidence.
        val attrs = Attributes(st = 11, dx = 12, iq = 13, ht = 14)
        val derived = SecondaryCharacteristics.of(attrs, SecondaryDelta())
        assertEquals(attrs.st, derived.maxHp, "HP is printed \"= ST (9)\"")
        assertEquals(attrs.ht, derived.maxFp, "FP is printed \"= HT (9)\"")
        assertEquals(attrs.iq, derived.will, "Will is printed \"= IQ (9)\"")
        assertEquals(attrs.iq, derived.per, "Per is printed \"= IQ (9)\"")
        // "(HT + DX) / 4", counted in quarters, so HT + DX is the quarter count.
        assertEquals(attrs.ht + attrs.dx, derived.basicSpeedQuarters)
    }

    @Test
    @DisplayName("the table keeps its nine rows in the order the book prints them")
    fun `the row set is pinned`() {
        val rows = ContentLoader.secondaryCosts()
        assertEquals(9, rows.size)
        assertEquals(
            listOf("hp", "will", "per", "fp", "spi", "cor", "basicSpeed", "basicMove",
                "digestion"),
            rows.map { it.key },
        )
        assertTrue(rows.all { it.characteristic.isNotBlank() && it.formula.isNotBlank() })
    }
}
