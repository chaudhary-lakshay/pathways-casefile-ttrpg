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
 * Perceptual Splitting, book 1551: "All physical rolls at -2 during an
 * episode" (6gv). A state the GM declares on the sheet, as the all-rolls rows
 * are (6gs), but scoped: line 390 defines a physical roll as "any roll based
 * on ST, DX, or HT (attack rolls, dodging, climbing, lifting, etc.)".
 */
class PhysicalRollsClausesTest {
    private val splitting = listOf(Trait("Perceptual Splitting", 10, TraitKind.DISADVANTAGE))
    private fun key(attr: ControllingAttribute) = SkillKey("any", null, attr, Difficulty.AVERAGE)
    private fun values(mods: List<com.pathways.model.Modifier>) = mods.map { (it.effect as ModEffect.Flat).n }

    @Test
    fun `a physical roll is one based on ST, DX or HT`() {
        for (attr in listOf(Attr.ST, Attr.DX, Attr.HT)) {
            assertTrue(EffortBurn.isPhysical(key(ControllingAttribute.Buyable(attr))), attr.name)
        }
        val mental = listOf(
            ControllingAttribute.Buyable(Attr.IQ), ControllingAttribute.Will,
            ControllingAttribute.Per, ControllingAttribute.Spi,
        )
        for (attr in mental) assertFalse(EffortBurn.isPhysical(key(attr)), attr.toString())
    }

    /** Both directions: the row quotes its cell, and no other cell prints the phrase. */
    @Test
    fun `every cell printing a state on physical rolls is a row`() {
        val catalogue = ContentLoader.advantages() + ContentLoader.disadvantages()
        val printing = catalogue.filter { "physical rolls" in it.notes.lowercase() }.map { it.name }.toSet()
        assertEquals(printing, TraitClauses.physicalRollsClauseNames())
        for (row in TraitClauses.physicalRollsClauseRows()) {
            assertTrue(row.printed in catalogue.single { it.name == row.trait }.notes, row.printed)
            assertTrue(row.conditional, row.trait)
        }
    }

    @Test
    fun `an episode is one sheet chip and reaches a physical roll alone`() {
        val chip = TraitClauses.declaredStateClauses(splitting).single()
        assertEquals(-2, chip.value)
        val lit = setOf(chip.key)
        val now = TraitClauses.declaredStates(splitting, lit)
        assertEquals(0, now.all.total)
        assertNull(now.all.line)
        assertEquals(-2, now.physical.total)
        assertEquals("Perceptual Splitting -2", now.physical.line)
        assertEquals(listOf(-2), values(TraitClauses.declaredModifiers(splitting, lit, physical = true)))
        assertTrue(TraitClauses.declaredModifiers(splitting, lit, physical = false).isEmpty())
        assertTrue(TraitClauses.declaredModifiers(splitting, emptySet(), physical = true).isEmpty())
        assertTrue("Perceptual Splitting" in TraitEffects.known())
    }

    @Test
    fun `an all-rolls state is on both and sums with the episode on a physical roll`() {
        val both = splitting + Trait("Addiction: Laudanum", 10, TraitKind.DISADVANTAGE)
        val lit = TraitClauses.declaredStateClauses(both).map { it.key }.toSet()
        assertEquals(2, lit.size)
        val now = TraitClauses.declaredStates(both, lit)
        assertEquals(-2, now.all.total)
        assertEquals("Addiction: Laudanum -2", now.all.line)
        assertEquals(-4, now.physical.total)
        assertEquals(listOf(-2), values(TraitClauses.declaredModifiers(both, lit, physical = false)))
        assertEquals(listOf(-2, -2), values(TraitClauses.declaredModifiers(both, lit, physical = true)))
    }
}
