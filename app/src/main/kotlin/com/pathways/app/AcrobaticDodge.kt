package com.pathways.app

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.rules.ActiveDefenses
import com.pathways.rules.SkillDefaults
import com.pathways.rules.SkillRoll
import com.pathways.rules.SkillRollOutcome
import com.pathways.rules.SkillTarget
import com.pathways.rules.SuccessRoll
import com.pathways.rules.TraitEffects

/**
 * What an Acrobatic Dodge is rolled against: the sheet's own Acrobatics, or
 * the default [com.pathways.rules.SkillDefaults] composed for a sheet without
 * it. [untrainedSource] is null for a trained skill and the default's own
 * account of itself otherwise ("DX-6, +1 Versatile").
 */
internal data class AcrobaticSkill(val key: SkillKey, val level: Int, val untrainedSource: String?) {
    /** What the roll is called, so a default says it is one and which. */
    val label: String
        get() = untrainedSource?.let { "${key.name} (untrained, $it)" } ?: key.name
}

/**
 * The key and level together: the key is what the modifier ledger is asked
 * about, and the level is what is rolled against.
 *
 * **The default is taken from [untrained], never rebuilt here.** Every roll
 * at a default carries Versatile's +1 and that lives in `SkillDefaults`; a
 * second DX-6 written on this screen is the one that would forget it.
 */
internal fun acrobaticsFor(
    skillLevels: Map<SkillKey, Int>,
    untrained: List<SkillDefaults.Untrained>,
): AcrobaticSkill? =
    skillLevels.entries
        .firstOrNull { it.key.name == ActiveDefenses.ACROBATIC_SKILL }
        ?.let { AcrobaticSkill(it.key, it.value, null) }
        ?: untrained
            .firstOrNull { it.key.name == ActiveDefenses.ACROBATIC_SKILL }
            ?.let { AcrobaticSkill(it.key, it.level, it.source) }

/**
 * The Acrobatic Dodge's target, with what the Skills tab gives the same skill:
 * what the trackers cost ([conditions]) and what the character is
 * ([TraitEffects.modifiers] -- Perfect Balance's +2 on Acrobatics).
 *
 * **The second term was missing until 6ev**, on the trained path too. No
 * premade could show it: the one sheet with Perfect Balance has no
 * Acrobatics, and this control refused a sheet without the skill. Offering
 * the default is what made Pickpocket roll 4 here and 6 on the Skills tab
 * (the diff review's HIGH).
 *
 * NOT the GM's circumstance chips: those belong to the Skills tab's roll
 * dialog, and this screen has none, so a circumstance is still the GM's to
 * apply by hand.
 *
 * **Resolved while the dialog is drawn, and rolled as printed** (6gd). It
 * was resolved inside the tap, so a trained sheet saw no number before the
 * dice at all.
 */
internal fun acrobaticTarget(
    skill: AcrobaticSkill,
    conditions: (SkillKey) -> List<com.pathways.model.Modifier>,
    traits: List<Trait>,
): SkillTarget = SkillRoll.targetFor(
    label = skill.label,
    base = skill.level,
    key = skill.key,
    mods = conditions(skill.key) + TraitEffects.modifiers(traits, skill.key),
)

/**
 * The Acrobatic Dodge: a roll this app can actually make (Ch. 5, p. 37).
 *
 * "[Free Action] [Acrobatic Dodge] Roll vs. Acrobatics: success gives +2 Dodge;
 * failure gives -2 Dodge." Extracted since 6g and applied by nothing -- the
 * only one of the five Defense Modifiers bullets with a die behind it.
 *
 * **Rolled through [SkillRoll], not [com.pathways.rules.SuccessRoll].** Every
 * other skill roll in this app carries what the trackers cost
 * ([com.pathways.rules.Conditions]); a bare 3d6 against the printed level would
 * have a reeling character roll Acrobatics at one number here and another on
 * the sheet screen.
 *
 * **A sheet without Acrobatics rolls its default here** (6ev). The bullet
 * says "Roll vs. Acrobatics" and names no training, and p. 10 opens "You can
 * attempt most skills even without training". The level is the one
 * `SkillDefaults` composed for the Skills tab ([acrobaticsFor]), and
 * [acrobaticTarget] adds the same tracker and trait modifiers that roll takes.
 *
 * **Re-rollable on purpose.** See the state's own comment: the "once per round"
 * limit lives in a paragraph governing all three free actions, which this app
 * neither extracts nor enforces for the other two.
 */
@Composable
internal fun AcrobaticDodge(
    skill: AcrobaticSkill?,
    standing: SkillTarget?,
    rolled: SkillRollOutcome?,
    onRoll: () -> Unit,
    onClear: () -> Unit,
) {
    DefenseNote(ActiveDefenses.printedAcrobaticDodge())
    if (skill == null || standing == null) {
        // Not reachable from a printed sheet: Acrobatics is DX/Hard and the
        // table gives Hard a default. Said rather than left blank all the same.
        DefenseNote("No ${ActiveDefenses.ACROBATIC_SKILL} on this sheet, and no default for it.")
        return
    }
    if (standing.forbidden) {
        // Said before the tap, not only after it (6gd's design review).
        DefenseNote("Something forbids this roll, so the Dodge row above is unchanged.")
        return
    }
    if (skill.untrainedSource != null) {
        // p. 10's own caveat, which this app records no ruling for.
        DefenseNote(
            "${skill.label} ${skill.level}. The GM may rule that a skill needs " +
                "specialist knowledge before it can be attempted at all.",
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onRoll) {
            // The number this tap rolls, with the level beside it wherever
            // the trackers or a trait moved it.
            val against = if (standing.target == standing.baseTarget) {
                SuccessRoll.printed(standing.target)
            } else {
                "${SuccessRoll.printed(standing.target)} (${standing.baseTarget} → ${standing.target})"
            }
            Text(
                (if (rolled == null) "Roll vs. ${ActiveDefenses.ACROBATIC_SKILL}" else "Roll again") +
                    " — $against",
            )
        }
        // The way back, which a latched roll needs as much as a paid boost does
        // (6u): the GM who rolled for the wrong attack has to be able to say so.
        if (rolled != null) {
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
    rolled?.let { outcome ->
        val roll = outcome.roll
        if (roll == null) {
            // A forbidden roll is not rolled at all, and the Dodge row above
            // is unmoved -- ActiveDefenses.acrobaticDodge answers 0 for it.
            DefenseNote("Something forbids this roll, so the Dodge row above is unchanged.")
            return@let
        }
        // Both targets, whenever they differ: the printed level and what the
        // trackers left of it. One number the GM cannot reconcile against the
        // sheet is the defect 6be found on this screen's neighbour.
        val target = if (outcome.target == outcome.baseTarget) {
            "${outcome.baseTarget}"
        } else {
            "${outcome.baseTarget} → ${outcome.target}"
        }
        // Non-null wherever the roll is: `effectiveOutcome` is null exactly when
        // nothing was rolled, which the branch above has already answered.
        val effective = outcome.effectiveOutcome ?: return@let
        val swing = ActiveDefenses.acrobaticDodge(effective)
        DefenseNote(
            "${skill.label} $target: rolled ${roll.total} — " +
                "${label(effective, outcome.degree)}, " +
                "${if (swing > 0) "+" else ""}$swing on Dodge above.",
        )
    }
}
