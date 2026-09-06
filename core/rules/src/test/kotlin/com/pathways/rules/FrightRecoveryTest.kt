package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.RecoveryState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Fright Effects table's own recovery rolls, which nothing offered.
 *
 * 6dr gave the Major Wound's stun a roller and left these nine cells a stated
 * gap: their attribute is the COLUMN's, not the state's, and it was carried in
 * prose. `extract_fright.py` structures it now, so what is under test here is
 * the scope — three rows have a roll and six do not, two rows are a stun that
 * Combat Reflexes reaches and one is three other states it does not.
 */
class FrightRecoveryTest {

    private fun trait(name: String) = Trait(name, 0, TraitKind.ADVANTAGE)

    private val reflexes = listOf(trait("Combat Reflexes"))
    private val fit = listOf(trait("Fit"))

    private fun band(low: Int) = ContentLoader.frightEffects().first { it.low == low }

    @Test
    fun `only the rows that print a roll offer one`() {
        val offering = ContentLoader.frightEffects().filter {
            FrightCheck.recoveryFor(it, FrightKind.FRIGHT) != null
        }
        assertEquals(listOf(4, 10, 12), offering.map { it.low })
        // A band offers one in every column or in none — asserted per band
        // rather than per column, because a table that gave one column a way
        // out another lacks is the failure this reads for.
        for (row in ContentLoader.frightEffects()) {
            val present = FrightKind.entries.count { FrightCheck.recoveryFor(row, it) != null }
            assertTrue(present == 0 || present == FrightKind.entries.size, "${row.printed}: $present")
        }
    }

    /**
     * The column decides, and [FrightCheck.levelFor] is the only thing asked.
     *
     * Row 12 is the dangerous case — its fright cell counts down from HT and
     * rolls Will — but the guard against reading HT there belongs to the
     * extractor's anchor test, which is mutation-proved against exactly that
     * substitution. Asserting the cell still contains "HT" here would restate
     * the book rather than exercise production, so what this checks is the
     * number a screen would roll against.
     */
    @Test
    fun `the column decides which attribute level the roll uses`() {
        val twelve = band(12)
        val will = 11
        val iq = 14
        assertEquals(will, FrightCheck.levelFor(FrightCheck.recoveryFor(twelve, FrightKind.FRIGHT)!!, will, iq))
        assertEquals(will, FrightCheck.levelFor(FrightCheck.recoveryFor(twelve, FrightKind.AWE)!!, will, iq))
        assertEquals(iq, FrightCheck.levelFor(FrightCheck.recoveryFor(twelve, FrightKind.CONFUSION)!!, will, iq))
    }

    /**
     * Which recovery rolls the bare-Will clause list reaches.
     *
     * Two of three columns and never the third: this book prints no trait
     * clause naming IQ rolls, so the Confusion column has no list to offer.
     * That is a gap in the source, recorded rather than papered over by
     * handing it the Will one.
     */
    @Test
    fun `only the Will columns take the bare-Will clauses`() {
        for (low in listOf(4, 10, 12)) {
            val row = band(low)
            assertTrue(FrightCheck.takesWillClauses(FrightCheck.recoveryFor(row, FrightKind.FRIGHT)!!))
            assertTrue(FrightCheck.takesWillClauses(FrightCheck.recoveryFor(row, FrightKind.AWE)!!))
            assertFalse(FrightCheck.takesWillClauses(FrightCheck.recoveryFor(row, FrightKind.CONFUSION)!!))
        }
    }

    @Test
    fun `Combat Reflexes reaches the stunned rows and not the twelve row`() {
        for (low in listOf(4, 10)) {
            val row = band(low)
            for (kind in FrightKind.entries) {
                val recovery = FrightCheck.recoveryFor(row, kind)!!
                assertEquals(RecoveryState.STUNNED, recovery.state)
                assertEquals(6, FrightCheck.recoveryBonus(recovery, reflexes))
            }
        }
        for (kind in FrightKind.entries) {
            val recovery = FrightCheck.recoveryFor(band(12), kind)!!
            assertFalse(recovery.state == RecoveryState.STUNNED, "$kind: $recovery")
            assertEquals(0, FrightCheck.recoveryBonus(recovery, reflexes))
        }
    }

    @Test
    fun `the twelve row prints three different states, and the book's own words`() {
        assertEquals(
            listOf(RecoveryState.RETCHING, RecoveryState.ECSTASY, RecoveryState.DAZED),
            FrightKind.entries.map { FrightCheck.recoveryFor(band(12), it)!!.state },
        )
    }

    @Test
    fun `Fit reaches none of them, because not one of these rolls is HT`() {
        for (row in listOf(band(4), band(10), band(12))) {
            for (kind in FrightKind.entries) {
                assertEquals(0, FrightCheck.recoveryBonus(FrightCheck.recoveryFor(row, kind)!!, fit))
            }
        }
        // The guard that says why: `Fit` is "+1 to all HT rolls", and it moves
        // the Major Wound's stun recovery for exactly that reason.
        assertEquals(1, StunRecovery.bonusFor(fit))
    }

    @Test
    fun `a row with no roll answers null in every column`() {
        for (low in listOf(13, 16, 22, 24, 30, 35)) {
            for (kind in FrightKind.entries) assertNull(FrightCheck.recoveryFor(band(low), kind))
        }
        assertNotNull(FrightCheck.recoveryFor(band(4), FrightKind.FRIGHT))
    }
}
