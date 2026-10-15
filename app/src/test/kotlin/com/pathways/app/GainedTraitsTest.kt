package com.pathways.app

import com.pathways.model.Attributes
import com.pathways.model.Character
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * A disadvantage gained in play ([SavedEdits.gained]): held, its rules
 * applied, billed at nothing. Split out of [EditedCharacterTest], which had
 * grown past the 800-line cap.
 */
class GainedTraitsTest {

    private val mortal = Character(name = "test", attributes = Attributes(iq = 10))

    /**
     * Play session 1: a failed Fright Check's "New -10 pt disadvantage" could
     * only be entered as a purchase, which paid the character 10 points. One
     * marked gained in play is held -- its rules still reach the rolls -- and
     * billed at nothing.
     */
    @Test
    fun `a disadvantage gained in play is held and bills no points`() {
        val phobia = SavedInstancedTrait("Phobia (specify)", instance = "Candles", cost = 10)
        val bought = SavedEdits(instanced = listOf(phobia)).applyTo(mortal)
        val gained = SavedEdits(instanced = listOf(phobia), gained = listOf("Phobia (Candles)")).applyTo(mortal)
        assertEquals(10, bought.traits.single { it.name == "Phobia (Candles)" }.cost)
        assertEquals(0, gained.traits.single { it.name == "Phobia (Candles)" }.cost)
        assertEquals(TraitKind.DISADVANTAGE, gained.traits.single { it.name == "Phobia (Candles)" }.kind)
        // An advantage cannot be gained free this way: the mark is for the
        // fright table's disadvantages.
        val luck = SavedEdits(traits = listOf("Luck"), gained = listOf("Luck")).applyTo(mortal)
        assertNotEquals(0, luck.traits.single { it.name == "Luck" }.cost)
    }

    /**
     * The mark is a bought row's, never the sheet's (the diff review): a sheet
     * printing the same name keeps its price while the bought twin bills 0.
     */
    @Test
    fun `a gained mark never zeroes the sheet's own trait of that name`() {
        val printed = mortal.copy(traits = listOf(Trait("Phobia (Candles)", 10, TraitKind.DISADVANTAGE)))
        val edited = SavedEdits(
            instanced = listOf(SavedInstancedTrait("Phobia (specify)", instance = "Candles", cost = 10)),
            gained = listOf("Phobia (Candles)"),
        ).applyTo(printed)
        assertEquals(listOf(10, 0), edited.traits.filter { it.name == "Phobia (Candles)" }.map { it.cost })
    }
}
