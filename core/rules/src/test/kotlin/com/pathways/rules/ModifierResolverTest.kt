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

    // --- Phase D: Scale composes by product (KNOWN_ISSUES: "Scale floors at
    // each step rather than composing by product") ---

    @Test
    fun `two Scale effects compose by product and floor once, not per step`() {
        // Spec 4.1: "cut x1.5 and imp x2 give x3". Flooring each Scale(3,2)
        // independently gives floor(floor(5*1.5)*1.5) = floor(7*1.5) = 10.
        // Composing by product gives floor(5*1.5*1.5) = floor(11.25) = 11.
        val r = ModifierResolver.resolve(5, listOf(
            mod("cut", ModEffect.Scale(3, 2), Layer.EQUIPMENT),
            mod("impaling", ModEffect.Scale(3, 2), Layer.SITUATIONAL),
        ), target, ctx)
        assertEquals(11, r.value)
    }

    @Test
    fun `a single Scale still just floors - no regression from composition`() {
        assertEquals(8, ModifierResolver.resolve(10, listOf(
            mod("encumbered", ModEffect.Scale(4, 5)),
        ), target, ctx).value)
    }

    @Test
    fun `a Flat between two Scales breaks the composing chain`() {
        // The Flat commits (floors) whatever Scale chain preceded it, so the
        // second Scale starts fresh from the post-Flat integer value - it
        // does not retroactively "see" the first Scale's fractional remainder.
        // floor(7*1.5) = 10 (first Scale); +1 = 11; floor(11*1.5) = 16.
        val r = ModifierResolver.resolve(7, listOf(
            mod("first-scale", ModEffect.Scale(3, 2), Layer.EQUIPMENT),
            mod("between", ModEffect.Flat(1), Layer.SITUATIONAL),
            mod("second-scale", ModEffect.Scale(3, 2), Layer.BURN),
        ), target, ctx)
        assertEquals(16, r.value)
    }

    @Test
    fun `a grouped Scale still floors and caps immediately, unaffected by composition`() {
        // Phase C's Scale-group-cap semantics (delta capped, immediate commit)
        // must be untouched by the product-composition fix: only *ungrouped*
        // Scales defer their floor.
        val cap = StackGroup("cut-cap", cap = 1)
        val r = ModifierResolver.resolve(5, listOf(
            mod("cut", ModEffect.Scale(3, 2), Layer.EQUIPMENT, group = cap),
        ), target, ctx)
        assertEquals(6, r.value)   // raw delta would be +2 (floor(7.5)-5), capped to +1
    }

    // --- Phase D: Scale can round up instead of down (KNOWN_ISSUES: "ModEffect
    // cannot express 'half, rounded up'") ---

    @Test
    fun `Scale rounds down by default, even when the exact half differs from the ceiling`() {
        // -5 * 1/2 = -2.5. Floor is -3, ceiling is -2 - a value where the two
        // directions genuinely differ, per Divination Arts (spec 4.1): Direct
        // Link halves anti-divination penalties rounding down.
        assertEquals(-3, ModifierResolver.resolve(-5, listOf(
            mod("direct-link", ModEffect.Scale(1, 2)),
        ), target, ctx).value)
    }

    @Test
    fun `Scale rounds up when roundUp is set`() {
        // Same -5 * 1/2, but active Divination Arts techniques apply their
        // half-penalty rounded UP (spec 4.1): -2.5 ceilings to -2, not -3.
        assertEquals(-2, ModifierResolver.resolve(-5, listOf(
            mod("active-technique", ModEffect.Scale(1, 2, roundUp = true)),
        ), target, ctx).value)
    }

    // --- Phase D: Duration remains deliberately unenforced (KNOWN_ISSUES) ---

    @Test
    fun `a Turns duration does not expire - Duration is not enforced by the resolver`() {
        // Pins the documented, deliberate non-enforcement (see Duration's
        // KDoc): ctx.turn alone cannot tell the resolver when a Turns(n)
        // modifier's clock started, so it behaves exactly like Permanent.
        // This must keep failing loudly (by this test changing) if Duration
        // enforcement is ever added, rather than silently starting to pass.
        val m = mod("still-active", ModEffect.Flat(-2), Layer.SITUATIONAL).let {
            it.copy(duration = Duration.Turns(1))
        }
        assertEquals(10, ModifierResolver.resolve(12, listOf(m), target, ctx.copy(turn = 0)).value)
        assertEquals(10, ModifierResolver.resolve(12, listOf(m), target, ctx.copy(turn = 500)).value)
    }

    // --- Phase D: Resolution.applied is an honest audit trail (KNOWN_ISSUES:
    // "Resolution.applied is a misleading audit trail") ---

    @Test
    fun `applied reports only modifiers that actually contributed a nonzero delta`() {
        val r = ModifierResolver.resolve(12, listOf(
            mod("bonus", ModEffect.Flat(2)),
            mod("noop-cancel", ModEffect.Cancel(5, SourceKind.SITUATIONAL), Layer.ADVANTAGE),
            mod("silent-forbid", ModEffect.Forbid, Layer.ADVANTAGE),
        ), target, ctx)
        assertEquals(14, r.value)
        assertEquals(listOf(Contribution(listOf(ModifierId("bonus")), 2)), r.applied)
    }

    @Test
    fun `applied attributes a capped joint pool to every contributing modifier as one step`() {
        val support = StackGroup("divination-support", cap = 2)
        val r = ModifierResolver.resolve(12, listOf(
            mod("occultism", ModEffect.Flat(1), group = support),
            mod("hidden-lore", ModEffect.Flat(1), group = support),
            mod("psychology", ModEffect.Flat(1), group = support),
            mod("cogitation", ModEffect.Flat(1), group = support),
        ), target, ctx)
        assertEquals(14, r.value)
        assertEquals(1, r.applied.size)
        val step = r.applied.single()
        assertEquals(2, step.delta)   // +4 raw, capped to +2
        assertEquals(setOf("occultism", "hidden-lore", "psychology", "cogitation"), step.ids.map { it.value }.toSet())
    }

    @Test
    fun `applied attributes each composed Scale its own marginal delta`() {
        val r = ModifierResolver.resolve(5, listOf(
            mod("cut", ModEffect.Scale(3, 2), Layer.EQUIPMENT),
            mod("impaling", ModEffect.Scale(3, 2), Layer.SITUATIONAL),
        ), target, ctx)
        assertEquals(11, r.value)
        assertEquals(
            listOf(Contribution(listOf(ModifierId("cut")), 2), Contribution(listOf(ModifierId("impaling")), 4)),
            r.applied,
        )
    }
}
