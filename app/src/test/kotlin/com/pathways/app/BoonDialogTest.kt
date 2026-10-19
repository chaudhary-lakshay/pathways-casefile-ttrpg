package com.pathways.app

import com.pathways.model.BoonState
import com.pathways.rules.Languages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoonDialogTest {

    private val broker = BoonState("Chaos Mist", 9)

    @Test
    fun `a prayer needs the skill, a patron and a rung to ask for`() {
        fun refusal(skill: Int? = 12, patron: String? = "Chaos Mist", held: BoonState? = null, next: BoonState? = broker, lost: Boolean = false) =
            boonRefusal(skill, patron, held, next, lost = lost)
        assertNull(refusal())
        // Ch. 7 II: the skill "has no default and cannot be substituted".
        assertTrue("Ritualistic Magic" in refusal(skill = null)!!)
        // A prayer to nobody was "granted" and granted nothing.
        assertTrue("patron" in refusal(patron = null, next = null)!!)
        // The last printed rung is held: the book records nothing further.
        val last = BoonState("Chaos Mist", 8)
        assertTrue("Sequence 8" in refusal(held = last, next = null)!!)
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
        assertEquals(standingRefusal(null, false), boonRefusal(null, null, null, null))
    }

    @Test
    fun `a prayer in a Broken tongue is refused`() {
        val broken = Languages.proficiencies(Languages.MYSTICAL).first { !Languages.canPerformRituals(it) }
        val refusal = boonRefusal(12, "Chaos Mist", null, broker, language = broken)
        assertTrue(broken.proficiency in refusal!!)
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
