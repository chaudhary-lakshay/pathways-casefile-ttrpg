package com.pathways.app

import com.pathways.model.Attr
import com.pathways.rules.TraitEffects
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** God of Combat in the builder and on the sheet (6hf). */
class BlessingSessionTest {
    private val combat = TraitEffects.GOD_OF_COMBAT

    @Test
    fun `a mortal holder plays ST, DX and HT three higher`() {
        val (s, _) = session(sheet = plain())
        val before = s.characterFor(plain()).attributesWith(null)
        s.buyTrait(combat)
        val after = s.characterFor(plain()).attributesWith(null)
        assertEquals(before.st + 3, after.st)
        assertEquals(before.dx + 3, after.dx)
        assertEquals(before.iq, after.iq)
        assertEquals(before.ht + 3, after.ht)
        // Bought ST is unchanged: the budget bills the trait alone.
        assertEquals(before.st, s.characterFor(plain()).attributes.st)
    }

    /** Refused, never clamped: the purchase would leave ST above the holder's 14. */
    @Test
    fun `God of Combat is refused while a bought score sits above the holder's ceiling`() {
        val (s, _) = session(sheet = plain())
        s.setAttribute(Attr.ST, 15)
        assertNotNull(s.buyRefusal(combat))
        s.buyTrait(combat)
        assertFalse(combat in s.edits.traits)
        assertEquals(15, s.characterFor(plain()).attributes.st)
        s.setAttribute(Attr.ST, 14)
        assertNull(s.buyRefusal(combat))
        s.buyTrait(combat)
        assertTrue(combat in s.edits.traits)
        // ...and a holder cannot step past it.
        s.setAttribute(Attr.ST, 15)
        assertEquals(14, s.characterFor(plain()).attributes.st)
    }

    /** A save holding both from before 6hf is brought inside on open, as any over-ceiling save is. */
    @Test
    fun `the ceilings read the blessing among the bought traits`() {
        val held = SavedEdits(st = 19, traits = listOf(combat)).withinCeilings()
        assertEquals(14, held.st)
        assertEquals(17, SavedEdits(st = 19).withinCeilings().st)
    }

    @Test
    fun `an attribute row a blessing moved says bought, not before potions`() {
        assertEquals("9 bought", boughtNote(bought = "9", played = "12"))
        assertNull(boughtNote(bought = "9", played = "9"))
    }
}
