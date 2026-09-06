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

    @Test
    fun `the attribute is the column's, and it is not the one the row counts down from`() {
        val twelve = band(12)
        assertEquals("Will", FrightCheck.recoveryFor(twelve, FrightKind.FRIGHT)!!.attribute)
        assertEquals("Will", FrightCheck.recoveryFor(twelve, FrightKind.AWE)!!.attribute)
        assertEquals("IQ", FrightCheck.recoveryFor(twelve, FrightKind.CONFUSION)!!.attribute)
        // That row's fright cell counts down from HT and rolls Will. If the
        // extraction ever anchored on an attribute NAME it would read HT here,
        // and this is the assertion that would say so.
        assertTrue(twelve.fright.contains("HT"), twelve.fright)
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
