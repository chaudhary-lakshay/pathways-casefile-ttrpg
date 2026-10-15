package com.pathways.app

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.Derive
import com.pathways.rules.Premades
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A quirk acquired in play: the Fright table's 13-15 band, "Acquire a quirk:
 * mild phobia or superstition" (play session 2, which had nowhere to write
 * it). Gained in play, so it bills nothing, as 6hs's gained disadvantage.
 */
class QuirksTest {

    private val blank = Premades.byName(BLANK_SHEET)

    @Test
    fun `a recorded quirk is held at no cost and the budget does not move`() {
        val base = blank.toCharacter()
        val played = SavedEdits(quirks = listOf("Superstitious about mirrors")).applyTo(base)
        assertEquals(listOf(Trait("Superstitious about mirrors", 0, TraitKind.QUIRK)), played.traits - base.traits.toSet())
        assertEquals(Derive.of(base).budget.spent, Derive.of(played).budget.spent)
    }

    @Test
    fun `a quirk is recorded once, trimmed, and can be given back`() {
        val (session, last) = session()
        session.addQuirk("  Fears rats ")
        session.addQuirk("Fears rats")
        session.addQuirk("   ")
        assertEquals(listOf("Fears rats"), last().edits.quirks)
        session.removeQuirk("Fears rats")
        assertEquals(emptyList<String>(), last().edits.quirks)
    }

    @Test
    fun `reset clears a quirk play added`() {
        val played = SavedSession(edits = SavedEdits(quirks = listOf("Fears rats")), basedOn = BLANK_SHEET)
        assertEquals(SavedSession(basedOn = BLANK_SHEET), played.cleared())
    }
}
