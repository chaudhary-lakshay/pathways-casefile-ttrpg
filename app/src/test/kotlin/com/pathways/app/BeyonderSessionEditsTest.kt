package com.pathways.app

import com.pathways.model.Attr
import com.pathways.model.BeyonderState
import com.pathways.model.MAX_ATTRIBUTE
import com.pathways.model.MAX_SECONDARY_DELTA_MAGNITUDE
import com.pathways.rules.Corruption
import com.pathways.rules.Languages
import com.pathways.rules.PotionDigestion
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The builder's side of [BeyonderSession]: what a purchase stores, what a
 * setter refuses, and what a save is repaired to when it opens.
 *
 * A test is here when it calls none of the session's play members (`drink`,
 * `spend`, `gainCor`, `digest`, `rerollPcr`, `stillShows`, `onSamePotion`,
 * `traitsInPlay`); those are [BeyonderSessionTest]'s.
 */
class BeyonderSessionEditsTest {

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

    @Test
    fun `a second Give back of the same picture sells nothing`() {
        // Two taps reach the handler of the list as drawn (6ei), and by then
        // position 0 holds the neighbour: it was sold too.
        val (s, saved) = session()
        val patients = SavedInstancedTrait("Sense of Duty", instance = "Patients", cost = 10)
        val coworkers = SavedInstancedTrait("Sense of Duty", instance = "Coworkers", cost = 5)
        s.buyInstancedTrait(patients)
        s.buyInstancedTrait(coworkers)

        s.sellInstancedTrait(0, patients)
        s.sellInstancedTrait(0, patients)
        assertEquals(listOf(coworkers), saved().edits.instanced)
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

        s.sellInstancedTrait(0, patients)
        assertEquals(listOf(coworkers), saved().edits.instanced)
        s.sellInstancedTrait(7, coworkers)
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
        // Giant's walk. Bringing ST down to 17 (the ceiling leaves room for a
        // boon too) takes HP (ST + steps) from 5 to -3, so the floors must be asked AFTER the ceilings, or the repair
        // leaves a save the sheet throws on.
        val saved = SavedSession(edits = SavedEdits(st = 25, secondaries = mapOf("hp" to -20, "per" to 100)))
        val (s, last) = session(saved, plain())
        val expected = SavedEdits(st = 17, secondaries = mapOf("hp" to -16, "per" to 98))
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
}
