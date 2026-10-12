package com.pathways.app

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.Character
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.CharmedObject
import com.pathways.rules.TraitClauses
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Charmed Object's skill, from the save to the Attack roll (6hk). */
class CharmedSkillTest {
    private val mortal = Character(name = "test", attributes = Attributes(iq = 10))
    private val pistol = SkillKey("Guns", "Pistol", ControllingAttribute.Buyable(Attr.DX), Difficulty.EASY)
    private val chosen = SavedCharmedSkill("Guns", "Pistol")
    private fun names(edits: SavedEdits) = edits.applyTo(mortal).traits.map { it.name }

    @Test
    fun `a bought charm takes its skill into the trait's name, at the row's price`() {
        val edits = SavedEdits(traits = listOf(CharmedObject.ROW), charmedSkill = chosen)
        assertEquals(
            listOf(Trait("Charmed Object (Guns (Pistol))", 5, TraitKind.ADVANTAGE)),
            edits.applyTo(mortal).traits,
        )
    }

    @Test
    fun `a charm with no skill chosen is the bare row, and a skill with no charm is nothing`() {
        assertEquals(listOf("Charmed Object"), names(SavedEdits(traits = listOf(CharmedObject.ROW))))
        assertEquals(emptyList<String>(), names(SavedEdits(charmedSkill = chosen)))
        // The choice names no other bought row.
        assertEquals(
            listOf("Luck", "Charmed Object (Guns (Pistol))"),
            names(SavedEdits(traits = listOf("Luck", CharmedObject.ROW), charmedSkill = chosen)),
        )
    }

    @Test
    fun `a stored skill the book does not print leaves the row bare`() {
        val edits = SavedEdits(traits = listOf(CharmedObject.ROW), charmedSkill = SavedCharmedSkill("Juggling Geese"))
        assertEquals(listOf("Charmed Object"), names(edits))
    }

    @Test
    fun `the builder says which skill is charmed, that none is, or that it is not held`() {
        val held = listOf(pistol)
        assertEquals("No skill chosen: no roll offers its +1.", charmLine(null, held))
        assertEquals("Charms Guns (Pistol): +1 when used, lit on the roll.", charmLine(chosen, held))
        assertEquals(
            "Charms Guns (Rifle), which this character has no level in.",
            charmLine(SavedCharmedSkill("Guns", "Rifle"), held),
        )
    }

    @Test
    fun `a potion's skill can be charmed, as the Skills tab lists it`() {
        // Seen on the SM-T570: a Fool 9's picker left out Ritualistic Magic.
        fun names(state: com.pathways.model.BeyonderState?) = charmableSkills(mortal, state).map { it.name }
        assertEquals(false, "Divination Arts" in names(null))
        assertEquals(true, "Divination Arts" in names(fool9))
        // A sheet's case is the roll's: the builder's line agrees with it.
        assertEquals(
            "Charms Guns (Pistol): +1 when used, lit on the roll.",
            charmLine(chosen, listOf(pistol.copy(specialization = "pistol"))),
        )
    }

    @Test
    fun `the choice is stored, switched, and survives the row being given back`() {
        val (s, saved) = session()
        s.buyTrait(CharmedObject.ROW)
        assertNull(saved().edits.charmedSkill)
        s.setCharmedSkill(pistol)
        s.setCharmedSkill(pistol)
        assertEquals(chosen, saved().edits.charmedSkill)
        s.setCharmedSkill(pistol.copy(name = "Stealth", specialization = null))
        assertEquals(SavedCharmedSkill("Stealth"), saved().edits.charmedSkill)
        s.sellTrait(CharmedObject.ROW)
        assertEquals(SavedCharmedSkill("Stealth"), saved().edits.charmedSkill)
    }

    @Test
    fun `a save written before the choice existed still loads, and one with it comes back`() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString<SavedEdits>("""{"traits":["Charmed Object"]}""")
        assertNull(old.charmedSkill)
        val edits = SavedEdits(traits = listOf(CharmedObject.ROW), charmedSkill = chosen)
        assertEquals(edits, json.decodeFromString<SavedEdits>(json.encodeToString(SavedEdits.serializer(), edits)))
    }

    @Test
    fun `a charmed weapon's plus one reaches the attack only when lit`() {
        val held = SavedEdits(traits = listOf(CharmedObject.ROW), charmedSkill = chosen).applyTo(mortal).traits
        val key = TraitClauses.skillRollClauses(held, pistol).single().key
        fun flats(declared: Set<String>) =
            traitTerms(held, pistol, killerTarget = null, declared = declared).map { (it.effect as ModEffect.Flat).n }
        assertEquals(emptyList<Int>(), flats(emptySet()))
        assertEquals(listOf(1), flats(setOf(key)))
        // The Attack dialog offers the charm's chip and no other skill clause.
        assertEquals(key, charmFor(held, pistol)?.key)
        assertNull(charmFor(held, pistol.copy(specialization = "Rifle")))
    }
}
