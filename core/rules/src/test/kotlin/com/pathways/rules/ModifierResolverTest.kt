package com.pathways.rules

import com.pathways.model.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ModifierResolverTest {

    private val ctx = Context(isNight = true, isDarkness = false)
    private val target = ModTarget.AllRolls

    private fun mod(
        id: String, effect: ModEffect, layer: Layer = Layer.SITUATIONAL,
        group: StackGroup? = null, supersedes: String? = null,
        condition: Condition = Condition.Always,
    ) = Modifier(
        id = ModifierId(id), source = ModifierSource.Situational(id), target = target,
        effect = effect, layer = layer, group = group,
        supersedes = supersedes?.let { ModifierId(it) }, condition = condition,
    )

    @Test
    fun `flat modifiers sum within a layer`() {
        val r = ModifierResolver.resolve(12, listOf(
            mod("a", ModEffect.Flat(-2)), mod("b", ModEffect.Flat(-1)),
        ), target, ctx)
        assertEquals(9, r.value)
    }

    @Test
    fun `SetTo supersedes the modifier it names - the Midnight Poet case`() {
        // Seq 9 grants +1 at night. Seq 8 says the bonus "increases to +2
        // (instead of +1)". A flat sum would wrongly give +3.
        val seq9 = mod("seq9-night", ModEffect.Flat(1), Layer.POTION)
        val seq8 = mod("seq8-night", ModEffect.SetTo(2), Layer.POTION, supersedes = "seq9-night")
        assertEquals(14, ModifierResolver.resolve(12, listOf(seq9, seq8), target, ctx).value)
    }

    @Test
    fun `a stack group is capped at its maximum`() {
        val support = StackGroup("divination-support", cap = 2)
        val r = ModifierResolver.resolve(12, listOf(
            mod("occultism", ModEffect.Flat(1), group = support),
            mod("hidden-lore", ModEffect.Flat(1), group = support),
            mod("psychology", ModEffect.Flat(1), group = support),
            mod("cogitation", ModEffect.Flat(1), group = support),
        ), target, ctx)
        assertEquals(14, r.value)   // +4 raw, capped to +2
    }

    @Test
    fun `Cancel removes penalties up to its budget and no further`() {
        val r = ModifierResolver.resolve(12, listOf(
            mod("fright", ModEffect.Flat(-5), Layer.SITUATIONAL),
            mod("bard", ModEffect.Cancel(3, SourceKind.SITUATIONAL), Layer.ADVANTAGE),
        ), target, ctx)
        assertEquals(10, r.value)   // -5 reduced by 3
    }

    @Test
    fun `Cancel does not become a bonus when there is nothing to cancel`() {
        val r = ModifierResolver.resolve(12, listOf(
            mod("bard", ModEffect.Cancel(3, SourceKind.SITUATIONAL), Layer.ADVANTAGE),
        ), target, ctx)
        assertEquals(12, r.value)
    }

    @Test
    fun `Scale rounds down`() {
        assertEquals(8, ModifierResolver.resolve(10, listOf(
            mod("encumbered", ModEffect.Scale(4, 5)),   // x0.8
        ), target, ctx).value)
    }

    @Test
    fun `Decay reduces by one per elapsed turn and never past zero`() {
        val m = mod("gauntlet", ModEffect.Decay(from = -3, perTurn = 1))
        assertEquals(9,  ModifierResolver.resolve(12, listOf(m), target, ctx.copy(turn = 0)).value)
        assertEquals(10, ModifierResolver.resolve(12, listOf(m), target, ctx.copy(turn = 1)).value)
        assertEquals(12, ModifierResolver.resolve(12, listOf(m), target, ctx.copy(turn = 3)).value)
        assertEquals(12, ModifierResolver.resolve(12, listOf(m), target, ctx.copy(turn = 9)).value)
    }

    @Test
    fun `Forbid cannot be bought off by bonuses`() {
        val r = ModifierResolver.resolve(12, listOf(
            mod("anti-divination", ModEffect.Forbid),
            mod("huge-bonus", ModEffect.Flat(12)),
        ), target, ctx)
        assertTrue(r.forbidden)
    }

    @Test
    fun `RequireCrit is reported without changing the target number`() {
        val r = ModifierResolver.resolve(12, listOf(mod("warded", ModEffect.RequireCrit)), target, ctx)
        assertEquals(12, r.value)
        assertTrue(r.requiresCritical)
    }

    @Test
    fun `conditional modifiers do not apply when their condition is false`() {
        val r = ModifierResolver.resolve(12, listOf(
            mod("night-only", ModEffect.Flat(2), condition = Condition.AtNight),
            mod("dark-only", ModEffect.Flat(3), condition = Condition.InDarkness),
        ), target, ctx)   // night, but not darkness
        assertEquals(14, r.value)
    }

    @Test
    fun `modifiers aimed at another target are ignored`() {
        val elsewhere = Modifier(
            id = ModifierId("other"), source = ModifierSource.Situational("other"),
            target = ModTarget.Skill(SkillKey("Stealth", null, Attr.DX, Difficulty.AVERAGE)),
            effect = ModEffect.Flat(5), layer = Layer.SITUATIONAL,
        )
        assertEquals(12, ModifierResolver.resolve(12, listOf(elsewhere), target, ctx).value)
    }

    @Test
    fun `an inactive superseder does not suppress the modifier it names`() {
        // Seq 9 grants +1 unconditionally. Seq 8's "SetTo +2" only applies at
        // night and supersedes Seq 9. During the day, Seq 8 is inactive, so
        // it must not suppress Seq 9: 12 + 1 = 13.
        val dayCtx = Context(isNight = false, isDarkness = false)
        val seq9 = mod("seq9-flat", ModEffect.Flat(1), Layer.POTION)
        val seq8 = mod(
            "seq8-setto", ModEffect.SetTo(2), Layer.POTION,
            supersedes = "seq9-flat", condition = Condition.AtNight,
        )
        assertEquals(13, ModifierResolver.resolve(12, listOf(seq9, seq8), target, dayCtx).value)
    }

    @Test
    fun `a superseder aimed at another target does not suppress across targets`() {
        val seq9 = mod("seq9-general", ModEffect.Flat(1), Layer.POTION)
        val seq8ForSkill = Modifier(
            id = ModifierId("seq8-skill"), source = ModifierSource.Situational("seq8-skill"),
            target = ModTarget.Skill(SkillKey("Stealth", null, Attr.DX, Difficulty.AVERAGE)),
            effect = ModEffect.SetTo(2), layer = Layer.POTION,
            supersedes = ModifierId("seq9-general"),
        )
        assertEquals(13, ModifierResolver.resolve(12, listOf(seq9, seq8ForSkill), target, ctx).value)
    }
}
