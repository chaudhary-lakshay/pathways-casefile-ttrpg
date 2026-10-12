package com.pathways.app

import com.pathways.model.BeyonderState
import com.pathways.model.canDrink
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Hanged Man 9's "Choose one of these three drawbacks", stored (6he). */
class DrawbackChoiceTest {
    private val hanged9 = BeyonderState("Hanged Man", 9)

    @Test
    fun `a chosen drawback is in play, free, and saved`() {
        val (s, saved) = session(SavedSession(beyonder = hanged9))
        assertTrue(s.canChooseDrawback(plain(), "Paranoia"))
        s.chooseDrawback(plain(), "Paranoia")
        assertEquals("Paranoia", saved().beyonder?.drawback)
        assertTrue(s.traitsInPlay(plain()).any { it.name == "Paranoia" && it.cost == 0 })
        // ...and the character, which the budget prices, never holds it.
        assertTrue(s.characterFor(plain()).traits.none { it.name == "Paranoia" })
    }

    /**
     * The owner's case: a drawback bought in the builder credits points the
     * potion does not give. Chosen on top, the bought row would keep that
     * credit, so the choice is refused until the purchase is sold.
     */
    @Test
    fun `a drawback bought in the builder cannot also be chosen`() {
        val (s, _) = session(SavedSession(beyonder = hanged9))
        s.buyTrait("Paranoia")
        assertFalse(s.canChooseDrawback(plain(), "Paranoia"))
        assertThrows<IllegalArgumentException> { s.chooseDrawback(plain(), "Paranoia") }
        assertTrue(s.canChooseDrawback(plain(), "Hallucinations"))
    }

    @Test
    fun `only the block's three, and only on that pathway`() {
        val (s, _) = session(SavedSession(beyonder = hanged9))
        assertFalse(s.canChooseDrawback(plain(), "Bloodlust"))
        assertFalse(session(SavedSession(beyonder = fool9)).first.canChooseDrawback(plain(), "Paranoia"))
        assertFalse(session().first.canChooseDrawback(plain(), "Paranoia"))
    }

    @Test
    fun `a lost character chooses nothing`() {
        val (s, _) = session(SavedSession(beyonder = hanged9, lost = true))
        assertFalse(s.canChooseDrawback(plain(), "Paranoia"))
    }

    /** The screen builds the next potion; built without the choice, Drink vanished. */
    @Test
    fun `the next potion carries the choice, so Drink is still offered`() {
        val drunk = hanged9.copy(digestion = 100, drawback = "Paranoia")
        val eight = ContentLoader.pathways().single { it.pathway == "Hanged Man" && it.sequence == 8 }
        assertTrue(drunk.canDrink(potionOn(eight, drunk)))
        val (s, _) = session(SavedSession(beyonder = drunk))
        s.drink(potionOn(eight, drunk), fixed(1)) { 10 }
        assertEquals(BeyonderState("Hanged Man", 8, drawback = "Paranoia"), s.state)
    }
}
