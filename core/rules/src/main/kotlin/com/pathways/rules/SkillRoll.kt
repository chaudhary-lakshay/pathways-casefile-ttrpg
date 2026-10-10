package com.pathways.rules

import com.pathways.model.ModTarget
import com.pathways.model.Modifier
import com.pathways.model.ModifierId
import com.pathways.model.ModifierSource
import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.model.SkillKey
import com.pathways.model.SourceKind

/**
 * One skill roll, start to finish: the skill's computed level, the modifier
 * stack resolved against it, then 3d6 rolled under the result.
 *
 * [roll] is null exactly when [forbidden] — a forbidden roll is not rolled and
 * then hidden, because a hidden roll still consumes the [RandomSource] and
 * would desynchronise a seeded one.
 */
data class SkillRollOutcome(
    val label: String,
    val baseTarget: Int,
    val target: Int,
    val applied: List<Contribution>,
    val forbidden: Boolean,
    val requiresCritical: Boolean,
    val roll: RollResult?,
    /** See [SkillTarget.sources]. */
    val sources: Map<ModifierId, ModifierSource> = emptyMap(),
) {
    /** What moved the target, in words: one [LedgerLine] per entry of [applied]. */
    fun lines(): List<LedgerLine> = ledgerLines(applied, sources)

    /**
     * [RollResult.outcome] reads the dice against the target. RequireCrit adds
     * a rule the dice cannot express — an ordinary success does not count — so
     * it is demoted here rather than displayed as a success with a footnote.
     */
    val effectiveOutcome: Outcome? get() = when {
        roll == null -> null
        requiresCritical && roll.outcome == Outcome.SUCCESS -> Outcome.FAILURE
        else -> roll.outcome
    }

    /**
     * How well this succeeded (Appendix A), or null when it did not.
     *
     * Computed from [effectiveOutcome] rather than from [roll] directly: a
     * roll demoted by `requiresCritical` is displayed as a failure, and
     * grading its margin would print "Good success" beside the word Failure.
     * Exposed here so no caller has to remember which of the two outcomes to
     * pass to [DegreeOfSuccess].
     */
    val degree: SuccessDegree? get() =
        roll?.let { DegreeOfSuccess.of(effectiveOutcome, it.margin) }
}

/**
 * A skill roll resolved and not yet rolled: the level, the modifier stack
 * applied to it, and what the stack said about the roll itself.
 *
 * What a screen prints BEFORE the dice. The fields are
 * [SkillRollOutcome]'s, less the roll.
 */
data class SkillTarget(
    val label: String,
    val baseTarget: Int,
    val target: Int,
    val applied: List<Contribution>,
    val forbidden: Boolean,
    val requiresCritical: Boolean,
    /**
     * Each modifier's own [ModifierSource], by id. [applied] names what
     * moved the target by id alone, and an id is a key, not a sentence.
     */
    val sources: Map<ModifierId, ModifierSource> = emptyMap(),
) {
    /** What moved the target, in words: one [LedgerLine] per entry of [applied]. */
    fun lines(): List<LedgerLine> = ledgerLines(applied, sources)
}

/**
 * One line of a ledger roll's breakdown: what moved the target, in the words
 * the modifier was built with, and by how much.
 *
 * [kind] is the source's, or null where nothing explains the id. A screen
 * that has just shown the GM's own picks as chips reads it to leave those
 * lines out; nothing else should branch on it.
 */
data class LedgerLine(val label: String, val delta: Int, val kind: SourceKind?)

/**
 * The words a modifier carries for itself.
 *
 * Every [ModifierSource] has held them since the model was written -- the
 * common-modifier row's situation, the pool threshold's level, the trait's
 * name -- and until 6ge no screen read one: the breakdown printed
 * `state:hp: -1`. This is the one place a source becomes text, so a new
 * source says here what it is called. Nothing builds a `Potion` source yet,
 * so that branch has never been seen on a screen.
 */
fun ModifierSource.label(): String = when (this) {
    is ModifierSource.Situational -> description
    is ModifierSource.StateThreshold -> tracker
    is ModifierSource.FromTrait -> name
    is ModifierSource.Equipment -> name
    is ModifierSource.Potion -> "$pathway, Sequence $sequence"
}

/**
 * A contribution with no source keeps its id, so it still prints and still
 * adds up. A grouped contribution names every member.
 */
internal fun ledgerLines(
    applied: List<Contribution>,
    sources: Map<ModifierId, ModifierSource>,
): List<LedgerLine> = applied.map { contribution ->
    val known = contribution.ids.mapNotNull(sources::get)
    LedgerLine(
        label = contribution.ids.joinToString { sources[it]?.label() ?: it.value },
        delta = contribution.delta,
        kind = known.map { it.kind }.distinct().singleOrNull(),
    )
}

object SkillRoll {

    /**
     * A roll of one of the sheet's printed skills.
     *
     * [mods] defaults to empty for a roll nothing is modifying; the resolver
     * still runs either way, which is why [Conditions] and [Attacks] gained
     * their effect by being *added to the list* rather than by growing a
     * second code path here.
     */
    fun of(
        sheet: PremadeSheet,
        skill: PremadeSkill,
        source: RandomSource,
        mods: List<Modifier> = emptyList(),
        ctx: Context = Context(),
    ): SkillRollOutcome = at(skill.label, sheet.levelFor(skill), skill.key(), source, mods, ctx)

    /**
     * A roll of a skill known only by its key and level -- the shape a
     * Beyonder's digested skill levels come in, where there is no
     * [PremadeSkill] to hand because a potion granted the skill.
     *
     * [targetFor] and then [rollAgainst], in one call. No screen calls this
     * or [of] since 6gd: each prints the target first. They are the composed
     * convenience the tests use, as `attempt` is in the other engines (6eh).
     */
    fun at(
        label: String,
        base: Int,
        key: SkillKey,
        source: RandomSource,
        mods: List<Modifier> = emptyList(),
        ctx: Context = Context(),
    ): SkillRollOutcome = rollAgainst(targetFor(label, base, key, mods, ctx), source)

    /**
     * The first half of [at]: the modifier stack resolved against [base], no
     * die drawn.
     *
     * A screen prints this and hands it to [rollAgainst], so the number on
     * the button is the number rolled (6eg). Until 6gd the three rolls that
     * go through the ledger, the skill roll, the attack and the Acrobatic
     * Dodge, printed a level and resolved their target inside the click.
     */
    fun targetFor(
        label: String,
        base: Int,
        key: SkillKey,
        mods: List<Modifier> = emptyList(),
        ctx: Context = Context(),
    ): SkillTarget {
        val target = ModTarget.Skill(key)
        // ModifierResolver matches targets by exact equality, so a modifier
        // aimed at AllRolls would silently miss this one. Retarget it here
        // rather than loosening the matching rule for every caller of the
        // resolver. ModTarget.RollClass is deliberately not handled: SkillKey
        // carries no SkillTag yet, so there is nothing to match it against.
        val aimed = mods.map { if (it.target == ModTarget.AllRolls) it.copy(target = target) else it }
        val resolved = ModifierResolver.resolve(base, aimed, target, ctx)
        return SkillTarget(
            label = label,
            baseTarget = base,
            target = resolved.value,
            applied = resolved.applied,
            forbidden = resolved.forbidden,
            requiresCritical = resolved.requiresCritical,
            sources = aimed.associate { it.id to it.source },
        )
    }

    /**
     * The second half: 3d6 against [target] as it was handed over, never
     * resolved again. A forbidden target draws no die.
     */
    fun rollAgainst(target: SkillTarget, source: RandomSource): SkillRollOutcome = SkillRollOutcome(
        label = target.label,
        baseTarget = target.baseTarget,
        target = target.target,
        applied = target.applied,
        forbidden = target.forbidden,
        requiresCritical = target.requiresCritical,
        roll = if (target.forbidden) null else SuccessRoll.against(target.target, source),
        sources = target.sources,
    )
}
