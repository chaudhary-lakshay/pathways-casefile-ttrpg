package com.pathways.rules

import com.pathways.model.ModTarget
import com.pathways.model.Modifier
import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.model.SkillKey

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
) {
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
)

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
    )
}
