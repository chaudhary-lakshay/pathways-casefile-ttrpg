package com.pathways.rules

import com.pathways.model.ModTarget
import com.pathways.model.Modifier
import com.pathways.model.Outcome
import com.pathways.model.RollResult

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
}

object SkillRoll {

    /**
     * [mods] defaults to empty because the app has no modifier sources yet
     * (conditions and combat are M4). The resolver still runs: routing every
     * roll through it from the first one means a modifier gains its effect by
     * being *added to the list*, with no second code path to migrate later.
     */
    fun of(
        sheet: PremadeSheet,
        skill: PremadeSkill,
        source: RandomSource,
        mods: List<Modifier> = emptyList(),
        ctx: Context = Context(),
    ): SkillRollOutcome {
        val base = sheet.levelFor(skill)
        val target = ModTarget.Skill(skill.key())
        // ModifierResolver matches targets by exact equality, so a modifier
        // aimed at AllRolls would silently miss this one. Retarget it here
        // rather than loosening the matching rule for every caller of the
        // resolver. ModTarget.RollClass is deliberately not handled: SkillKey
        // carries no SkillTag yet, so there is nothing to match it against.
        val aimed = mods.map { if (it.target == ModTarget.AllRolls) it.copy(target = target) else it }
        val resolved = ModifierResolver.resolve(base, aimed, target, ctx)
        return SkillRollOutcome(
            label = skill.label,
            baseTarget = base,
            target = resolved.value,
            applied = resolved.applied,
            forbidden = resolved.forbidden,
            requiresCritical = resolved.requiresCritical,
            roll = if (resolved.forbidden) null else SuccessRoll.against(resolved.value, source),
        )
    }
}
