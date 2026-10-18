package com.pathways.app

import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuilderLedgerTest {

    // Frame 23: 76 of 90 points fills 25 of 30 pips.
    @Test
    fun `the frame's budget fills twenty-five of thirty pips`() {
        assertEquals(25, budgetPips(spent = 76, budget = 90))
    }

    @Test
    fun `nothing spent fills none`() {
        assertEquals(0, budgetPips(spent = 0, budget = 90))
    }

    @Test
    fun `a pip fills only when its points are all spent`() {
        // 89 of 90 is 29.67 pips: the last stays empty until the budget is met.
        assertEquals(29, budgetPips(spent = 89, budget = 90))
        assertEquals(BUDGET_PIPS, budgetPips(spent = 90, budget = 90))
    }

    @Test
    fun `over budget fills every pip and no more`() {
        assertEquals(BUDGET_PIPS, budgetPips(spent = 120, budget = 90))
    }

    // A sheet whose disadvantages pay back more than it buys spends below 0.
    @Test
    fun `a negative spend fills none`() {
        assertEquals(0, budgetPips(spent = -15, budget = 70))
    }

    @Test
    fun `a zero budget fills none until something is spent`() {
        assertEquals(0, budgetPips(spent = 0, budget = 0))
        assertEquals(BUDGET_PIPS, budgetPips(spent = 5, budget = 0))
    }

    // The owner's ruling on frame 23: the rows read as drawn.
    @Test
    fun `the six priced rows take the frame's short names`() {
        val shown = ContentLoader.secondaryCosts()
            .filter { it.pointsPerStep != null }
            .map { secondaryShortName(it.key, it.characteristic) }
        assertEquals(listOf("HP", "Will", "Per", "FP", "Speed", "Move"), shown)
    }

    // Frame 24 is drawn at 390 dp with the study stepper beside the tag; at
    // font 1.3 the stepper took half the row and wrapped the name a word a line.
    @Test
    fun `the frame's phone keeps the stepper beside the tag`() {
        assertFalse(ledgerStacks(screenWidthDp = 390, fontScale = 1.0f))
    }

    @Test
    fun `a phone at large text puts the stepper on its own line`() {
        assertTrue(ledgerStacks(screenWidthDp = 390, fontScale = 1.3f))
    }

    // The ledger is drawn in the 600 dp column, so a wider window buys it
    // nothing: the SM-T570 in landscape is 960 dp, and at font 2.0 the
    // screen read 480 text-dp where the column gives 300.
    @Test
    fun `a landscape tablet at large text stacks, the column being capped`() {
        assertTrue(ledgerStacks(screenWidthDp = 960, fontScale = 2.0f))
    }

    @Test
    fun `a landscape tablet at large-ish text keeps it beside`() {
        assertFalse(ledgerStacks(screenWidthDp = 960, fontScale = 1.3f))
    }

    @Test
    fun `the tablet at large text keeps it beside`() {
        assertFalse(ledgerStacks(screenWidthDp = 600, fontScale = 1.3f))
    }

    @Test
    fun `a row the frame does not draw keeps the book's name`() {
        assertEquals("Spirituality", secondaryShortName("spi", "Spirituality"))
    }
}
