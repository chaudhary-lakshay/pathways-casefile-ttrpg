package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Charmed Object's stored skill (6hk; book 1055-1058): *"One item you own
 * provides +1 to one specific skill when used (specify item and skill)."*
 */
class CharmedObjectTest {
    private val dx = ControllingAttribute.Buyable(Attr.DX)
    private val pistol = SkillKey("Guns", "Pistol", dx, Difficulty.EASY)
    private val rifle = SkillKey("Guns", "Rifle", dx, Difficulty.EASY)
    private val stealth = SkillKey("Stealth", null, dx, Difficulty.AVERAGE)
    private fun held(name: String) = listOf(Trait(name, 5, TraitKind.ADVANTAGE))
    private val charmedPistol = held(CharmedObject.heldAs("Guns", "Pistol"))

    @Test
    fun `the held name carries the skill and still is the book's row`() {
        assertEquals("Charmed Object (Guns (Pistol))", CharmedObject.heldAs("Guns", "Pistol"))
        assertEquals("Charmed Object (Stealth)", CharmedObject.heldAs("Stealth", null))
        assertEquals("Charmed Object", TraitNames.catalogueName("Charmed Object (Guns (Pistol))"))
        assertEquals("Charmed Object", TraitNames.catalogueName("Charmed Object (Stealth)"))
        // An empty bracket names nothing, and another row's bracket is not this row's.
        assertEquals("Charmed Object ()", TraitNames.catalogueName("Charmed Object ()"))
        assertEquals("Obsession (A rival)", TraitNames.catalogueName("Obsession (A rival)"))
    }

    @Test
    fun `a held charm is the row already bought, so it is not sold twice`() {
        val row = PurchasableTraits.named(CharmedObject.ROW)!!
        assertTrue(PurchasableTraits.canBuy(row, emptyList()))
        assertFalse(PurchasableTraits.canBuy(row, charmedPistol))
    }

    @Test
    fun `the clause is the cell's own words`() {
        val cell = ContentLoader.advantages().single { it.name == CharmedObject.ROW }.notes
        assertTrue(CharmedObject.CLAUSE in cell, cell)
        assertTrue(CharmedObject.DESTROYED in cell, cell)
    }

    @Test
    fun `the chip is offered on the chosen skill and on no other`() {
        val clause = TraitClauses.skillRollClauses(charmedPistol, pistol).single()
        assertEquals(CharmedObject.ROW, clause.trait)
        assertEquals("Charmed Object (Guns (Pistol))", clause.shown)
        assertEquals(CharmedObject.CLAUSE, clause.printed)
        assertEquals(1, clause.value)
        assertTrue(clause.conditional)

        assertEquals(emptyList<TraitClause>(), TraitClauses.skillRollClauses(charmedPistol, rifle))
        assertEquals(emptyList<TraitClause>(), TraitClauses.skillRollClauses(charmedPistol, stealth))
        // No skill chosen: nothing to offer anywhere.
        assertEquals(emptyList<TraitClause>(), TraitClauses.skillRollClauses(held("Charmed Object"), pistol))
        // A skill with no specialization is matched whole, not as a prefix.
        val charmedGuns = held(CharmedObject.heldAs("Guns", null))
        assertEquals(emptyList<TraitClause>(), TraitClauses.skillRollClauses(charmedGuns, pistol))
    }

    /**
     * The carrier is a name and holds no difficulty, so a charm on Knife
     * reaches Knife at either difficulty the book prints it at. A reading,
     * stated: the cell says "one specific skill" and names no difficulty.
     */
    @Test
    fun `one name at two difficulties is charmed at both`() {
        val charmed = held(CharmedObject.heldAs("Knife", null))
        for (difficulty in listOf(Difficulty.EASY, Difficulty.AVERAGE)) {
            val knife = SkillKey("Knife", null, dx, difficulty)
            assertEquals(1, TraitClauses.skillRollClauses(charmed, knife).size, difficulty.name)
        }
    }

    @Test
    fun `the plus one reaches the dice only when the table declares it`() {
        val key = TraitClauses.skillRollClauses(charmedPistol, pistol).single().key
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(charmedPistol, pistol))
        val lit = TraitEffects.modifiers(charmedPistol, pistol, setOf(key)).single()
        assertEquals(ModEffect.Flat(1), lit.effect)
        // Declared, and rolled on another skill: nothing.
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(charmedPistol, rifle, setOf(key)))
    }

    @Test
    fun `the census holds the row and the report says what is left`() {
        assertTrue(CharmedObject.ROW in TraitEffects.known())
        val chosen = TraitReport.reported(charmedPistol)
        assertEquals(listOf(CharmedObject.CLAUSE, CharmedObject.DESTROYED), chosen.map { it.printed })
        assertEquals(CharmedObject.REACH, chosen.first().reason)
        // Read after the sheet's "Not applied:", so it names what is NOT reached.
        assertTrue(CharmedObject.REACH.startsWith("on a parry"), CharmedObject.REACH)
        assertEquals(TraitReport.Applied.PARTLY, tagOf(charmedPistol))
    }

    /** Seen on the SM-T570: an unchosen row read "Partly applied" with nothing applied. */
    @Test
    fun `a charm with no skill chosen applies nothing, and its tag says so`() {
        val cell = ContentLoader.advantages().single { it.name == CharmedObject.ROW }.notes
        val bare = TraitReport.reported(held("Charmed Object")).single()
        assertEquals(cell, bare.printed)
        assertEquals(CharmedObject.UNCHOSEN, bare.reason)
        assertEquals(TraitReport.Applied.GM, tagOf(held("Charmed Object")))
    }

    private fun tagOf(traits: List<Trait>) =
        TraitReport.applied(TraitReport.held(traits, TraitKind.ADVANTAGE).single())

    @Test
    fun `the skill read back is the one stored`() {
        assertEquals("Guns (Pistol)", CharmedObject.skillOf("Charmed Object (Guns (Pistol))"))
        assertNull(CharmedObject.skillOf("Charmed Object"))
        assertNull(CharmedObject.skillOf("Secret (Guns)"))
    }
}
