package com.pathways.app

import com.pathways.model.BeyonderState
import com.pathways.model.MAX_SECONDARY_DELTA_MAGNITUDE
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

    /**
     * **Given back by INDEX, never by name.** Two purchases of one row are
     * legal, and a bare ranged row composes to the book's own name for both,
     * so "Sense of Duty" can name two rows on screen and could not say which
     * to remove. An exact repeat is refused on the way in, the same way a
     * double tap on any list is.
     */
    @Test
    fun `an instanced purchase is given back by position`() {
        val (s, saved) = session()
        val patients = SavedInstancedTrait("Sense of Duty", instance = "Patients", cost = 10)
        val coworkers = SavedInstancedTrait("Sense of Duty", instance = "Coworkers", cost = 5)
        s.buyInstancedTrait(patients)
        s.buyInstancedTrait(coworkers)
        s.buyInstancedTrait(patients)
        assertEquals(listOf(patients, coworkers), saved().edits.instanced)

        s.sellInstancedTrait(0)
        assertEquals(listOf(coworkers), saved().edits.instanced)
        s.sellInstancedTrait(7)
        assertEquals(
            listOf(coworkers),
            saved().edits.instanced,
            "an index off the end changes nothing",
        )
    }

    /**
     * **The same trait at two prices is still the same trait.** Composition
     * keys a purchase on row, variant and instance and NOT on cost, so saving
     * both would bill the first while the builder listed two -- and giving the
     * first back would silently change what the survivor costs (Codex).
     */
    @Test
    fun `the same purchase at a different price is refused`() {
        val (s, saved) = session()
        s.buyInstancedTrait(SavedInstancedTrait("Secret", instance = "A body", cost = 10))
        s.buyInstancedTrait(SavedInstancedTrait("Secret", instance = "A body", cost = 30))
        assertEquals(1, saved().edits.instanced.size)
        assertEquals(10, saved().edits.instanced.single().cost, "the first price stands")
    }

    @Test
    fun `every mutation is written back`() {
        val (s, saved) = session()
        s.spend(Pool.SPI, 3, max = 9, maxCor = 10)
        assertEquals(3, saved().spiSpent)
        s.drink(fool9, fixed(6)) { 10 }
        assertEquals(fool9, saved().beyonder)
    }

    @Test
    fun `a bought secondary is stored by the book's row key, and zero is the way back`() {
        val (s, saved) = session()
        s.setSecondary("hp", 2)
        s.setSecondary("basicSpeed", 1)
        assertEquals(mapOf("hp" to 2, "basicSpeed" to 1), s.edits.secondaries)
        assertEquals(mapOf("hp" to 2, "basicSpeed" to 1), saved().edits.secondaries)

        // Back to what the sheet prints leaves no entry behind: a stored zero
        // would keep the save looking edited forever. Cleared with a SECOND
        // row saved, so emptying the whole map would fail here (Codex).
        s.setSecondary("hp", 0)
        assertEquals(mapOf("basicSpeed" to 1), s.edits.secondaries)
        s.setSecondary("basicSpeed", 0)
        assertTrue(s.edits.secondaries.isEmpty())
        assertTrue(s.edits.isEmpty)
    }

    @Test
    fun `a step past the model's bound is coerced at the setter`() {
        val (s, _) = session()
        s.setSecondary("hp", 10_000)
        assertEquals(MAX_SECONDARY_DELTA_MAGNITUDE, s.edits.secondaries["hp"])
        s.setSecondary("will", -10_000)
        assertEquals(-MAX_SECONDARY_DELTA_MAGNITUDE, s.edits.secondaries["will"])
    }

    @Test
    fun `a weapon is carried once however it is spelled, and dropping it leaves the others`() {
        val (s, saved) = session()
        s.carryWeapon("Broadsword")
        // The catalogue's nine rows carry nine distinct names, so a repeat is
        // not a second weapon -- it would print a second identical parry row.
        s.carryWeapon("broadsword")
        s.carryWeapon("Combat Knife")
        assertEquals(listOf("Broadsword", "Combat Knife"), s.edits.weapons)
        assertEquals(listOf("Broadsword", "Combat Knife"), saved().edits.weapons)

        // Dropped by a spelling the save does not use: both calls using the
        // canonical name let a case-SENSITIVE comparison pass (Codex).
        s.dropWeapon("BROADSWORD")
        assertEquals(listOf("Combat Knife"), s.edits.weapons)
        // A weapon that is not carried is not an error and writes nothing.
        s.dropWeapon("Hand Axe")
        assertEquals(listOf("Combat Knife"), s.edits.weapons)
        s.dropWeapon("Combat Knife")
        assertTrue(s.edits.isEmpty)
    }

    // --- acting on what the screen printed ----------------------------------

    /**
     * Drink is the one irreversible button, and its handler belongs to the
     * composition that drew it. If the session has moved since -- a second
     * tap, a CoR step, a lost roll -- the screen is showing a past the GM is
     * no longer in, and the tap must do nothing rather than act on it.
     */
    @Test
    fun `a screen drawn before the session moved no longer shows the truth`() {
        val (s, _) = session(SavedSession(beyonder = fool9.digested(100)))
        val printed = s.state
        val printedCor = s.cor
        assertTrue(s.stillShows(printed, printedCor))

        // The potion's own tracker. Drink must notice; a CoR step must not,
        // because the ceiling it was drawn against has not moved.
        s.digest(-5)
        assertFalse(s.stillShows(printed, printedCor))
        assertTrue(s.onSamePotion(printed))
        s.digest(5)
        assertTrue(s.stillShows(printed, printedCor), "back where the screen drew it")

        // CoR: it prices an early advancement, so Drink compares it. The CoR
        // steppers do not, or the second of two quick taps would be dropped.
        s.gainCor(1, 10)
        assertFalse(s.stillShows(printed, printedCor))
        assertTrue(s.onSamePotion(printed), "the second of two quick CoR taps still counts")
        s.gainCor(-1, 10)

        // The second of two taps on Drink: the first one advanced the state,
        // and the second would be refused by drink itself -- by throwing.
        assertTrue(s.drink(fool8, fixed(1)) { 10 }!!.integrated)
        assertFalse(s.stillShows(printed, printedCor))
        assertFalse(s.onSamePotion(printed), "the ceiling may have moved with the potion")
        assertThrows<IllegalArgumentException> { s.drink(fool8, fixed(1)) { 10 } }
    }

    @Test
    fun `a mortal's screen shows the truth until the first potion`() {
        // `printed` is null for a character who has drunk nothing, and that
        // is a picture like any other: refusing it would mean nobody could
        // ever drink a first potion.
        val (s, _) = session()
        assertTrue(s.stillShows(null, s.cor))
        s.drink(fool9, fixed(6)) { 10 }
        assertFalse(s.stillShows(null, s.cor))
    }

    @Test
    fun `a lost character's screen shows nothing to act on`() {
        // A failed roll leaves `state` exactly where it was, so comparing the
        // potion alone would let a second tap through to a require.
        val (s, _) = session(SavedSession(beyonder = fool9))
        val printed = s.state
        assertFalse(s.drink(fool8, fixed(6)) { 10 }!!.integrated)
        assertEquals(printed, s.state)
        assertFalse(s.stillShows(printed, s.cor))
        assertThrows<IllegalArgumentException> { s.drink(fool8, fixed(6)) { 10 } }

        // Lost to corruption, with no roll failed: `lost` is false and the
        // character is gone all the same.
        val (c, _) = session(SavedSession(beyonder = fool9))
        c.gainCor(10, 10)
        assertFalse(c.lost)
        assertTrue(c.isLost)
        assertFalse(c.stillShows(c.state, c.cor))
        assertFalse(c.onSamePotion(c.state))
    }
}
