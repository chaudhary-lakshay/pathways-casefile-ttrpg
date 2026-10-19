package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.ModifierSource
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The held rows whose cell prints "-N to all rolls" under a circumstance the
 * GM declares (6gs): one sheet-level state, the role shock already plays.
 */
class AllRollsClausesTest {
    private fun held(vararg names: String) = names.map { Trait(it, 10, TraitKind.DISADVANTAGE) }

    @Test
    fun `every all-rolls number is still the one its row prints`() {
        val byName =
            (ContentLoader.advantages() + ContentLoader.disadvantages()).associateBy { it.name }
        for (clause in TraitClauses.allRollsClauseRows()) {
            val notes = byName.getValue(clause.trait).notes
            assertTrue(clause.printed in notes, "${clause.trait}: ${clause.printed}")
            assertTrue(Regex("${clause.value} to (all rolls|any roll)").containsMatchIn(clause.printed), clause.printed)
            assertTrue(clause.conditional, clause.trait)
        }
    }

    /**
     * The other direction: every catalogue cell printing a signed "to all
     * rolls" is either a row here or one whose phrase continues with a
     * purpose ("to all rolls to diagnose", "to influence"), which names no
     * state at all.
     */
    @Test
    fun `every cell printing a state on all rolls is a row`() {
        val state = Regex("""[-+]\d+ to all rolls(?! to )""")
        val printing = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .filter { state.containsMatchIn(it.notes) }
            .map { it.name }
            .toSet()
        // Reckless prints "-1 to any roll where caution would be smarter",
        // which this filter does not read; it is a row on the owner's ruling
        // (6he, question 4). Entity Fixation's "-2 to any roll that requires
        // you to ignore them" stays refused: its row is here for the -1 alone.
        assertEquals(printing + "Reckless", TraitClauses.allRollsClauseNames())
        val fixation = TraitClauses.allRollsClauseRows().filter { it.trait == "Entity Fixation" }
        assertEquals(listOf(-1), fixation.map { it.value })
    }

    /** Declared like the five, and its self-control roll is still offered. */
    @Test
    fun `Reckless declares its -1 on every roll`() {
        val clause = TraitClauses.allRollsClauses(held("Reckless")).single()
        assertEquals(-1, clause.value)
        assertEquals("-1 to any roll where caution would be smarter", clause.printed)
        assertEquals(-1, TraitClauses.clauseBonus(listOf(clause), setOf(clause.key)).total)
        assertTrue(SelfControl.checksFor(held("Reckless")).isNotEmpty())
    }

    @Test
    fun `a premade's phobia offers its all-rolls clause under its own spelling`() {
        val clause = TraitClauses.allRollsClauses(held("Phobia (Cities)")).single()
        assertEquals(-1, clause.value)
        assertEquals("Phobia (Cities)", clause.shown)
        // The ledger line names it as the sheet does, never "Phobia (specify)":
        // found on the device, where every other screen said "(Cities)".
        val source = TraitClauses.allRollsModifiers(listOf(clause), setOf(clause.key)).single().source
        assertEquals("Phobia (Cities)", source.label())
        assertEquals(false, (source as ModifierSource.FromTrait).advantage)
    }

    /**
     * Two phobias are two stimuli, so two chips and two terms (the diff
     * review: a dedupe on the catalogue row dropped the second). Their ledger
     * modifiers must not share an id.
     */
    @Test
    fun `two held phobias are two declarations`() {
        val clauses = TraitClauses.allRollsClauses(held("Phobia (Cities)", "Phobia (Heights)"))
        assertEquals(listOf("Phobia (Cities)", "Phobia (Heights)"), clauses.map { it.shown })
        val all = clauses.map { it.key }.toSet()
        assertEquals(-2, TraitClauses.clauseBonus(clauses, all).total)
        val mods = TraitClauses.allRollsModifiers(clauses, all)
        assertEquals(2, mods.map { it.id }.toSet().size)
        // A row held twice under ONE spelling is still one declaration.
        assertEquals(1, TraitClauses.allRollsClauses(held("Phobia (Cities)", "Phobia (Cities)")).size)
    }

    @Test
    fun `declared states sum and reach a skill roll as trait modifiers`() {
        val clauses = TraitClauses.allRollsClauses(held("Addiction: Laudanum", "Soul Scar"))
        assertEquals(2, clauses.size)
        val all = clauses.map { it.key }.toSet()
        assertEquals(-3, TraitClauses.clauseBonus(clauses, all).total)
        assertEquals(0, TraitClauses.clauseBonus(clauses, emptySet()).total)
        val laudanum = clauses.single { it.trait == "Addiction: Laudanum" }
        val mods = TraitClauses.allRollsModifiers(clauses, setOf(laudanum.key))
        assertEquals(listOf(-2), mods.map { (it.effect as ModEffect.Flat).n })
        val stealth = SkillKey("Stealth", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE)
        val target = SkillRoll.targetFor("Stealth", 12, stealth, mods)
        assertEquals(10, target.target)
    }

    @Test
    fun `the six rows are known and the refusals they carried are gone`() {
        val names = TraitClauses.allRollsClauseNames()
        assertEquals(6, names.size)
        assertTrue(names.all { it in TraitEffects.known() }, names.toString())
        // Phobia's refusal said "no control for a second all-rolls state";
        // Soul Scar's said "nothing here to roll or to modify". Both expired.
        assertTrue("Soul Scar" !in TraitRefusals.UNAPPLIED)
        val phobia = TraitRefusals.PARTLY_APPLIED["Phobia (specify)"].orEmpty().map { it.first }
        assertTrue(phobia.none { "all rolls" in it }, phobia.toString())
    }

    /**
     * Every engine that composes a bare target takes the state as its own
     * term, so a breakdown names it rather than counting it as shock or as
     * "hurt or tired".
     */
    @Test
    fun `every engine composing a bare target takes the state as its own term`() {
        val states = ClauseBonus(-2, "Addiction: Laudanum -2")
        assertEquals(
            Detection.targetFor(spi = 0, per = 10) - 2,
            Detection.targetFor(spi = 0, per = 10, states = states),
        )
        assertTrue("Addiction: Laudanum -2" in Detection.partsFor(spi = 0, per = 10, states = states))
        assertEquals(
            ActiveDefenses.everyDefence(emptyList(), stunned = false, poolPenalty = 0) - 2,
            ActiveDefenses.everyDefence(emptyList(), stunned = false, poolPenalty = 0, states = -2),
        )
        for (check in VitalCheck.entries) {
            assertEquals(
                VitalRolls.targetFor(check, 10, emptyList(), 0) - 2,
                VitalRolls.targetFor(check, 10, emptyList(), 0, states = -2),
            )
        }
        assertEquals(
            NaturalRecovery.targetFor(10, emptyList(), 0, 0) - 2,
            NaturalRecovery.targetFor(10, emptyList(), 0, 0, states = -2),
        )
    }
}
