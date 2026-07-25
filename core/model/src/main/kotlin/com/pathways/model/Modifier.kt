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
    /** WARNING: ModifierResolver currently sums SetTo effects rather than assigning them, so correctness depends entirely on a supersedes link that nothing enforces at the type level. Two live SetTo modifiers in the same layer and group will add together instead of the later one replacing the earlier one. */
    @Serializable data class SetTo(val n: Int) : ModEffect
    /** WARNING: ModifierResolver currently computes this effect's penalty budget by scanning raw Flat values in the live modifier list, rather than the post-cap contribution actually applied. This means it bypasses StackGroup caps and ignores Decay/Scale/SetTo penalties entirely. Measured: four Flat(-3) modifiers in a cap=2 StackGroup contribute -2 to the total, but Cancel(12) still adds +12 based on the raw -12 sum, turning a penalty into a bonus. Must be fixed before any content uses Cancel. */
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
