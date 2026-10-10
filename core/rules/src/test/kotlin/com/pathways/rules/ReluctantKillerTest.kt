package com.pathways.rules

import com.pathways.model.ModEffect
import com.pathways.model.ModifierSource
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Reluctant Killer's cell, book lines 1313-1314: *"-4 to hit recognizable
 * people with deadly force (-2 if face hidden); cannot Aim. If you kill a
 * recognizable person, become morose for 3d days — Will rolls required to
 * use violence again."*
 */
class ReluctantKillerTest {

    private val killer = listOf(Trait(TraitEffects.RELUCTANT_KILLER, 5, TraitKind.DISADVANTAGE))
    private val cell = ContentLoader.disadvantages()
        .single { it.name == TraitEffects.RELUCTANT_KILLER }.notes

    @Test
    fun `the two to-hit clauses are offered to a holder alone, as printed`() {
        val clauses = TraitClauses.attackClauses(killer)
        assertEquals(listOf(-4, -2), clauses.map { it.value })
        for (clause in clauses) {
            assertTrue(cell.contains(clause.printed), clause.printed)
            assertTrue(clause.conditional)
        }
        assertEquals(emptyList<Any>(), TraitClauses.attackClauses(emptyList()))
    }

    /**
     * One declaration, because the parenthetical is the same penalty at a
     * lower value: a hidden face is still a recognizable person. Two keys
     * would let one attack take -6.
     */
    @Test
    fun `a declared clause is the one term on the attack, and nothing undeclared is`() {
        assertEquals(emptyList<Any>(), TraitClauses.attackModifiers(killer, declared = null))
        for (clause in TraitClauses.attackClauses(killer)) {
            val mod = TraitClauses.attackModifiers(killer, clause.key).single()
            assertEquals(ModEffect.Flat(clause.value), mod.effect)
            assertTrue(mod.source is ModifierSource.FromTrait, "${mod.source}")
        }
    }

    @Test
    fun `a declaration the character was not offered is refused`() {
        val key = TraitClauses.attackClauses(killer).first().key
        assertThrows<IllegalArgumentException> { TraitClauses.attackModifiers(emptyList(), key) }
        assertThrows<IllegalArgumentException> { TraitClauses.attackModifiers(killer, "not a clause") }
    }

    /**
     * **Unconditional, the literal reading.** "cannot Aim" follows the
     * semicolon with no qualifier of its own, and the design review's
     * MEDIUM was that scoping it to the declared target is a gate the cell
     * does not print. A quirk of the same name is not the drawback.
     */
    @Test
    fun `a holder cannot aim, declared or not`() {
        assertTrue(cell.contains("cannot Aim"))
        assertTrue(TraitClauses.refusesAim(killer))
        assertFalse(TraitClauses.refusesAim(emptyList()))
        assertFalse(
            TraitClauses.refusesAim(listOf(Trait(TraitEffects.RELUCTANT_KILLER, 1, TraitKind.QUIRK))),
        )
    }

    @Test
    fun `the sheet names the morose days as the one clause left`() {
        assertTrue(TraitEffects.RELUCTANT_KILLER in TraitEffects.known())
        val note = TraitReport.reported(killer).single()
        assertEquals("become morose for 3d days", note.printed)
        assertTrue(cell.contains(note.printed))
    }
}
