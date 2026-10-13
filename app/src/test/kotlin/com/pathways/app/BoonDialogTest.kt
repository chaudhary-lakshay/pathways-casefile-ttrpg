package com.pathways.app

import com.pathways.model.BoonState
import com.pathways.rules.Boons
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoonDialogTest {

    @Test
    fun `an ordinary target is printed plainly`() {
        assertEquals("3d6 ≤ 8", targetLine(8))
        assertFalse("still succeeds" in targetLine(4))
    }

    @Test
    fun `a hopeless-looking target still says what can beat it`() {
        // REGRESSION (Codex): the boon-level doubling drives the target far
        // below 3 at the low sequences -- Sequence 0 is -512 -- and the line
        // read "3d6 <= -512", which says impossible. It is not: SuccessRoll
        // treats a rolled 3 or 4 as a critical success whatever the target,
        // and this chapter prints no override of that.
        val line = targetLine(Boons.penaltyForSequence(0))
        assertTrue("-512" in line)
        assertTrue("3 or 4 still succeeds" in line)
        assertTrue("still succeeds" in targetLine(3))
    }

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

    @Test
    fun `the sheet names the held boon by its rung`() {
        assertEquals("Boon · Broker · Seq 9", boonPill(broker))
        // Across Eternal Aeon's two spellings: the Sequence 8 is ch. 15's.
        assertEquals("Boon · Alms Monk · Seq 8", boonPill(BoonState("Eternal Aeon (Inevitability)", 8)))
        // A rung the book no longer prints still says what the save holds.
        assertEquals("Boon · Unprinted · Seq 4", boonPill(BoonState("Unprinted", 4)))
    }
}
