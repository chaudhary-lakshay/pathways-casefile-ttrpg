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

    /**
     * One answer to "what does this character hold" for the sheet AND the
     * builder (6fk's diff review): a Hermit 8's potion gave Combat Reflexes,
     * so the builder must not sell it again, and the sheet applies it.
     */
    @Test
    fun `a potion's rows are in play and cannot be bought again`() {
        val (s, _) = session(SavedSession(beyonder = BeyonderState("Hermit", 8)))
        val held = s.traitsInPlay(plain())
        assertTrue(held.any { it.name == "Combat Reflexes" && it.cost == 0 }, held.toString())
        val row = com.pathways.rules.PurchasableTraits.offered().single { it.name == "Combat Reflexes" }
        assertFalse(com.pathways.rules.PurchasableTraits.canBuy(row, held))
        // ...and the character itself, which the budget prices, never holds it.
        assertTrue(s.characterFor(plain()).traits.none { it.name == "Combat Reflexes" })
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
    fun `a hurt drinker rolls the table's target and no carried state`() {
        // p. 45: "Only the drinker's digestion of their current potion
        // matters". Reeling's "-1 to all rolls" is general and that sentence
        // is specific: a 12 against the 60% row's 12 integrates, where the
        // same 12 against 11 would end the character. HP and FP lost are
        // the two states a session holds, and the sheet is what gives them a
        // maximum: plain()'s 9 leaves this drinker reeling and below 0 FP.
        // Shock and the declared states live on the sheet screen, and drink
        // takes no parameter for them.
        val (s, _) = session(SavedSession(beyonder = fool9.digested(60), hpLost = 8, fpLost = 20), plain())
        val outcome = s.drink(fool8, fixed(4)) { 20 }!!
        assertEquals(12, outcome.target)
        assertTrue(outcome.integrated)
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

    @Test
    fun `a first potion that lowers the ceiling onto the total latches the loss`() {
        // A mortal can carry CoR (a ritual's). Abyss Sequence 9 is Will -1, so
        // CoR 9 under Will 10 lands at 9 / 9: a potion, an event in play, so
        // the loss latches -- and opening the save must not buy it back.
        val sheet = plain(iq = 10)
        val (s, last) = session(SavedSession(cor = 9), sheet)
        val abyss9 = BeyonderState("Abyss", 9)
        s.drink(abyss9, fixed(10)) { s.edits.corCeilingOn(sheet, it) }
        assertTrue(s.isLost)
        val (reopened, _) = session(last(), sheet)
        assertTrue(reopened.isLost)
        assertTrue(reopened.edits.isEmpty, "a loss is not repaired with a Will purchase")
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

    @Test
    fun `Drink is refused before the roll when the potion would take the ceiling below one`() {
        // A mortal at Will 1 drinking Abyss Sequence 9 lands at Will 0. The
        // book's arithmetic has no answer the engine can compute on, so the
        // app refuses the potion -- and checks before anything is spent.
        val untouched = RandomSource { error("rolled before refusing") }
        val (mortal, saved) = session(SavedSession())
        assertThrows<IllegalArgumentException> { mortal.drink(BeyonderState("Abyss", 9), untouched) { 0 } }
        assertNull(mortal.state)
        assertEquals(SavedSession(), saved())

        val (walker, _) = session(SavedSession(beyonder = fool9))
        assertThrows<IllegalArgumentException> { walker.drink(fool8, untouched) { 0 } }
        assertEquals(fool9, walker.state)
        assertFalse(walker.lost)
    }

    /**
     * Luck: "may reroll any one failed roll" (book line 1039; 6fg). A failed
     * PCR is the one failed roll this session makes permanent, so the potion
     * it attempted is SAVED, and a restart does not strand the reroll.
     */
    @Test
    fun `a failed roll can be rerolled once, from the save`() {
        val (s, saved) = session(SavedSession(beyonder = fool9.digested(90)))
        assertFalse(s.drink(fool8, fixed(6)) { 20 }!!.integrated)  // 18 vs 9
        assertEquals(fool8, saved().pendingPcr)

        val (back, savedBack) = session(saved())
        val again = back.rerollPcr(fixed(1)) { 20 }  // 3 vs 9
        assertTrue(again.integrated)
        assertFalse(back.lost)
        assertEquals(fool8, back.state)
        assertEquals(10, back.cor, "the same floor a first success charges")
        assertNull(savedBack().pendingPcr)
        assertFalse(savedBack().lost)
        assertThrows<IllegalArgumentException> { back.rerollPcr(fixed(1)) { 20 } }
    }

    @Test
    fun `a reroll that fails changes nothing, and is spent`() {
        val (s, saved) = session(SavedSession(beyonder = fool9.digested(90)))
        s.drink(fool8, fixed(6)) { 20 }
        assertFalse(s.rerollPcr(fixed(6)) { 20 }.integrated)
        assertTrue(s.lost)
        assertEquals(fool9.digested(90), s.state)
        assertEquals(0, s.cor)
        assertNull(saved().pendingPcr)
        assertThrows<IllegalArgumentException> { s.rerollPcr(fixed(1)) { 20 } }
    }

    @Test
    fun `nothing is rerolled without a failed roll to reroll`() {
        val (s, _) = session(SavedSession(beyonder = fool9))
        assertThrows<IllegalArgumentException> { s.rerollPcr(fixed(1)) { 20 } }
    }

    /** A ceiling lowered since the failure refuses the reroll and keeps it (Codex). */
    @Test
    fun `a reroll the ceiling refuses is kept for later`() {
        val (s, saved) = session(SavedSession(beyonder = fool9.digested(90)))
        s.drink(fool8, fixed(6)) { 20 }
        assertThrows<IllegalArgumentException> { s.rerollPcr(fixed(1)) { 0 } }
        assertEquals(fool8, s.pendingPcr)
        assertEquals(fool8, saved().pendingPcr)
        assertTrue(s.rerollPcr(fixed(1)) { 20 }.integrated)
    }
}
