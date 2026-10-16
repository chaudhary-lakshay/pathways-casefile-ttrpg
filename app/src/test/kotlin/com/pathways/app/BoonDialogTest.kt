package com.pathways.app

import com.pathways.model.BoonState
import com.pathways.rules.RandomSource
import com.pathways.rules.Languages
import com.pathways.rules.Boons
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoonDialogTest {

    private val broker = BoonState("Chaos Mist", 9)

    @Test
    fun `a prayer needs the skill, a patron, a rung to ask for and its fuel`() {
        fun refusal(skill: Int? = 12, patron: String? = "Chaos Mist", held: BoonState? = null, next: BoonState? = broker, spi: Int = 9, lost: Boolean = false) =
            boonRefusal(skill, patron, held, next, cost = 4, spiAvailable = spi, lost = lost)
        assertNull(refusal())
        // Ch. 7 II: the skill "has no default and cannot be substituted".
        assertTrue("Ritualistic Magic" in refusal(skill = null)!!)
        // A prayer to nobody was "granted" and granted nothing.
        assertTrue("patron" in refusal(patron = null, next = null)!!)
        // The last printed rung is held: the book records nothing further.
        val last = BoonState("Chaos Mist", 8)
        assertTrue("Sequence 8" in refusal(held = last, next = null)!!)
        assertTrue("4 SPI" in refusal(spi = 3)!!)
        // The session offers a lost character no rung: say why, not "no boon".
        assertTrue("GM" in refusal(next = null, lost = true)!!)
    }

    /**
     * Play session 3: a Scholar with no Ritualistic Magic filled in the whole
     * petition before the bottom line said no. The two refusals no pick in
     * the dialog can lift lead it (the owner, 2026-10-16); the rest are steps.
     */
    @Test
    fun `only a refusal no pick can lift leads the dialog`() {
        assertTrue("Ritualistic Magic" in standingRefusal(skillLevel = null, lost = false)!!)
        assertTrue("GM" in standingRefusal(skillLevel = 12, lost = true)!!)
        assertNull(standingRefusal(skillLevel = 12, lost = false))
        // The same words the footer prints: one copy.
        assertEquals(standingRefusal(null, false), boonRefusal(null, null, null, null, cost = 4, spiAvailable = 9))
    }

    @Test
    fun `blood pays for a prayer that SPI cannot`() {
        // Ch. 13: "a mortal may acquire a boon", by "the standard Ritualistic
        // Magic system"; ch. 7 IV.B: HP "in addition to or instead of SPI",
        // "Every 2 HP spent counts as 1 SPI". A mortal plays SPI 0.
        fun refusal(spi: Int, hp: Int) =
            boonRefusal(12, "Chaos Mist", null, broker, cost = 4, spiAvailable = spi, hpSacrificed = hp)
        assertNull(refusal(spi = 0, hp = 8))
        assertNull(refusal(spi = 2, hp = 4))
        // 6 HP covers 3 of the 4: the line names what is still owed.
        assertTrue("costs 1 SPI" in refusal(spi = 0, hp = 6)!!)
        // And the refusal says where the fuel can come from.
        assertTrue("HP" in refusal(spi = 0, hp = 0)!!)
    }

    @Test
    fun `a prayer in a Broken tongue is refused, and a paid one is not asked for its fuel again`() {
        val broken = Languages.proficiencies(Languages.MYSTICAL).first { !Languages.canPerformRituals(it) }
        val refusal = boonRefusal(12, "Chaos Mist", null, broker, cost = 4, spiAvailable = 9, language = broken)
        assertTrue(broken.proficiency in refusal!!)
        // Asked and charged: the 4 SPI are gone, and that is not a refusal.
        assertNull(boonRefusal(12, "Chaos Mist", null, broker, cost = 4, spiAvailable = 0, paid = true))
    }

    @Test
    fun `the patron is asked before the prayer, and two answers end it`() {
        fun asked(face: Int) = Boons.ask(RandomSource { face })
        assertTrue("Ask" in answerRefusal(null)!!)
        assertNull(answerRefusal(asked(1)))
        assertTrue("rejected" in answerRefusal(asked(6))!!)
        assertTrue("did not answer" in answerRefusal(asked(4))!!)
    }

    @Test
    fun `a granted row says what gave it`() {
        assertEquals("the potion", grantedBy(potion = true, boon = false))
        assertEquals("the boon", grantedBy(potion = false, boon = true))
        assertEquals("the potion or the boon", grantedBy(potion = true, boon = true))
    }

    @Test
    fun `the sheet names the held boon by its rung`() {
        assertEquals("Boon · Broker · Seq 9", boonPill(broker))
        // Across Eternal Aeon's two spellings: the Sequence 8 is ch. 15's.
        assertEquals("Boon · Alms Monk · Seq 8", boonPill(BoonState("Eternal Aeon (Inevitability)", 8)))
        // A rung the book no longer prints still says what the save holds.
        assertEquals("Boon · Unprinted · Seq 4", boonPill(BoonState("Unprinted", 4)))
    }
}
