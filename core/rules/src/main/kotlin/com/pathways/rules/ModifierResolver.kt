package com.pathways.rules

import com.pathways.model.*

data class Context(
    val isNight: Boolean = false,
    val isDarkness: Boolean = false,
    val sequenceDelta: Int = 0,
    val turn: Int = 0,
)

/**
 * One (group-of-modifiers, delta) pair that actually changed the running
 * value. [ids] holds every modifier that jointly produced [delta]: usually
 * one, but more than one when several modifiers share a single computation
 * step -- a Flat/SetTo StackGroup pool (KNOWN_ISSUES: the group is capped
 * as one joint total, so there is no natural per-modifier split once the
 * cap actually binds) or a chain of composed Scale effects that were
 * floored together (see [ModifierResolver]'s `runLayers`).
 *
 * Only steps with a nonzero [delta] appear here. A modifier that matched
 * the target but ended up contributing nothing (fully capped away, an
 * inactive branch of a joint pool, etc.) is honestly omitted rather than
 * listed as if it had mattered — the defect this replaces (KNOWN_ISSUES:
 * `Resolution.applied` was a misleading audit trail).
 */
data class Contribution(val ids: List<ModifierId>, val delta: Int)

data class Resolution(
    val value: Int,
    val forbidden: Boolean = false,
    val requiresCritical: Boolean = false,
    /**
     * What actually moved [value] away from the base, in application order,
     * each entry summing exactly to its slice of `value - base`. Deliberately
     * excludes Forbid/RequireCrit (they contribute no number — see
     * [forbidden]/[requiresCritical] instead) and any zero-delta step.
     */
    val applied: List<Contribution> = emptyList(),
)

object ModifierResolver {

    private fun gcd(a: Long, b: Long): Long {
        var x = if (a < 0) -a else a
        var y = if (b < 0) -b else b
        while (y != 0L) { val t = y; y = x % y; x = t }
        return if (x == 0L) 1L else x
    }

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
     * Every negative contribution actually applied by a non-Cancel, non-Scale
     * effect - post StackGroup cap, post SetTo-override - is recorded into
     * [penaltyLedger], keyed by the contributing modifier's SourceKind. That
     * ledger is what the real pass's [cancelBudget] gets seeded from. Scale is
     * deliberately excluded: its delta (`scaled - before`) is a function of the
     * running `value` at the point it runs, which differs between the shadow
     * pass (Cancel disabled) and the real pass (Cancel possibly already
     * restored an earlier penalty) whenever a Scale sits downstream of a
     * Cancel - recording it would seed the real pass's budget from a delta
     * measured against the wrong base. See [ModEffect.Cancel]'s KDoc: a
     * Scale-delivered penalty is simply not cancellable.
     *
     * [contributions], when non-null (the real pass only — see [resolve]), is
     * appended with one [Contribution] per nonzero-delta step, in application
     * order. See [Contribution]'s KDoc for what "one step" means.
     *
     * Group-cap semantics per effect (see KNOWN_ISSUES / the fix that closed it):
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
     *     produce (`scaled - before`), not the multiplier itself. This is only
     *     exercised (and only correct) for a *grouped* Scale, which always
     *     floors/caps immediately — see the "Scale composition" note below.
     *     Note this delta is never fed into [penaltyLedger] (see above) - the
     *     group cap still bounds what Scale itself applies, independent of
     *     whether it's ledgered for Cancel.
     *   - Cancel: the cap bounds the offset it grants after budget-limiting -
     *     i.e. it cannot hand back more than its own group allows, even if the
     *     penalty pool would otherwise permit it.
     * - Forbid/RequireCrit contribute no number, so a cap is meaningless for them
     *   and they are left alone.
     *
     * Scale composition (KNOWN_ISSUES: "Scale floors at each step rather than
     * composing by product"): an *ungrouped*, floor-mode Scale does not commit
     * (floor) its multiplication immediately. Instead it extends a pending
     * exact-fraction "chain" that started at the last committed integer value —
     * each subsequent ungrouped floor-mode Scale multiplies further into that
     * same fraction instead of restarting from an already-rounded value. The
     * chain is committed (floored, added into `value`) the moment anything else
     * needs the concrete integer: a Flat/SetTo bucket, a Decay, a Cancel, a
     * grouped or round-up Scale, or the end of the whole layer loop. Two
     * Scale(3,2) on base 5 therefore give floor(5 * 3/2 * 3/2) = 11, not
     * floor(floor(5*3/2)*3/2) = 10 — matching spec 4.1's "compose by product".
     * Each modifier's own marginal contribution (for [contributions]) is still
     * attributed correctly by tracking the chain's running floor as each Scale
     * joins it (see `applyScale` below) — the per-modifier deltas always sum
     * exactly to the final floored delta. (Not [penaltyLedger]: Scale is never
     * ledgered at all — see above.)
     */
    private fun runLayers(
        base: Int,
        live: List<Modifier>,
        ctx: Context,
        cancelBudget: MutableMap<SourceKind, Int>?,
        penaltyLedger: MutableMap<SourceKind, Int>,
        contributions: MutableList<Contribution>? = null,
    ): Triple<Int, Boolean, Boolean> {
        var value = base
        var forbidden = false
        var requiresCritical = false

        // Pending, not-yet-floored chain of consecutive ungrouped floor-mode
        // Scale effects. `chainBase` is the committed `value` when the chain
        // began; `chainNum`/`chainDen` is the accumulated product of their
        // num/den; `chainFloor` is the floor of the chain so far, used only
        // to attribute each modifier's own marginal delta.
        var chainActive = false
        var chainBase = 0
        var chainNum = 1L
        var chainDen = 1L
        var chainFloor = 0

        fun record(kind: SourceKind, delta: Int) {
            if (delta < 0) penaltyLedger[kind] = (penaltyLedger[kind] ?: 0) + delta
        }

        fun contribute(ids: List<ModifierId>, delta: Int) {
            if (delta != 0) contributions?.add(Contribution(ids, delta))
        }

        fun capped(raw: Int, group: StackGroup?): Int =
            if (group == null) raw else raw.coerceIn(-group.cap, group.cap)

        fun commitChain() {
            if (!chainActive) return
            value = chainFloor
            chainActive = false
            chainNum = 1L
            chainDen = 1L
        }

        fun applyScale(m: Modifier, e: ModEffect.Scale) {
            if (m.group == null && !e.roundUp) {
                if (!chainActive) {
                    chainActive = true
                    chainBase = value
                    chainFloor = value
                }
                var n = chainNum * e.num
                var d = chainDen * e.den
                val g = gcd(n, d)
                if (g != 0L) { n /= g; d /= g }
                chainNum = n
                chainDen = d
                val newFloor = Math.floorDiv(chainBase.toLong() * chainNum, chainDen).toInt()
                val applied = newFloor - chainFloor
                contribute(listOf(m.id), applied)
                chainFloor = newFloor
            } else {
                commitChain()
                val before = value
                val rawNumerator = before.toLong() * e.num
                val scaled = if (e.roundUp) {
                    // Ceiling division: -(floorDiv(-a, b)) — correct for any sign of a/b.
                    -Math.floorDiv(-rawNumerator, e.den.toLong())
                } else {
                    Math.floorDiv(rawNumerator, e.den.toLong())
                }.toInt()
                val applied = capped(scaled - before, m.group)
                value = before + applied
                contribute(listOf(m.id), applied)
            }
        }

        for (layer in Layer.entries) {
            val inLayer = live.filter { it.layer == layer }
            if (inLayer.isEmpty()) continue

            // Flat + SetTo: joint stacking pool, but ONLY within an explicit
            // StackGroup - a live SetTo overrides every Flat/SetTo sharing its
            // named group instead of summing with them (see ModEffect.SetTo
            // KDoc), and the highest SetTo wins when more than one is live in
            // the same group. Without a group (group == null), a SetTo is NOT
            // license to erase an unrelated ungrouped Flat that merely happens
            // to land in the same layer - group == null is "no relationship
            // declared", not "one shared bucket". A Flat/SetTo that a specific
            // SetTo actually supersedes is already gone from `live` entirely
            // by the time we get here (see [resolve]'s `supersedes` filter),
            // so the only thing left to decide for group == null is multiple
            // *live* SetTo's with no supersedes link between them - those
            // still override each other (highest wins), which is the
            // documented "two live SetTo" case (KNOWN_ISSUES #2) and names no
            // group at all. So: ungrouped Flat's simply sum among themselves,
            // ungrouped SetTo's separately override among themselves, and
            // both contributions apply independently.
            val flatSetTo = inLayer.filter { it.effect is ModEffect.Flat || it.effect is ModEffect.SetTo }
            if (flatSetTo.isNotEmpty()) {
                commitChain()
                flatSetTo.groupBy { it.group }.forEach { (group, ms) ->
                    val setTos = ms.filter { it.effect is ModEffect.SetTo }
                    val flats = ms.filter { it.effect is ModEffect.Flat }
                    if (group != null && setTos.isNotEmpty()) {
                        val winner = setTos.maxBy { (it.effect as ModEffect.SetTo).n }
                        val applied = capped((winner.effect as ModEffect.SetTo).n, group)
                        value += applied
                        record(winner.source.kind, applied)
                        contribute(listOf(winner.id), applied)
                    } else {
                        if (flats.isNotEmpty()) {
                            val raw = flats.sumOf { (it.effect as ModEffect.Flat).n }
                            val applied = capped(raw, group)
                            value += applied
                            contribute(flats.map { it.id }, applied)
                            // Split the (possibly capped) contribution across the
                            // SourceKinds that produced it, in proportion to each
                            // kind's raw share, via prefix differences so the parts
                            // sum to EXACTLY `applied`. Capping each kind's own sum
                            // independently (the previous approach) over-attributes
                            // a mixed-kind group: a cap=2 bucket with -3 from two
                            // different kinds recorded -2 twice, letting two Cancels
                            // hand back +4 against a penalty that only applied -2.
                            if (raw != 0) {
                                var prefixRaw = 0L
                                var prefixApplied = 0L
                                flats.groupBy { it.source.kind }.forEach { (kind, kms) ->
                                    prefixRaw += kms.sumOf { (it.effect as ModEffect.Flat).n }
                                    val next = Math.floorDiv(applied.toLong() * prefixRaw, raw.toLong())
                                    record(kind, (next - prefixApplied).toInt())
                                    prefixApplied = next
                                }
                            }
                        }
                        if (setTos.isNotEmpty()) {
                            // Only reachable here when group == null (the
                            // group != null + setTos case is handled above) -
                            // still overrides among themselves, applied on top
                            // of (not instead of) whatever the Flat's above
                            // just contributed.
                            val winner = setTos.maxBy { (it.effect as ModEffect.SetTo).n }
                            val applied = capped((winner.effect as ModEffect.SetTo).n, group)
                            value += applied
                            record(winner.source.kind, applied)
                            contribute(listOf(winner.id), applied)
                        }
                    }
                }
            }

            inLayer.forEach { m ->
                when (val e = m.effect) {
                    is ModEffect.Flat, is ModEffect.SetTo -> Unit // handled above
                    is ModEffect.Scale -> applyScale(m, e)
                    is ModEffect.Decay -> {
                        commitChain()
                        val remaining = e.from + e.perTurn * ctx.turn
                        val raw = if (e.from < 0) minOf(remaining, 0) else maxOf(remaining, 0)
                        val applied = capped(raw, m.group)
                        value += applied
                        record(m.source.kind, applied)
                        contribute(listOf(m.id), applied)
                    }
                    is ModEffect.Cancel -> {
                        commitChain()
                        if (cancelBudget != null) {
                            val available = -(cancelBudget[e.of] ?: 0)
                            val offset = capped(minOf(e.upTo, available), m.group)
                            value += offset
                            cancelBudget[e.of] = (cancelBudget[e.of] ?: 0) + offset
                            contribute(listOf(m.id), offset)
                        }
                    }
                    ModEffect.Forbid -> forbidden = true
                    ModEffect.RequireCrit -> requiresCritical = true
                }
            }
        }

        commitChain()
        return Triple(value, forbidden, requiresCritical)
    }

    fun resolve(base: Int, mods: List<Modifier>, target: ModTarget, ctx: Context): Resolution {
        // Only modifiers that are themselves active (right target, condition
        // holds) can supersede anything - an inactive or off-target
        // "superseder" must not delete the modifier it names.
        val activeForTarget = mods.filter {
            it.target == target && !expired(it, ctx) && matches(it.condition, ctx)
        }
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
        // deplete) a private copy of the ledger above, and this time every
        // nonzero-delta step is recorded into `contributions` for Resolution.applied.
        val contributions = mutableListOf<Contribution>()
        val (value, forbidden, requiresCritical) =
            runLayers(
                base, live, ctx, cancelBudget = penaltyLedger.toMutableMap(),
                penaltyLedger = mutableMapOf(), contributions = contributions,
            )

        return Resolution(value, forbidden, requiresCritical, contributions)
    }

    /**
     * True once [m]'s clock has run out by `ctx.turn`. A modifier with no
     * [Modifier.startTurn] is not on a clock and never expires, which keeps
     * every modifier that predates duration-tracking behaving as before.
     * One turn is one second, so [Duration.Minutes] `n` is `n * 60` turns.
     */
    private fun expired(m: Modifier, ctx: Context): Boolean {
        val start = m.startTurn ?: return false
        val elapsed = (ctx.turn - start).toLong()
        return when (val d = m.duration) {
            Duration.Permanent, Duration.Scene -> false
            is Duration.Turns -> elapsed >= d.n.toLong()
            is Duration.Minutes -> elapsed >= d.n.toLong() * 60L
        }
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
