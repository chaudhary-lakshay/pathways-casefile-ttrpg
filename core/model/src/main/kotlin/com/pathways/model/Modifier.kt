package com.pathways.model

import kotlinx.serialization.Serializable

@JvmInline @Serializable value class ModifierId(val value: String)

@Serializable
data class StackGroup(val name: String, val cap: Int) {
    init {
        // capped()'s coerceIn(-cap, cap) dies with "Cannot coerce value to an
        // empty range" for a negative cap instead of failing with a
        // meaningful message at the actual construction boundary.
        require(cap >= 0) { "StackGroup '$name' cap must not be negative; was $cap" }
    }
}

@Serializable
enum class SourceKind { POTION, ADVANTAGE, DISADVANTAGE, EQUIPMENT, SITUATIONAL, STATE }

@Serializable
sealed interface ModifierSource {
    val kind: SourceKind

    @Serializable data class Potion(val pathway: String, val sequence: Int) : ModifierSource {
        override val kind get() = SourceKind.POTION
    }
    @Serializable data class FromTrait(val name: String, val advantage: Boolean) : ModifierSource {
        override val kind get() = if (advantage) SourceKind.ADVANTAGE else SourceKind.DISADVANTAGE
    }
    @Serializable data class Equipment(val name: String) : ModifierSource {
        override val kind get() = SourceKind.EQUIPMENT
    }
    @Serializable data class Situational(val description: String) : ModifierSource {
        override val kind get() = SourceKind.SITUATIONAL
    }
    @Serializable data class StateThreshold(val tracker: String) : ModifierSource {
        override val kind get() = SourceKind.STATE
    }
}

/** Closed set: the spec enumerates exactly SPI, FP, HP as cost pools. */
@Serializable
enum class Pool { SPI, FP, HP }

/**
 * Type-distinct wrapper over a damage-slot identifier. The closed set of
 * valid slots is not yet known — it becomes an enum once Phase B's content
 * reveals the real members. Until then this at least prevents crossing a
 * damage slot with a bare String or with [SkillTag].
 */
@JvmInline
@Serializable
value class DamageSlot(val value: String)

/**
 * Type-distinct wrapper over a skill-tag identifier. The closed set of
 * valid tags is not yet known — it becomes an enum once Phase B's content
 * reveals the real members. Until then this at least prevents crossing a
 * skill tag with a bare String or with [DamageSlot].
 */
@JvmInline
@Serializable
value class SkillTag(val value: String)

@Serializable
sealed interface ModTarget {
    @Serializable data class Attribute(val a: Attr) : ModTarget
    @Serializable data class Skill(val key: SkillKey) : ModTarget
    @Serializable data class RollClass(val tag: SkillTag) : ModTarget
    @Serializable data object AllRolls : ModTarget
    @Serializable data class Damage(val slot: DamageSlot) : ModTarget
    @Serializable data class Cost(val pool: Pool) : ModTarget
    @Serializable data object RequiredMargin : ModTarget
    // Deliberately NO Derived case — spec 4.2.
}

@Serializable
sealed interface ModEffect {
    @Serializable data class Flat(val n: Int) : ModEffect
    /**
     * Multiplies the running value by `num/den`, rounding down by default
     * (spec 4.1's default for damage multipliers) or up when [roundUp] is
     * set (spec 4.1's Divination Arts case: active techniques apply their
     * anti-divination penalty at half strength, rounded UP, while Direct
     * Link's passive halving rounds DOWN -- the same effect type, opposite
     * rounding, which is why this is a flag rather than a second effect).
     *
     * Multiple *ungrouped* (`group == null`), floor-mode (`roundUp ==
     * false`) Scale effects that run back-to-back with nothing else
     * (no Flat/Decay/Cancel, no grouped Scale) in between compose by
     * PRODUCT and floor only ONCE at the end, per spec 4.1 ("cut x1.5 and
     * imp x2 give x3") -- see [ModifierResolver]'s `runLayers` for why
     * flooring each one independently is wrong. A [group] on a Scale (or
     * `roundUp = true`) always floors/ceils and caps that step immediately,
     * exactly as before -- nothing in the rulebook or fixtures asks two
     * such Scales to compose, so this only changes behaviour for the
     * documented product-composition case.
     */
    @Serializable data class Scale(val num: Int, val den: Int, val roundUp: Boolean = false) : ModEffect {
        init { require(den != 0) { "Scale.den must not be zero (num=$num)" } }
    }
    /** Overrides rather than adds within its (layer, group) bucket - but only when that [Modifier.group] is an explicit, non-null [StackGroup]: when one or more SetTo effects are live in the same layer and the same named group, the highest-value SetTo wins and replaces every Flat/SetTo contribution sharing that group (they do not sum). When [Modifier.group] is null, there is no shared bucket to override: an ungrouped SetTo does NOT reach across and erase an unrelated ungrouped Flat that merely happens to share a layer - `group == null` means "no declared relationship", not "one shared pool" (see [ModifierResolver]'s `runLayers`). Multiple *live*, *ungrouped* SetTo effects still override each other (the highest wins) rather than sum, since there is no other way for two SetTo's claiming the same conceptual slot to resolve. This is in addition to, not instead of, [Modifier.supersedes] - supersedes removes a named modifier from consideration entirely (used when the replacement is known by construction, e.g. Ch. 10's Midnight Poet), while this override handles the case where two SetTo effects are simply live together with nothing naming the other. */
    @Serializable data class SetTo(val n: Int) : ModEffect
    /** Offsets up to [upTo] of the penalty actually applied by modifiers whose [ModifierSource.kind] equals [of] - the post-StackGroup-cap, post-SetTo-override contribution, not a raw scan of Flat values, and not limited to Flat: a penalty delivered via Decay or SetTo is offset the same as one delivered via Flat. Scale-delivered penalties are NOT cancellable: Scale's delta (`scaled - before`) is a function of the running value at the point it runs, which the shadow pass that computes Cancel's budget cannot measure correctly once a Cancel sits upstream of it (the shadow pass runs with Cancel disabled, so its value - and therefore Scale's delta - differs from the real pass's). Rather than seed Cancel's budget from a value measured against the wrong base, Scale's contribution is simply excluded from that budget entirely - a Scale downstream of a Cancel targeting its SourceKind is unaffected by that Cancel. The budget is computed once per [ModifierResolver.resolve] call independently of layer order, so Cancel is correct whether its own layer runs before or after the layer carrying the (non-Scale) penalty it targets. Multiple Cancel effects aimed at the same [SourceKind] share and deplete one pool rather than each independently re-reading the same total, so cancelling a penalty can never turn it into a net bonus. */
    @Serializable data class Cancel(val upTo: Int, val of: SourceKind) : ModEffect {
        init { require(upTo >= 0) { "Cancel.upTo must not be negative (was $upTo); a negative budget would act as an extra penalty" } }
    }
    @Serializable data class Decay(val from: Int, val perTurn: Int) : ModEffect
    @Serializable data object Forbid : ModEffect
    @Serializable data object RequireCrit : ModEffect
}

/** Resolution order. Never a flat sum across layers. */
@Serializable
enum class Layer {
    BASE, POTION, ADVANTAGE, EQUIPMENT, SITUATIONAL,
    STATE_THRESHOLD, COUNTERMEASURE, COUNTER_COUNTER, BURN
}

@Serializable
sealed interface Scope {
    @Serializable data object Self : Scope
    @Serializable data class Ally(val radiusYards: Int) : Scope
    @Serializable data object Enemy : Scope
    @Serializable data class Area(val radiusYards: Int) : Scope
}

/**
 * Declared but deliberately NOT enforced by [ModifierResolver] — nothing
 * currently expires a modifier when its Duration elapses (KNOWN_ISSUES).
 *
 * This is a scope decision, not an oversight: enforcing it needs state the
 * resolver does not have. [Modifier] carries no "activated at turn N" (or
 * "activated at real time T") field, and [ModifierResolver.resolve] is a
 * pure function of `(base, mods, target, ctx)` called fresh each time with
 * no memory of when any given modifier's clock started -- `ctx.turn` alone
 * cannot answer "has N turns elapsed since this Turns(N) modifier began"
 * without a start-turn to compare it against. Adding that start-turn field
 * to [Modifier] itself would actually be cheap right now -- there are zero
 * `Modifier(...)` construction sites in main source today, and no content
 * file holds a serialized [Modifier] at all, so nothing would need touching.
 * The real blocker is simpler: that start-turn has to live somewhere a
 * caller can supply and update it turn-by-turn as play proceeds, and no such
 * stateful caller exists yet -- adding the field alone wouldn't accomplish
 * anything without one. Until a caller exists that can supply that state,
 * [Turns] and [Minutes] behave exactly like [Permanent] and [Scene]
 * currently do: never expiring on their own. `ModifierResolverTest` pins
 * this current (non-)behaviour so it cannot silently change without
 * updating this note.
 */
@Serializable
sealed interface Duration {
    @Serializable data object Permanent : Duration
    @Serializable data class Turns(val n: Int) : Duration
    @Serializable data class Minutes(val n: Int) : Duration
    @Serializable data object Scene : Duration
}

/** A data ADT, not a lambda, so new chapters stay data rather than code. */
@Serializable
sealed interface Condition {
    @Serializable data object Always : Condition
    @Serializable data object AtNight : Condition
    @Serializable data object InDarkness : Condition
    @Serializable data class AgainstLowerSequence(val delta: Int) : Condition
    @Serializable data class All(val of: List<Condition>) : Condition
    @Serializable data class Any(val of: List<Condition>) : Condition
}

@Serializable
data class Modifier(
    val id: ModifierId,
    val source: ModifierSource,
    val target: ModTarget,
    val effect: ModEffect,
    val layer: Layer,
    val group: StackGroup? = null,
    val supersedes: ModifierId? = null,
    val scope: Scope = Scope.Self,
    val duration: Duration = Duration.Permanent,
    val condition: Condition = Condition.Always,
)
