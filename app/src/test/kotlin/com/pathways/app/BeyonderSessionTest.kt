package com.pathways.app

import com.pathways.model.Attr
import com.pathways.model.BeyonderState
import com.pathways.model.MAX_ATTRIBUTE
import com.pathways.model.MAX_SECONDARY_DELTA_MAGNITUDE
import com.pathways.rules.Corruption
import com.pathways.rules.Languages
import com.pathways.rules.Pool
import com.pathways.rules.PotionDigestion
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.PremadeSheet
import com.pathways.rules.PublishedValues
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

    private fun session(
        saved: SavedSession = SavedSession(),
        sheet: PremadeSheet? = null,
    ): Pair<BeyonderSession, () -> SavedSession> {
        var last = saved
        return BeyonderSession(saved, sheet) { last = it } to { last }
    }

    /** Every attribute at the book's 9, and IQ at [iq]. */
    private fun plain(iq: Int = 9) = PremadeSheet(
        "plain",
        Attr.entries.associateWith { if (it == Attr.IQ) iq else 9 },
        PublishedValues(),
    )

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
    fun `whatever the builder sells, every potion can be digested on top of it`() {
        // The pathway screen digests the BROWSED block while it draws, potion
        // or not, and a potion ADDS to what was bought: Twilight Giant's walk
        // is ST +11, so a bought ST 20 was an ST 31 the model cannot hold.
        // Each setter on its own session: both hold the WHOLE save to the
        // ceilings, so a secondary step after an attribute step would hide an
        // attribute setter that held nothing.
        val sheet = plain()
        val blocks = ContentLoader.pathways()
        val buys = listOf<(BeyonderSession) -> Unit>(
            { s -> Attr.entries.forEach { s.setAttribute(it, MAX_ATTRIBUTE) } },
            { s -> ContentLoader.secondaryCosts().forEach { s.setSecondary(it.key, MAX_SECONDARY_DELTA_MAGNITUDE) } },
        )
        for (buy in buys) {
            val (s, _) = session(sheet = sheet)
            buy(s)
            for (b in blocks) {
                PotionDigestion.digestThrough(s.characterFor(sheet), b.pathway, b.sequence, blocks)
            }
        }
    }

    @Test
    fun `no builder edit leaves a live character standing at maximum CoR`() {
        // "At maximum CoR: The character is permanently lost" (book line 3075): a
        // STATE, which the app latched only on a gain. Three edits lower the
        // ceiling -- Will steps, IQ (Will = IQ + steps) and selling Spiritual
        // Fortitude -- and each, from CoR 9 under a ceiling of 10, reaches 9.
        val edits = listOf<(BeyonderSession) -> Unit>(
            { it.setSecondary("will", -1) },
            { it.setAttribute(Attr.IQ, 9) },
            { it.sellTrait("Spiritual Fortitude") },
        )
        val starts = listOf(
            SavedEdits(),
            SavedEdits(),
            SavedEdits(secondaries = mapOf("will" to -3), traits = listOf("Spiritual Fortitude")),
        )
        for ((edit, start) in edits.zip(starts)) {
            val sheet = plain(iq = 10)
            val (s, _) = session(SavedSession(cor = 9, edits = start), sheet)
            edit(s)
            val ceiling = Corruption.maxFor(s.characterFor(sheet))
            assertTrue(s.isLost || s.cor < ceiling, "CoR ${s.cor} under a ceiling of $ceiling, and live")
        }
    }

    @Test
    fun `the ceiling may come down to one above the total and no further`() {
        // CoR 8 under 10: one Will sold leaves 9, live; a second would be 8.
        val sheet = plain(iq = 10)
        val (s, _) = session(SavedSession(cor = 8), sheet)
        assertTrue(s.canSetSecondary("will", -1))
        s.setSecondary("will", -1)
        assertFalse(s.canSetSecondary("will", -2))
        s.setSecondary("will", -2)
        assertEquals(-1, s.edits.secondaries["will"])
    }

    @Test
    fun `selling Spiritual Fortitude onto the total is refused, and the builder can say so`() {
        val sheet = plain(iq = 10)
        val start = SavedEdits(secondaries = mapOf("will" to -3), traits = listOf("Spiritual Fortitude"))
        val (s, _) = session(SavedSession(cor = 9, edits = start), sheet)
        assertFalse(s.canSellTrait("Spiritual Fortitude"))
        s.sellTrait("Spiritual Fortitude")
        assertEquals(listOf("Spiritual Fortitude"), s.edits.traits)
        val (calm, _) = session(SavedSession(cor = 2, edits = start), sheet)
        assertTrue(calm.canSellTrait("Spiritual Fortitude"))
    }

    @Test
    fun `a save already standing at its ceiling buys Will back on opening, and stays live`() {
        // Written before the builder refused it: CoR 9 under a ceiling of 9.
        // A CoR "-" tap there walked the character back out, so the book's
        // "permanently lost" was never reached; latching it instead would end
        // a character for a builder tap the app now refuses.
        val saved = SavedSession(cor = 9, edits = SavedEdits(secondaries = mapOf("will" to -1)))
        val (s, last) = session(saved, plain(iq = 10))
        assertFalse(s.isLost)
        assertTrue(s.edits.secondaries["will"] == null, "bought back to a ceiling of 10")
        assertEquals(s.edits, last().edits, "and written back")
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

    @Test
    fun `a Beyonder's ceiling is read from the potions drunk`() {
        // Abyss 9 lowers Will to 9: selling one more Will would leave CoR 8
        // at 8 / 8. Read off the undigested Will (10) it is 8 / 9, and safe.
        val sheet = plain(iq = 10)
        val (s, _) = session(SavedSession(beyonder = BeyonderState("Abyss", 9), cor = 8), sheet)
        assertFalse(s.canSetSecondary("will", -1))
    }

    @Test
    fun `a lost save is not bought back on opening`() {
        val start = SavedEdits(secondaries = mapOf("will" to -1))
        val (s, _) = session(SavedSession(lost = true, cor = 9, edits = start), plain(iq = 10))
        assertEquals(start, s.edits)
    }

    @Test
    fun `a save the purchase ceiling cannot buy back above its total is lost`() {
        // IQ 26 and Will +98, both at their purchase ceilings: maximum CoR
        // 124, sold onto a CoR of 124 before the builder refused it. Will
        // cannot pass +98, so 124 stands at the maximum -- the book's answer
        // is "lost". The edits come back UNCHANGED, so only the latch says
        // the save must be written.
        val saved = SavedSession(cor = 124, edits = SavedEdits(iq = 26, secondaries = mapOf("will" to 98)))
        val (s, last) = session(saved, plain(iq = 10))
        assertTrue(s.isLost)
        assertTrue(last().corrupted, "and written back")
    }

    @Test
    fun `a save bought past a ceiling is brought down to it on opening, before the floors`() {
        // Written before the ceilings existed: ST 25 is ST 36 on Twilight
        // Giant's walk. Bringing ST down to 19 takes HP (ST + steps) from 5 to
        // -1, so the floors must be asked AFTER the ceilings, or the repair
        // leaves a save the sheet throws on.
        val saved = SavedSession(edits = SavedEdits(st = 25, secondaries = mapOf("hp" to -20, "per" to 100)))
        val (s, last) = session(saved, plain())
        val expected = SavedEdits(st = 19, secondaries = mapOf("hp" to -18, "per" to 98))
        assertEquals(expected, s.edits)
        assertEquals(expected, last().edits, "the repair is written back, not redone on every open")
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

    // --- score floors -------------------------------------------------------
    //
    // REGRESSION, reproduced on an SM-T570: the builder sold HP a hundred
    // steps, and a maximum of 0 killed the sheet screen while it was DRAWN
    // (`Pools.hp`). The save kept the number, so the premade died on every open.

    @Test
    fun `a step that would leave HP below one is refused`() {
        val (s, saved) = session(sheet = plain())
        s.setSecondary("hp", -8)
        s.setSecondary("hp", -9)
        assertEquals(mapOf("hp" to -8), s.edits.secondaries)
        assertEquals(mapOf("hp" to -8), saved().edits.secondaries)
        assertFalse(s.canSetSecondary("hp", -9))
        assertTrue(s.canSetSecondary("hp", -8))
    }

    @Test
    fun `the builder's question is asked of the edit the setter would make`() {
        // REGRESSION (SM-T570, this slice's own first build): the builder
        // built its candidate itself, asked about ST 8 at the book's floor of
        // 9, and `Attributes` refuses an 8 by throwing -- in composition. The
        // setter coerces to 9 first; the question must too.
        val (s, _) = session(sheet = plain(iq = 10))
        assertTrue(s.canSetAttribute(Attr.ST, 8), "coerced to 9: no change, nothing refused")
        s.setSecondary("will", -9)
        assertFalse(s.canSetAttribute(Attr.IQ, 9))
        assertTrue(s.canSetSecondary("will", MAX_SECONDARY_DELTA_MAGNITUDE + 50), "coerced like the setter")
    }

    @Test
    fun `lowering IQ is refused when it takes the CoR ceiling below one`() {
        // Will is IQ plus the steps bought, so an attribute step moves it
        // without the Will row ever being touched.
        val (s, _) = session(sheet = plain(iq = 10))
        s.setSecondary("will", -9)
        s.setAttribute(Attr.IQ, 9)
        assertNull(s.edits.iq, "Will 10 - 9 = 1; IQ 9 would leave a ceiling of 0")
    }

    @Test
    fun `selling Spiritual Fortitude is refused when the ceiling rests on it`() {
        // Fortitude: "Maximum CoR equal Will + 3", so Will -2 under it is a
        // ceiling of 1 -- and of -2 the moment it is sold.
        val (s, _) = session(sheet = plain())
        s.buyTrait("Spiritual Fortitude")
        s.setSecondary("will", -11)
        assertEquals(mapOf("will" to -11), s.edits.secondaries)
        s.sellTrait("Spiritual Fortitude")
        assertTrue("Spiritual Fortitude" in s.edits.traits)
    }

    @Test
    fun `a save already below a floor is bought back to it when the session opens`() {
        // The tablet's own save: Abyss Sequence 9 grants +2 HP and -1 Will.
        // The floors hold on the BOUGHT scores as well as the digested ones,
        // so HP -11 (bought 9 - 11 = -2) comes back to -8, and Will -9 to -8
        // and then -7 for the potion's -1.
        val (s, saved) = session(
            SavedSession(
                beyonder = BeyonderState("Abyss", 9, 100),
                edits = SavedEdits(secondaries = mapOf("hp" to -11, "will" to -9, "per" to -3)),
            ),
            sheet = plain(),
        )
        assertEquals(mapOf("hp" to -8, "will" to -7, "per" to -3), s.edits.secondaries)
        assertEquals(s.edits, saved().edits, "the repair is written back, not recomputed on every read")
    }

    @Test
    fun `Basic Speed and Basic Move are not sold below zero`() {
        // The tablet read Speed -20.50 and Move -100 with 908 points spare.
        val (s, _) = session(sheet = plain())
        s.setSecondary("basicMove", -4)
        s.setSecondary("basicMove", -5)
        assertEquals(mapOf("basicMove" to -4), s.edits.secondaries)
        // 4.25 still rounds down to 4; 3.75 would leave Move at -1.
        assertTrue(s.canSetSecondary("basicSpeed", -1))
        assertFalse(s.canSetSecondary("basicSpeed", -3))
        s.setSecondary("basicMove", 0)
        s.setSecondary("basicSpeed", -18)
        assertFalse(s.canSetSecondary("basicSpeed", -19))
        assertFalse(s.canSetSecondary("basicMove", -1), "Move's base is 0 once Speed is")
        // Speed has a floor of its own: Move bought back on top of a negative
        // Speed stands at 0 and would otherwise carry the sale.
        s.setSecondary("basicMove", 1)
        assertFalse(s.canSetSecondary("basicSpeed", -19))
    }

    @Test
    fun `lowering DX or HT is refused when it takes Basic Speed below zero`() {
        val (s, _) = session(sheet = plain())
        s.setAttribute(Attr.DX, 10)
        s.setSecondary("basicSpeed", -19)
        assertEquals(mapOf("basicSpeed" to -19), s.edits.secondaries)
        assertFalse(s.canSetAttribute(Attr.DX, 9))
        s.setAttribute(Attr.DX, 9)
        assertEquals(10, s.edits.dx)
    }

    @Test
    fun `a save with Speed and Move sold to the bound opens at zero and is not charged twice`() {
        val (s, saved) = session(
            SavedSession(
                edits = SavedEdits(
                    secondaries = mapOf(
                        "basicSpeed" to -MAX_SECONDARY_DELTA_MAGNITUDE,
                        "basicMove" to -MAX_SECONDARY_DELTA_MAGNITUDE,
                    ),
                ),
            ),
            sheet = plain(),
        )
        // Move comes back to what was bought on top of a base of 0: nothing.
        assertEquals(mapOf("basicSpeed" to -18), s.edits.secondaries)
        assertEquals(s.edits, saved().edits)
    }

    @Test
    fun `a save at the bound is repaired, not overflowed by the potion`() {
        // Codex, on the diff: Will -100 plus Abyss's -1 is a SecondaryDelta of
        // -101, which its constructor refuses -- inside the repair meant to
        // rescue that save.
        val (s, _) = session(
            SavedSession(
                beyonder = BeyonderState("Abyss", 9, 100),
                edits = SavedEdits(secondaries = mapOf("will" to -MAX_SECONDARY_DELTA_MAGNITUDE)),
            ),
            sheet = plain(),
        )
        assertEquals(mapOf("will" to -7), s.edits.secondaries)
    }

    @Test
    fun `a save inside the floors is left alone`() {
        var writes = 0
        BeyonderSession(SavedSession(edits = SavedEdits(secondaries = mapOf("hp" to -8))), plain()) { writes++ }
        assertEquals(0, writes)
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
     * Three names and no price, for the reason a bought trait stores a name:
     * the cost is the book's, and it moves when Language Talent is bought or
     * sold. A language is owned once, so buying an owned one again CHANGES it
     * in place -- the position is what Multilingual's "first two" reads.
     */
    @Test
    fun `a language is changed in place and given back by name`() {
        val (s, saved) = session()
        val hermes = SavedLanguage("Hermes", "Broken", Languages.BOTH_FORMS)
        val loen = SavedLanguage("Loen", "Native", Languages.BOTH_FORMS)
        s.buyLanguage(hermes)
        s.buyLanguage(loen)
        s.buyLanguage(hermes.copy(proficiency = "Native"))
        assertEquals(listOf(hermes.copy(proficiency = "Native"), loen), saved().edits.languages)

        s.sellLanguage("Elvish")
        assertEquals(2, saved().edits.languages.size, "a language not owned changes nothing")
        s.sellLanguage("Hermes")
        assertEquals(listOf(loen), saved().edits.languages)
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
