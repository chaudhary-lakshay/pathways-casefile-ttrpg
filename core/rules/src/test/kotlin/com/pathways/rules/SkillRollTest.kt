package com.pathways.rules

import com.pathways.model.Layer
import com.pathways.model.ModEffect
import com.pathways.model.ModTarget
import com.pathways.model.Modifier
import com.pathways.model.ModifierId
import com.pathways.model.ModifierSource
import com.pathways.model.Outcome
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * [SkillRoll] is the first production caller of [ModifierResolver]. These
 * tests exist to prove the wiring carries a real modifier through to the
 * target number, not merely that an empty list changes nothing.
 */
class SkillRollTest {

    private val sheet = Premades.byName("Occult Scholar")
    private val skill = sheet.skills.first()

    /** Every die shows [face], so a 3d6 total is deterministic. */
    private fun always(face: Int) = RandomSource { face }

    private fun situational(id: String, effect: ModEffect, target: ModTarget) = Modifier(
        id = ModifierId(id),
        source = ModifierSource.Situational(id),
        target = target,
        effect = effect,
        layer = Layer.SITUATIONAL,
    )

    @Test
    fun `an unmodified roll targets the skill's computed level`() {
        val out = SkillRoll.of(sheet, skill, always(4))

        assertEquals(sheet.levelFor(skill), out.baseTarget)
        assertEquals(out.baseTarget, out.target, "no modifiers must not move the target")
        assertEquals(12, out.roll?.total, "three dice showing 4")
        assertEquals(out.target, out.roll?.target)
        assertTrue(out.applied.isEmpty())
    }

    @Test
    fun `a penalty aimed at this skill moves the target and is reported`() {
        val dark = situational("darkness", ModEffect.Flat(-3), ModTarget.Skill(skill.key()))

        val out = SkillRoll.of(sheet, skill, always(4), mods = listOf(dark))

        assertEquals(sheet.levelFor(skill) - 3, out.target)
        assertEquals(listOf(ModifierId("darkness")), out.applied.single().ids)
        assertEquals(-3, out.applied.single().delta)
    }

    @Test
    fun `a modifier aimed at a different skill leaves this one alone`() {
        val other = sheet.skills.first { it.key() != skill.key() }
        val elsewhere = situational("elsewhere", ModEffect.Flat(-3), ModTarget.Skill(other.key()))

        val out = SkillRoll.of(sheet, skill, always(4), mods = listOf(elsewhere))

        assertEquals(sheet.levelFor(skill), out.target)
        assertTrue(out.applied.isEmpty())
    }

    @Test
    fun `a forbidden roll produces no dice at all`() {
        val forbidden = situational("sealed", ModEffect.Forbid, ModTarget.Skill(skill.key()))

        val out = SkillRoll.of(sheet, skill, always(4), mods = listOf(forbidden))

        assertTrue(out.forbidden)
        assertNull(out.roll, "a forbidden roll must not be rolled anyway and then hidden")
    }

    @Test
    fun `a require-critical roll still rolls but is flagged`() {
        val crit = situational("crit-only", ModEffect.RequireCrit, ModTarget.Skill(skill.key()))

        val out = SkillRoll.of(sheet, skill, always(4), mods = listOf(crit))

        assertTrue(out.requiresCritical)
        assertEquals(12, out.roll?.total, "RequireCrit constrains the reading, not the dice")
    }

    @Test
    fun `a modifier aimed at every roll still reaches this skill`() {
        // ModifierResolver matches targets by exact equality, so AllRolls does
        // not match Skill(key) on its own. If it silently missed, a global
        // penalty would read as no penalty at the table.
        val fog = situational("fog", ModEffect.Flat(-2), ModTarget.AllRolls)

        val out = SkillRoll.of(sheet, skill, always(4), mods = listOf(fog))

        assertEquals(sheet.levelFor(skill) - 2, out.target)
        assertEquals(-2, out.applied.single().delta)
    }

    @Test
    fun `require-critical demotes an ordinary success`() {
        val crit = situational("crit-only", ModEffect.RequireCrit, ModTarget.Skill(skill.key()))

        // Three twos is a 6: under the target, but nowhere near a critical.
        val out = SkillRoll.of(sheet, skill, always(2), mods = listOf(crit))

        assertEquals(Outcome.SUCCESS, out.roll?.outcome, "the dice did come in under the target")
        assertEquals(Outcome.FAILURE, out.effectiveOutcome, "but only a critical counts")
    }

    @Test
    fun `require-critical leaves a real critical alone`() {
        val crit = situational("crit-only", ModEffect.RequireCrit, ModTarget.Skill(skill.key()))

        val out = SkillRoll.of(sheet, skill, always(1), mods = listOf(crit))

        assertEquals(Outcome.CRITICAL_SUCCESS, out.effectiveOutcome)
    }

    @Test
    fun `the outcome comes from SuccessRoll rather than being recomputed`() {
        // Three ones is a 3: a critical success against any target.
        val out = SkillRoll.of(sheet, skill, always(1))

        assertEquals(Outcome.CRITICAL_SUCCESS, out.roll?.outcome)
        assertEquals(SuccessRoll.evaluate(3, out.target), out.roll)
    }

    @Test
    fun `the label carries the specialization when the skill has one`() {
        val specialized = Premades.all()
            .flatMap { it.skills }
            .first { it.specialization != null }

        assertTrue(specialized.label.endsWith(")"), "expected 'Name (Spec)', got ${specialized.label}")
        assertTrue(specialized.label.startsWith(specialized.name))
    }

    /**
     * The roll in two steps, so a screen can print the target and then roll
     * the number it printed (6eg's rule, which the three ledger rolls were
     * the last to break). [SkillRoll.at] is the two composed.
     */
    @Test
    fun `a roll is its target resolved and then rolled`() {
        val dark = situational("darkness", ModEffect.Flat(-3), ModTarget.Skill(skill.key()))
        val level = sheet.levelFor(skill)

        val target = SkillRoll.targetFor(skill.label, level, skill.key(), listOf(dark))

        assertEquals(level, target.baseTarget)
        assertEquals(level - 3, target.target)
        assertEquals(-3, target.applied.single().delta)
        assertEquals(
            SkillRoll.at(skill.label, level, skill.key(), always(4), listOf(dark)),
            SkillRoll.rollAgainst(target, always(4)),
        )
    }

    /**
     * Against the number HANDED over, never one resolved again: what moves
     * between the draw and the tap must not move the roll.
     */
    @Test
    fun `the dice are judged against the target the roll was handed`() {
        val printed = SkillTarget("Stealth", baseTarget = 12, target = 9, applied = emptyList(), forbidden = false, requiresCritical = false)

        val out = SkillRoll.rollAgainst(printed, always(3))

        assertEquals(9, out.roll?.total)
        assertEquals(9, out.roll?.target)
        assertEquals(Outcome.SUCCESS, out.roll?.outcome)
        assertEquals(0, out.roll?.margin)
        assertEquals(12, out.baseTarget)
        assertEquals(9, out.target)
    }

    @Test
    fun `a forbidden target draws no die`() {
        val sealed = SkillTarget("Stealth", baseTarget = 12, target = 12, applied = emptyList(), forbidden = true, requiresCritical = false)

        val out = SkillRoll.rollAgainst(sealed) { error("a forbidden roll drew a die") }

        assertTrue(out.forbidden)
        assertNull(out.roll)
    }
}
