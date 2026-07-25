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
        source: ModifierSource = ModifierSource.Situational(id),
    ) = Modifier(
        id = ModifierId(id), source = source, target = target,
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
            target = ModTarget.Skill(SkillKey("Stealth", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE)),
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
            target = ModTarget.Skill(SkillKey("Stealth", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE)),
            effect = ModEffect.SetTo(2), layer = Layer.POTION,
            supersedes = ModifierId("seq9-general"),
        )
        assertEquals(13, ModifierResolver.resolve(12, listOf(seq9, seq8ForSkill), target, ctx).value)
    }

    // --- Phase C regression tests: fixes 1-3 (KNOWN_ISSUES #1, #2, #3) ---

    @Test
    fun `a stack group cap also bounds Decay, not just Flat and SetTo`() {
        // KNOWN_ISSUES #3: a single Decay(from = -9) carrying StackGroup(cap = 2)
        // used to apply -9 in full, ignoring its group entirely.
        val cap = StackGroup("fear-decay", cap = 2)
        val r = ModifierResolver.resolve(12, listOf(
            mod("dread", ModEffect.Decay(from = -9, perTurn = 0), group = cap),
        ), target, ctx.copy(turn = 0))
        assertEquals(10, r.value)   // -9 raw, capped to -2
    }

    @Test
    fun `Cancel offsets the post-cap contribution, not the raw Flat sum`() {
        // KNOWN_ISSUES #1 (measured): four Flat(-3) in a StackGroup(cap = 2)
        // contribute -2, but Cancel(12) used to compute its budget from the raw
        // -12 sum and add +12, turning the penalty into a net bonus (12 -> 22).
        // Correct behaviour: the -12 raw is capped to -2 before Cancel ever sees
        // it, so Cancel can only offset -2, fully cancelling it and landing back
        // at the base value - never a bonus.
        val supportPenalty = StackGroup("multi-source-fear", cap = 2)
        val r = ModifierResolver.resolve(12, listOf(
            mod("f1", ModEffect.Flat(-3), Layer.SITUATIONAL, group = supportPenalty),
            mod("f2", ModEffect.Flat(-3), Layer.SITUATIONAL, group = supportPenalty),
            mod("f3", ModEffect.Flat(-3), Layer.SITUATIONAL, group = supportPenalty),
            mod("f4", ModEffect.Flat(-3), Layer.SITUATIONAL, group = supportPenalty),
            mod("cancel", ModEffect.Cancel(12, SourceKind.SITUATIONAL), Layer.ADVANTAGE),
        ), target, ctx)
        assertEquals(12, r.value)
    }

    @Test
    fun `two live SetTo effects in the same layer and group override rather than sum`() {
        // KNOWN_ISSUES #2: two live SetTo effects used to add together instead of
        // one replacing the other.
        val r = ModifierResolver.resolve(12, listOf(
            mod("weak", ModEffect.SetTo(1), Layer.POTION),
            mod("strong", ModEffect.SetTo(2), Layer.POTION),
        ), target, ctx)
        assertEquals(14, r.value)   // the higher SetTo (+2) wins; they must not sum to +3
    }

    @Test
    fun `Cancel only cancels its own SourceKind, not a mismatched one`() {
        // KNOWN_ISSUES #13: mod() used to hardcode source = Situational(id)
        // regardless of layer, so this distinction was never exercised.
        val r = ModifierResolver.resolve(12, listOf(
            mod("fright", ModEffect.Flat(-5), Layer.SITUATIONAL, source = ModifierSource.Situational("fright")),
            mod("wrong-kind", ModEffect.Cancel(3, SourceKind.POTION), Layer.ADVANTAGE),
        ), target, ctx)
        assertEquals(7, r.value)   // Cancel targets POTION; fright is SITUATIONAL, so nothing is cancelled
    }
}
