package com.pathways.app

import com.pathways.model.BeyonderState
import com.pathways.rules.Pool
import com.pathways.rules.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The wiring under the screens.
 *
 * Both defects this file guards against reached `master` and were caught by
 * review, not by a test: CoR was charged only when the advancement cost
 * something, and FP spent below zero never billed the HP the book charges for
 * it. Neither is visible in `core:rules`, which is why this source set exists.
 */
class BeyonderSessionTest {

    /** Always rolls [face], so a PCR outcome is a decision, not a coin toss. */
    private fun fixed(face: Int) = RandomSource { face }

    private val fool9 = BeyonderState("Fool", 9)
    private val fool8 = BeyonderState("Fool", 8)

    private fun session(saved: SavedSession = SavedSession()): Pair<BeyonderSession, () -> SavedSession> {
        var last = saved
        return BeyonderSession(saved) { last = it } to { last }
    }

    @Test
    fun `a wealth level is stored as a choice and survives a save`() {
        // Only the CHOICE is stored, never the trait it resolves to: the
        // points and the reaction modifier belong to the book, and a save file
        // carrying its own copy could contradict it after a content fix --
        // the same rule a bought skill already follows.
        val (s, saved) = session()
        s.setWealth("Very Wealthy")
        assertEquals("Very Wealthy", s.edits.wealth)
        assertEquals("Very Wealthy", saved().edits.wealth)

        // Clearing it is reachable from the picker's "As printed" row, and is
        // the only way back to the published sheet: 20 of the 23 premades
        // print no wealth level, so without it one tap would permanently give a
        // character a level the book never gave them (Codex).
        s.setWealth(null)
        assertNull(s.edits.wealth, "clearing it puts the character back on the printed level")
        assertNull(saved().edits.wealth)
    }

    // --- drinking ----------------------------------------------------------

    @Test
    fun `the first potion is not rolled for`() {
        // p. 45: the PCR is made against "the drinker's digestion of their
        // current potion", and a mortal has none.
        val (s, _) = session()
        assertNull(s.drink(fool9, fixed(6)) { 10 })
        assertEquals(fool9, s.state)
    }

    @Test
    fun `a pathway is walked one potion at a time`() {
        val (s, _) = session()
        assertThrows<IllegalArgumentException> { s.drink(fool8, fixed(6)) { 10 } }
    }

    @Test
    fun `a failed roll loses the character to the Rampager`() {
        val (s, saved) = session(SavedSession(beyonder = fool9))
        val outcome = s.drink(fool8, fixed(6)) { 10 }!!  // 18 vs target 1 at 0%
        assertFalse(outcome.integrated)
        assertTrue(s.lost)
        assertEquals(fool9, s.state, "a lost character does not advance")
        assertTrue(saved().lost)
    }

    @Test
    fun `a failed roll charges no corruption`() {
        // The Rampager belongs to the GM; a CoR total on that sheet is
        // bookkeeping for a character who is no longer played.
        val (s, _) = session(SavedSession(beyonder = fool9))
        s.drink(fool8, fixed(6)) { 10 }
        assertEquals(0, s.cor)
    }

    @Test
    fun `advancing before full digestion charges the book's floor`() {
        // p. 45: "Advancing sequence without full digestion: 10+ CoR".
        val (s, saved) = session(SavedSession(beyonder = fool9.digested(90)))
        val outcome = s.drink(fool8, fixed(1)) { 20 }
        assertTrue(outcome!!.integrated)
        assertEquals(10, s.cor)
        assertEquals(10, saved().cor)
    }

    @Test
    fun `a fully digested advancement charges nothing`() {
        val (s, _) = session(SavedSession(beyonder = fool9.digested(100)))
        s.drink(fool8, fixed(1)) { 20 }
        assertEquals(0, s.cor)
    }

    /**
     * REGRESSION (Codex, pre-fix behaviour: no loss). The Abyss pathway lowers
     * Will at Sequences 9, 8 and 6, so a potion can drop the CoR ceiling onto
     * a total that was safe -- even when the advancement itself costs 0 CoR.
     * Checking the cap of the state being LEFT misses exactly this character.
     */
    @Test
    fun `a potion that lowers the ceiling onto the current total is lost`() {
        val abyss9 = BeyonderState("Abyss", 9)
        val abyss8 = BeyonderState("Abyss", 8)
        val (s, saved) = session(SavedSession(beyonder = abyss9.digested(100), cor = 8))
        // Will 9 before the potion, 8 after it: the drinker is inside their
        // ceiling until the moment they swallow.
        s.drink(abyss8, fixed(1)) { state -> if (state == abyss8) 8 else 9 }
        assertTrue(s.corrupted, "the new ceiling is 8 and the total is 8")
        assertTrue(saved().corrupted, "and the loss is latched, not recomputed")
    }

    @Test
    fun `a raised ceiling does not give a lost character back`() {
        // The mirror of the above, and the reason `corrupted` is stored: a
        // potion that RAISES Will must not resurrect anyone.
        val (s, _) = session(SavedSession(beyonder = fool9, cor = 10, corrupted = true))
        assertTrue(s.isLost)
        s.gainCor(-5, max = 20)
        assertTrue(s.corrupted, "nothing un-loses a character")
    }

    // --- corruption --------------------------------------------------------

    @Test
    fun `corruption is held inside zero and the maximum`() {
        val (s, _) = session(SavedSession(beyonder = fool9))
        s.gainCor(4, max = 10)
        assertEquals(4, s.cor)
        s.gainCor(-9, max = 10)
        assertEquals(0, s.cor)
    }

    @Test
    fun `reaching the maximum latches the loss`() {
        val (s, saved) = session(SavedSession(beyonder = fool9))
        s.gainCor(10, max = 10)
        assertTrue(s.corrupted)
        assertTrue(saved().corrupted)
    }

    @Test
    fun `a lost character gains no further corruption and digests nothing`() {
        val (s, _) = session(SavedSession(beyonder = fool9, cor = 10, corrupted = true))
        s.gainCor(3, max = 10)
        s.digest(20)
        assertEquals(10, s.cor)
        assertEquals(0, s.state?.digestion)
    }

    // --- pools -------------------------------------------------------------

    /**
     * REGRESSION (Codex, pre-fix behaviour: HP untouched). p. 42: "Every point
     * of FP spent below 0 also costs 1 HP." The tracker printed that rule
     * under the FP row and did not apply it.
     */
    @Test
    fun `spending FP below zero costs one HP per point`() {
        val (s, saved) = session()
        repeat(11) { s.spend(Pool.FP, 1, max = 9, maxCor = 10) }
        assertEquals(11, s.fpLost, "FP is at -2")
        assertEquals(2, s.hpLost)
        assertEquals(2, saved().hpLost)
    }

    @Test
    fun `stepping FP back up refunds the HP it charged`() {
        // A stepper the GM cannot undo is a worse tool than one that needs a
        // correction now and then.
        val (s, _) = session()
        repeat(11) { s.spend(Pool.FP, 1, max = 9, maxCor = 10) }
        repeat(2) { s.spend(Pool.FP, -1, max = 9, maxCor = 10) }
        assertEquals(9, s.fpLost)
        assertEquals(0, s.hpLost)
    }

    @Test
    fun `FP above zero costs no HP at all`() {
        val (s, _) = session()
        repeat(9) { s.spend(Pool.FP, 1, max = 9, maxCor = 10) }
        assertEquals(0, s.hpLost)
    }

    /**
     * REGRESSION (pre-fix behaviour: CoR untouched). p. 35: "Soul Debt: +1 CoR
     * per 1 point below 0". The SPI row printed that rule and did not apply it
     * -- the same defect Codex found on FP, one pool over.
     */
    @Test
    fun `spending SPI below zero buys one corruption per point`() {
        val (s, saved) = session()
        repeat(12) { s.spend(Pool.SPI, 1, max = 9, maxCor = 10) }
        assertEquals(12, s.spiSpent, "SPI is at -3")
        assertEquals(3, s.cor)
        assertEquals(3, saved().cor)
    }

    /**
     * REGRESSION (Codex, pre-fix behaviour: CoR refunded on the way back up).
     * FP's HP charge is symmetric because the book leaves it ordinary; this
     * one it does not -- "CoR from Soul Debt are permanent and require special
     * rituals (Chapter 6) to remove" (p. 41).
     */
    @Test
    fun `recovering SPI does not hand the Soul Debt corruption back`() {
        val (s, _) = session()
        repeat(12) { s.spend(Pool.SPI, 1, max = 9, maxCor = 10) }
        repeat(5) { s.spend(Pool.SPI, -1, max = 9, maxCor = 10) }
        assertEquals(7, s.spiSpent, "SPI is back above zero")
        assertEquals(3, s.cor, "and the debt it bought is still owed")
    }

    @Test
    fun `recovering SPI cannot take back corruption Soul Debt never charged`() {
        // cor is one total with no memory of what charged it, so a refund
        // would spend a ritual's corruption to pay for a pool going back up.
        val (s, _) = session(SavedSession(cor = 6))
        repeat(11) { s.spend(Pool.SPI, 1, max = 9, maxCor = 20) }
        assertEquals(8, s.cor, "6 from before, 2 from the debt")
        repeat(11) { s.spend(Pool.SPI, -1, max = 9, maxCor = 20) }
        assertEquals(8, s.cor)
    }

    @Test
    fun `SPI above zero buys no corruption at all`() {
        val (s, _) = session()
        repeat(9) { s.spend(Pool.SPI, 1, max = 9, maxCor = 10) }
        assertEquals(0, s.cor)
    }

    @Test
    fun `Soul Debt can corrupt the character, and coming back does not undo it`() {
        // SPI loss never kills (p. 35) but it can still end a character this
        // way, and the loss is latched like every other -- climbing back out
        // of debt is not a route out of being lost.
        val (s, _) = session()
        repeat(11) { s.spend(Pool.SPI, 1, max = 9, maxCor = 2) }
        assertEquals(2, s.cor)
        assertTrue(s.corrupted)
        repeat(5) { s.spend(Pool.SPI, -1, max = 9, maxCor = 2) }
        assertTrue(s.corrupted, "a latched loss is never withdrawn")
        assertEquals(2, s.cor)
    }

    @Test
    fun `a pool never rises above its maximum`() {
        val (s, _) = session()
        s.spend(Pool.HP, -5, max = 10, maxCor = 10)
        assertEquals(0, s.hpLost)
    }

    @Test
    fun `pools have no floor because the book's rules live below zero`() {
        val (s, _) = session()
        repeat(60) { s.spend(Pool.HP, 1, max = 10, maxCor = 10) }
        assertEquals(60, s.hpLost, "-50 HP is dead, and the tracker must be able to say so")
    }

    @Test
    fun `every mutation is written back`() {
        val (s, saved) = session()
        s.spend(Pool.SPI, 3, max = 9, maxCor = 10)
        assertEquals(3, saved().spiSpent)
        s.drink(fool9, fixed(6)) { 10 }
        assertEquals(fool9, saved().beyonder)
    }
}
