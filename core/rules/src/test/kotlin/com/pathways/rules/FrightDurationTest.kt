package com.pathways.rules

import com.pathways.rules.content.ComputedDuration
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.DurationAttribute
import com.pathways.rules.content.FrightEffect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Row 12's duration, which the book computes and the screen printed as a sum.
 *
 * 6ds structured the recovery ROLL of the three bands that print one. What
 * stayed prose was the duration, and one band's is a function of the
 * character: "Retching for (25 - HT) sec", "Ecstasy for (25 - Will) sec",
 * "Dazed for (25 - IQ) sec". A player read the parenthetical and did the
 * subtraction by hand.
 *
 * The boundary is the table's own: rows 4-9 print "1 sec" and 10-11
 * "1d-2d sec", neither of which has a stat in it, so neither is something
 * this app could work out.
 */
class FrightDurationTest {

    private fun band(low: Int) = ContentLoader.frightEffects().first { it.low == low }

    @Test
    fun `only the row with a stat in its duration computes one`() {
        FrightKind.entries.forEach { kind ->
            assertNotNull(FrightCheck.durationFor(band(12), kind), "band 12, $kind")
            // "1 sec" and "1d-2d sec" are durations too, and are already
            // resolved in the cell the screen prints.
            assertNull(FrightCheck.durationFor(band(4), kind), "band 4-9, $kind")
            assertNull(FrightCheck.durationFor(band(10), kind), "band 10-11, $kind")
            // Past 12 the table prints no duration at all.
            assertNull(FrightCheck.durationFor(band(13), kind), "band 13-15, $kind")
        }
    }

    @Test
    fun `each column counts down from the attribute its own cell names`() {
        val row = band(12)
        val seconds = { kind: FrightKind ->
            FrightCheck.secondsFor(FrightCheck.durationFor(row, kind)!!, ht = 11, will = 13, iq = 9)
        }
        assertEquals(14, seconds(FrightKind.FRIGHT), "25 - HT 11")
        assertEquals(12, seconds(FrightKind.AWE), "25 - Will 13")
        assertEquals(16, seconds(FrightKind.CONFUSION), "25 - IQ 9")
    }

    @Test
    fun `the fright cell counts down from an attribute it does not roll`() {
        val row = band(12)
        val duration = FrightCheck.durationFor(row, FrightKind.FRIGHT)!!
        val recovery = FrightCheck.recoveryFor(row, FrightKind.FRIGHT)!!
        // The whole reason the duration needs its own field: one cell, two
        // attributes, and taking the roll's would answer 12 seconds here.
        assertEquals(DurationAttribute.HT, duration.attribute)
        assertEquals(14, FrightCheck.secondsFor(duration, ht = 11, will = 13, iq = 9))
        assertEquals(13, FrightCheck.levelFor(recovery, will = 13, iq = 9))
    }

    @Test
    fun `the line the screen prints keeps the book's own subtraction visible`() {
        val row = band(12)
        val line = { kind: FrightKind ->
            FrightCheck.durationLine(FrightCheck.durationFor(row, kind)!!, ht = 11, will = 13, iq = 9)
        }
        // The attribute's printed name is the rules' to say, not the screen's:
        // asking it there would be the same predicate at a second call site.
        assertEquals("14 sec (25 − HT 11)", line(FrightKind.FRIGHT))
        assertEquals("12 sec (25 − Will 13)", line(FrightKind.AWE))
        assertEquals("16 sec (25 − IQ 9)", line(FrightKind.CONFUSION))
    }

    @Test
    fun `the floor shows its working, so a clamped duration is not silent`() {
        val duration = FrightCheck.durationFor(band(12), FrightKind.FRIGHT)!!
        assertEquals(
            "0 sec (25 − HT 27)",
            FrightCheck.durationLine(duration, ht = 27, will = 10, iq = 10),
        )
    }

    @Test
    fun `a band cannot compute a duration for some of its columns`() {
        // The table prints row 12's parenthetical in all three cells. A row
        // carrying one or two is a parse that dropped a cell, and the screen
        // would silently print the worked number for one column and the raw
        // subtraction for its neighbours.
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            FrightEffect(
                low = 12,
                high = 12,
                printed = "12",
                fright = "Retching for (25 − HT) sec.",
                awe = "Ecstasy for (25 − Will) sec.",
                confusion = "Dazed for (25 − IQ) sec.",
                frightComputedDuration = ComputedDuration(25, DurationAttribute.HT),
            )
        }
        assertTrue(
            thrown.message!!.contains("computes a duration in 1 of 3 columns"),
            "wrong guard spoke: ${thrown.message}",
        )
    }

    @Test
    fun `a duration cannot run backwards, though the book prints no floor`() {
        val duration = FrightCheck.durationFor(band(12), FrightKind.FRIGHT)!!
        assertEquals(0, FrightCheck.secondsFor(duration, ht = 27, will = 10, iq = 10))
        assertEquals(0, FrightCheck.secondsFor(duration, ht = 25, will = 10, iq = 10))
        assertEquals(1, FrightCheck.secondsFor(duration, ht = 24, will = 10, iq = 10))
    }
}
