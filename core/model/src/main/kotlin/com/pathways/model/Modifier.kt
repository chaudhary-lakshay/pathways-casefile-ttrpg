package com.pathways.model

import kotlinx.serialization.Serializable

@JvmInline @Serializable value class ModifierId(val value: String)

@Serializable
data class StackGroup(val name: String, val cap: Int)

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
    @Serializable data class Scale(val num: Int, val den: Int) : ModEffect
    /** Overrides rather than adds within its (layer, group) bucket: when one or more SetTo effects are live in the same layer and group, the highest-value SetTo wins and replaces every Flat/SetTo contribution sharing that bucket (they do not sum). This is in addition to, not instead of, [Modifier.supersedes] - supersedes removes a named modifier from consideration entirely (used when the replacement is known by construction, e.g. Ch. 10's Midnight Poet), while this override handles the case where two SetTo effects are simply live together with nothing naming the other. */
    @Serializable data class SetTo(val n: Int) : ModEffect
    /** Offsets up to [upTo] of the penalty actually applied by modifiers whose [ModifierSource.kind] equals [of] - the post-StackGroup-cap, post-SetTo-override contribution, not a raw scan of Flat values, and not limited to Flat: a penalty delivered via Decay, Scale or SetTo is offset the same as one delivered via Flat. The budget is computed once per [ModifierResolver.resolve] call independently of layer order, so Cancel is correct whether its own layer runs before or after the layer carrying the penalty it targets. Multiple Cancel effects aimed at the same [SourceKind] share and deplete one pool rather than each independently re-reading the same total, so cancelling a penalty can never turn it into a net bonus. */
    @Serializable data class Cancel(val upTo: Int, val of: SourceKind) : ModEffect
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

/** Declared but not yet enforced by the resolver — nothing currently expires a modifier when its Duration elapses. */
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
