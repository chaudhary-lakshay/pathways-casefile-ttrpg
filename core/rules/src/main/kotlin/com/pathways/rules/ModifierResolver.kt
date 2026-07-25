package com.pathways.rules

import com.pathways.model.*

data class Context(
    val isNight: Boolean = false,
    val isDarkness: Boolean = false,
    val sequenceDelta: Int = 0,
    val turn: Int = 0,
)

data class Resolution(
    val value: Int,
    val forbidden: Boolean = false,
    val requiresCritical: Boolean = false,
    val applied: List<ModifierId> = emptyList(),
)

object ModifierResolver {

    /**
     * Runs the full layer pipeline once and returns (value, forbidden, requiresCritical).
     *
     * [cancelBudget], when non-null, is a *mutable, shared* pool of already-known
     * per-SourceKind penalty totals. Cancel effects draw down this pool as they're
     * encountered - so two Cancels aimed at the same SourceKind split one pool
     * instead of each independently re-reading the same total (which would let a
     * -5 penalty be "cancelled" twice for a net +1 bonus). When [cancelBudget] is
     * null, Cancel effects are skipped entirely - this is how the shadow pass
     * (see [resolve]) measures penalties without ever referencing Cancel itself.
     *
     * Every negative contribution actually applied by a non-Cancel effect - post
     * StackGroup cap, post SetTo-override - is recorded into [penaltyLedger],
     * keyed by the contributing modifier's SourceKind. That ledger is what the
     * real pass's [cancelBudget] gets seeded from.
     *
     * Group-cap semantics per effect (see KNOWN_ISSUES #3 / the fix that closed it):
     * - Flat + SetTo share one stacking pool per (layer, group): their raw values
     *   are summed (or the winning SetTo replaces them - see ModEffect.SetTo KDoc)
     *   and *that total* is clamped to the cap. This models "these skills stack,
     *   capped at +2 total" - the rulebook scenario the type was built for.
     * - Scale, Decay and Cancel are still applied per-modifier (no cross-modifier
     *   summing - nothing in the rulebook or fixtures describes multiple Decays,
     *   Scales or Cancels sharing one named stack group), but each one's own
     *   computed delta is now clamped to its group's cap before being added:
     *   - Decay: the cap bounds how much of its computed `remaining` value can
     *     apply this turn.
     *   - Scale: the cap bounds the delta the multiplication would otherwise
     *     produce (`scaled - before`), not the multiplier itself.
     *   - Cancel: the cap bounds the offset it grants after budget-limiting -
     *     i.e. it cannot hand back more than its own group allows, even if the
     *     penalty pool would otherwise permit it.
     * - Forbid/RequireCrit contribute no number, so a cap is meaningless for them
     *   and they are left alone.
     */
    private fun runLayers(
        base: Int,
        live: List<Modifier>,
        ctx: Context,
        cancelBudget: MutableMap<SourceKind, Int>?,
        penaltyLedger: MutableMap<SourceKind, Int>,
    ): Triple<Int, Boolean, Boolean> {
        var value = base
        var forbidden = false
        var requiresCritical = false

        fun record(kind: SourceKind, delta: Int) {
            if (delta < 0) penaltyLedger[kind] = (penaltyLedger[kind] ?: 0) + delta
        }

        fun capped(raw: Int, group: StackGroup?): Int =
            if (group == null) raw else raw.coerceIn(-group.cap, group.cap)

        for (layer in Layer.entries) {
            val inLayer = live.filter { it.layer == layer }
            if (inLayer.isEmpty()) continue

            // Flat + SetTo: joint stacking pool per (layer, group). A live SetTo
            // overrides every Flat/SetTo sharing its bucket instead of summing
            // with them - see ModEffect.SetTo KDoc. The highest SetTo wins when
            // more than one is live in the same bucket.
            inLayer.filter { it.effect is ModEffect.Flat || it.effect is ModEffect.SetTo }
                .groupBy { it.group }
                .forEach { (group, ms) ->
                    val setTos = ms.filter { it.effect is ModEffect.SetTo }
                    if (setTos.isNotEmpty()) {
                        val winner = setTos.maxBy { (it.effect as ModEffect.SetTo).n }
                        val applied = capped((winner.effect as ModEffect.SetTo).n, group)
                        value += applied
                        record(winner.source.kind, applied)
                    } else {
                        val raw = ms.sumOf { (it.effect as ModEffect.Flat).n }
                        value += capped(raw, group)
                        // Attribute the (possibly capped) contribution per
                        // SourceKind, each bounded individually by the same cap.
                        // Exact when the bucket is single-kind - the only case
                        // any rulebook example or fixture exercises.
                        ms.groupBy { it.source.kind }.forEach { (kind, kms) ->
                            record(kind, capped(kms.sumOf { (it.effect as ModEffect.Flat).n }, group))
                        }
                    }
                }

            inLayer.forEach { m ->
                when (val e = m.effect) {
                    is ModEffect.Flat, is ModEffect.SetTo -> Unit // handled above
                    is ModEffect.Scale -> {
                        val before = value
                        val scaled = Math.floorDiv(value * e.num, e.den)
                        val applied = capped(scaled - before, m.group)
                        value = before + applied
                        record(m.source.kind, applied)
                    }
                    is ModEffect.Decay -> {
                        val remaining = e.from + e.perTurn * ctx.turn
                        val raw = if (e.from < 0) minOf(remaining, 0) else maxOf(remaining, 0)
                        val applied = capped(raw, m.group)
                        value += applied
                        record(m.source.kind, applied)
                    }
                    is ModEffect.Cancel -> {
                        if (cancelBudget != null) {
                            val available = -(cancelBudget[e.of] ?: 0)
                            val offset = capped(minOf(e.upTo, available), m.group)
                            value += offset
                            cancelBudget[e.of] = (cancelBudget[e.of] ?: 0) + offset
                        }
                    }
                    ModEffect.Forbid -> forbidden = true
                    ModEffect.RequireCrit -> requiresCritical = true
                }
            }
        }

        return Triple(value, forbidden, requiresCritical)
    }

    fun resolve(base: Int, mods: List<Modifier>, target: ModTarget, ctx: Context): Resolution {
        // Only modifiers that are themselves active (right target, condition
        // holds) can supersede anything - an inactive or off-target
        // "superseder" must not delete the modifier it names.
        val activeForTarget = mods.filter { it.target == target && matches(it.condition, ctx) }
        val superseded = activeForTarget.mapNotNull { it.supersedes }.toSet()
        val live = activeForTarget.filter { it.id !in superseded }

        // Shadow pass: measure what each SourceKind actually contributes as a
        // penalty (post-cap, post-SetTo-override), with Cancel switched off, so
        // Cancel's own budget is known *before* Cancel ever runs. This is what
        // makes Cancel correct regardless of whether its own layer runs before
        // or after the layer carrying the penalty it targets.
        val penaltyLedger = mutableMapOf<SourceKind, Int>()
        runLayers(base, live, ctx, cancelBudget = null, penaltyLedger = penaltyLedger)

        // Real pass: identical pipeline, but Cancel effects now draw from (and
        // deplete) a private copy of the ledger above.
        val (value, forbidden, requiresCritical) =
            runLayers(base, live, ctx, cancelBudget = penaltyLedger.toMutableMap(), penaltyLedger = mutableMapOf())

        return Resolution(value, forbidden, requiresCritical, live.map { it.id })
    }

    private fun matches(c: Condition, ctx: Context): Boolean = when (c) {
        Condition.Always -> true
        Condition.AtNight -> ctx.isNight
        Condition.InDarkness -> ctx.isDarkness
        is Condition.AgainstLowerSequence -> ctx.sequenceDelta >= c.delta
        is Condition.All -> c.of.all { matches(it, ctx) }
        is Condition.Any -> c.of.any { matches(it, ctx) }
    }
}
