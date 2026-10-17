package com.pathways.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.model.SourceKind
import com.pathways.model.Trait
import com.pathways.rules.Appearance
import com.pathways.rules.BurnKind
import com.pathways.rules.CommonModifiers
import com.pathways.rules.EffortBurn
import com.pathways.rules.LedgerLine
import com.pathways.rules.SkillRollOutcome
import com.pathways.rules.SkillTarget
import com.pathways.rules.SpiritualSenses
import com.pathways.rules.SuccessDegree
import com.pathways.rules.TraitClause
import com.pathways.rules.TraitClauses
import com.pathways.rules.content.CommonModifier
import com.pathways.rules.content.SenseModifier
import com.pathways.rules.content.SpiritualSense

/**
 * Pick the circumstances, then roll.
 *
 * The roll used to fire on the tap that opened this dialog, which left nowhere
 * to state Appendix A's Common Modifiers before the dice were already cast.
 * Re-rolling as each chip is toggled would be worse: the GM would watch
 * results scroll past and keep whichever one arrived last.
 */
@Composable
internal fun RollDialog(
    label: String,
    base: Int,
    sense: SpiritualSense? = null,
    burnKind: BurnKind? = null,
    // What the mystical push may spend. Not the maximum and not what the
    // tracker shows: SPI already spent is gone, and 6ah's lesson is that
    // passing a pool where a rule wants a different quantity is a defect no
    // test catches.
    spiAvailable: Int = 0,
    // A push the book allows on part of what this skill covers, which the
    // engine cannot test for itself. Shown, never applied.
    conditionalBurnNote: String? = null,
    // Chapter 8's equipment rows whose printed note names this skill in a
    // signed clause. Reference, never a modifier: this app records no gear
    // owned but weapons, the table prices two disguise kits that would stack, and
    // one of its penalties is aimed at whoever is looking FOR the wearer.
    // Shown for the same reason the conditional push above is shown.
    equipment: List<String> = emptyList(),
    // The character's own conditional rows for this roll: the social ones on
    // a social skill and the ones naming this skill
    // ([TraitClauses.skillRollClauses]). The screen asks which hold and
    // hands the keys back; it never turns one into a modifier, because a
    // rules-authored list is the only thing that keeps one decision in one
    // place (6ag, 6ai, 6be, 6bp, 6bs, 6bv).
    declarableClauses: List<TraitClause> = emptyList(),
    onBurn: (BurnKind, Int) -> Unit = { _, _ -> },
    // The roll in two steps (6gd): the target for the circumstances picked
    // and the clauses declared, resolved while the dialog is drawn so it can
    // be printed, and the dice against that same value.
    targetFor: (List<com.pathways.model.Modifier>, Set<String>) -> SkillTarget,
    roll: (SkillTarget) -> SkillRollOutcome,
    onDismiss: () -> Unit,
) {
    var out by remember { mutableStateOf<SkillRollOutcome?>(null) }
    // Points already burned on THIS roll. The book caps the FP side at +5 and
    // caps the SPI side at nothing, so the ceiling comes from EffortBurn
    // rather than from a number chosen here.
    var burned by remember { mutableIntStateOf(0) }
    val chosen = remember { mutableStateListOf<CommonModifier>() }
    // Chapter 4's own circumstances, for the two skills that print them.
    val senseChosen = remember { mutableStateListOf<SenseModifier>() }
    // Which of this character's social circumstances the GM says hold. Keyed
    // on the clause, because Social Anxiety prints two that can both hold.
    val declaredClauses = remember { mutableStateListOf<String>() }
    // Appendix A rows this sense's own price supersedes. Recomputed rather
    // than stored, so a chip turned back off cannot leave a withheld row
    // hidden -- the stale-state defect the boon dialog was fixed for.
    val withheld = SpiritualSenses.displacedCommonModifiers(senseChosen.toList())
    // A GM's pick inside a printed range, keyed by situation. Rebuilt from the
    // current picks on every roll, so dropping a chip cannot leave its value
    // behind -- the stale-state defect the boon dialog was fixed for.
    val gmValue = remember { mutableStateMapOf<String, Int>() }
    // The GM's own ruling beside the printed rows (play session 1).
    var gmRuling by remember { mutableIntStateOf(0) }
    // The roll as the chips stand: printed under the level, and what Roll
    // rolls. This is the expression the click used to build, moved into the
    // draw, so every `require` behind it now runs while the dialog is on
    // screen. None can fire: each chip writes `chosen` and `gmValue` in one
    // handler, a displaced row is hidden and purged, and the steps stay
    // inside the printed span (traced by the design review).
    val standing = targetFor(
        CommonModifiers.modifiers(
            chosen = chosen.toList(),
            gmValue = gmValue.filterKeys { key ->
                chosen.any { it.situation == key }
            },
        ) + listOfNotNull(CommonModifiers.gmRuling(gmRuling)) +
            // Passed the general picks as well, so the double-charge is
            // refused in rules even though the chips cannot produce it.
            if (sense == null) emptyList() else SpiritualSenses.modifiers(
                sense = sense,
                chosen = senseChosen.toList(),
                common = chosen.toList(),
            ),
        declaredClauses.toSet(),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        // Both buttons always exist, rather than one slot whose label and
        // onClick swap from Roll to Close once the dice are cast. Roll simply
        // goes disabled, which makes the re-entrancy guard below visible
        // instead of implied -- a GM can see the roll has happened.
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        confirmButton = {
            Button(
                enabled = out == null,
                onClick = {
                    // Belt to the disabled state's braces: two taps in the same
                    // frame could otherwise roll twice and show the second, the
                    // guard the divination and ritual dialogs also needed.
                    // `standing` as this button was drawn beside it, never
                    // resolved again here: the number printed is the number
                    // rolled (6eg).
                    if (out == null) out = roll(standing)
                },
            ) {
                Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Roll", modifier = Modifier.padding(start = 8.dp))
            }
        },
        title = {
            Column {
                Text(label)
                Text(
                    "Skill $base",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = steadyBody {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val shownOut = out
                if (shownOut == null) {
                    // One height whatever is picked, so the chips below it do
                    // not move under a finger. What made it is listed under
                    // the chips, where it can grow.
                    TargetCard(standing)
                    if (sense != null) SenseRow(sense, senseChosen, chosen, gmValue)
                    if (declarableClauses.isNotEmpty()) {
                        Text(
                            "Does any of this hold?",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            declarableClauses.forEach { clause ->
                                FilterChip(
                                    selected = clause.key in declaredClauses,
                                    onClick = {
                                        if (clause.key in declaredClauses) {
                                            declaredClauses.remove(clause.key)
                                        } else {
                                            declaredClauses.add(clause.key)
                                        }
                                    },
                                    label = { Text("${clause.shown}: ${clause.printed}") },
                                )
                            }
                        }
                    }
                    CircumstanceRow(chosen, gmValue, withheld)
                    OptionStepper(
                        label = "Modifier",
                        value = gmRuling,
                        shown = signed(gmRuling),
                        note = "the GM's, for anything the chips do not name",
                    ) { gmRuling += it }
                    // What the target above is made of beyond the chips:
                    // the trackers and the traits. A circumstance picked
                    // here is already on screen as its own lit chip, and
                    // printing it again under them said one -4 twice. The
                    // result lists every line, the chips being gone by then.
                    LedgerLines(
                        standing.lines().filter { it.kind != SourceKind.SITUATIONAL },
                        standing.requiresCritical,
                    )
                    // Every row the table prints for this skill, conditions
                    // and all. Showing only the unconditional ones would teach
                    // the player that a skill with no line here has no gear --
                    // false exactly in the conditional band, which is most of
                    // them.
                    //
                    // PRE-ROLL, and that is the whole point of it sitting
                    // inside this branch. It used to live below the burn row,
                    // past the `return@Column`, so it composed only once a
                    // result existed -- chapter 8's rows reached the GM AFTER
                    // the dice, when the one thing they are for is choosing a
                    // modifier before them. Device-verified, because no test
                    // in this repo can reach a Compose label.
                    //
                    // BELOW the chips rather than above them, against a
                    // reviewer who preferred reference-before-picker. These
                    // rows print numbers ("+1 to First Aid"), and a numbered
                    // line directly above a row of tappable modifier chips
                    // reads as another selectable one; this app already
                    // refused a design that blurred an applied chip into a
                    // shown reference, and that distinction breaks the tie.
                    //
                    // Closed behind its title (6gl's RuleNote): reference the
                    // GM reads once, and the title still says "not applied".
                    if (equipment.isNotEmpty()) {
                        RuleNote("Gear that names this skill (not applied)") {
                            equipment.forEach {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    return@Column
                }
                ResultCard(shownOut, sense)
                // Ch. 2, p. 9: "You may use this after seeing the result."
                // That is the whole reason this sits BELOW the result rather
                // than beside the chips -- it is a decision made once the
                // dice are known, and the same dice are then re-read against
                // a higher target.
                // Withheld when the burn cannot move the result: a roll that
                // never happened, or one only a critical can win, where the
                // total decides and raising the target changes nothing.
                if (burnKind != null && EffortBurn.canPush(shownOut)) {
                    // The count this row's chips are offered against.
                    val drawn = burned
                    BurnRow(burnKind, burned, spiAvailable) { points ->
                        // The LIVE outcome, never the `shownOut` captured when
                        // this lambda was composed: two taps before a
                        // recomposition would both read the stale one, so the
                        // second push would overwrite the first while the pool
                        // paid for both. Bail rather than guess if it is gone.
                        val live = out ?: return@BurnRow
                        // And the live count. A second tap of the same
                        // picture buys nothing: its chip was offered against
                        // `drawn`, so +3 twice threw past the FP cap, and on
                        // the SPI row it spent a pool the first tap had
                        // emptied, which is permanent CoR. Dropped, not
                        // honoured: the pool moves at the tap here, and
                        // `spiAvailable` is the balance this row was drawn
                        // with (the design review). The row redraws.
                        if (burned != drawn) return@BurnRow
                        out = EffortBurn.pushed(live, burnKind, points, burned, spiAvailable)
                            ?: return@BurnRow
                        onBurn(burnKind, points)
                        burned += points
                    }
                }
                // Occultism's push is scoped to "vs. Beyonder phenomena", and
                // a SkillKey carries no subject matter. Naming it beats both
                // alternatives: granting it would push rolls the book does
                // not, and withholding it in silence would hide a printed
                // rule -- the shape a wealth level's reaction modifier and a
                // called shot's multiplier already take.
                if (conditionalBurnNote != null && EffortBurn.canPush(shownOut)) {
                    Text(
                        conditionalBurnNote,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
    )
}

/**
 * Appendix A's Common Modifiers. The untrained-default row is absent because
 * `CommonModifiers.offered()` withholds it: it replaces the roll with an
 * attribute default rather than modifying a skill, and this screen has no way
 * to offer that as a chip without lying about what it does.
 *
 * Chapter 5's darkness and cover rows are deliberately not here either — they
 * belong to the ranged dialog, and offering both would charge one dark room
 * twice.
 */
@Composable
private fun CircumstanceRow(
    chosen: MutableList<CommonModifier>,
    gmValue: MutableMap<String, Int>,
    withheld: Set<String> = emptySet(),
) {
    Text(
        "Circumstances (Appendix A)",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp),
    )
    if (withheld.isNotEmpty()) {
        Text(
            "Chapter 4 prices being distracted or in combat itself, so " +
                "Appendix A's general rows for it do not also apply.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CommonModifiers.offered().filterNot { it.situation in withheld }.forEach { row ->
            val isOn = chosen.any { it.situation == row.situation }
            FilterChip(
                selected = isOn,
                onClick = {
                    if (isOn) {
                        chosen.removeAll { it.situation == row.situation }
                        gmValue.remove(row.situation)
                    } else {
                        chosen.add(row)
                    }
                },
                label = { Text("${row.situation.substringBefore(" (")} ${row.printed}") },
            )
        }
    }
    // The whole printed range, not just its ends. "Improvised / poor tools
    // -2 to -4" prints -3 as surely as it prints -2 and -4, and
    // `CommonModifiers.valueFor` has always accepted it -- the chips were the
    // only thing that could not say it. The gentle end stays the default.
    //
    // An open end ("-6 or worse", "+4 or more") names no ceiling, so it
    // offers its printed end and three steps beyond: the same reading the
    // open-ended ritual fuel cell takes (6q), because a stepper running to a
    // hundred is not a choice worth making at the table.
    chosen.filter { it.otherEnd != null || it.isOpenEnded }.forEach { row ->
        val printed = row.modifier
        val ends = when {
            printed == null -> emptyList()
            row.otherEnd != null -> endsBetween(printed, row.otherEnd)
            // Away from zero, which is the only direction the book bounds.
            else -> endsBeyond(printed)
        }
        Text(
            "${row.situation}: ${row.printed}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ends.forEach { value ->
                FilterChip(
                    selected = (gmValue[row.situation] ?: row.modifier) == value,
                    onClick = { gmValue[row.situation] = value },
                    label = { Text(if (value > 0) "+$value" else "$value") },
                )
            }
        }
    }
}

/**
 * Chapter 4's own circumstances, for the two skills that print them.
 *
 * "+6 Seer" is offered rather than applied. Seer is the Fool pathway's
 * SEQUENCE 9 NAME -- a Fool at Sequence 8 is a Clown -- so granting it to
 * anyone on the Fool pathway would author a rule the book did not print.
 * Same call as the Sealed Artifact grade table (6af): show it, do not gate.
 */
@Composable
private fun SenseRow(
    sense: SpiritualSense,
    senseChosen: MutableList<SenseModifier>,
    chosen: MutableList<CommonModifier>,
    gmValue: MutableMap<String, Int>,
) {
    Text(
        "Circumstances (${sense.skill}, Ch. 4)",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SpiritualSenses.offered(sense).forEach { row ->
            val isOn = senseChosen.any { it.situation == row.situation }
            FilterChip(
                selected = isOn,
                onClick = {
                    if (isOn) {
                        senseChosen.removeAll { it.situation == row.situation }
                    } else {
                        senseChosen.add(row)
                        // Drop any Appendix A pick this row now supersedes,
                        // with the value a GM chose inside it. Leaving one
                        // selected but hidden is how a withheld row gets
                        // charged anyway.
                        val displaced = SpiritualSenses
                            .displacedCommonModifiers(senseChosen.toList())
                        chosen.removeAll { it.situation in displaced }
                        gmValue.keys.retainAll { it !in displaced }
                    }
                },
                label = {
                    Text(
                        "${row.situation} " +
                            if (row.modifier >= 0) "+${row.modifier}" else "${row.modifier}",
                    )
                },
            )
        }
    }
}

/** What a ledger roll says where nothing may be rolled, before the dice or after. */
internal const val FORBIDDEN_ROLL = "Forbidden — this roll cannot be attempted."

/**
 * One line for each thing that moved a ledger roll's target, and the
 * critical-only rule. The same lines before the dice and after, so the two
 * cannot be two renderings of one breakdown (6eh, on Detection).
 *
 * In the modifier's own words ([LedgerLine]): this printed its id,
 * "state:hp: -1", until 6ge.
 */
@Composable
internal fun LedgerLines(lines: List<LedgerLine>, requiresCritical: Boolean) {
    lines.forEach { line ->
        Text(
            "${line.label}: ${if (line.delta > 0) "+" else ""}${line.delta}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (requiresCritical) {
        Text(
            "Only a critical success counts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Appendix A grades a success into three bands and gives a critical success
 * its own row above them, so the degree only ever refines the SUCCESS line --
 * a critical is already the best result the table prints and re-labelling it
 * by margin would demote it.
 */
internal fun label(outcome: Outcome, degree: SuccessDegree? = null) = when (outcome) {
    Outcome.CRITICAL_SUCCESS -> "Critical success"
    Outcome.SUCCESS -> when (degree) {
        SuccessDegree.EXCELLENT -> "Excellent success"
        SuccessDegree.GOOD -> "Good success"
        else -> "Success"
    }
    Outcome.FAILURE -> "Failure"
    Outcome.CRITICAL_FAILURE -> "Critical failure"
}

/**
 * What a bought face a potion beat says beside its points (6fn): the budget
 * still bills it, and the better face is the one played.
 */
internal fun superseded(trait: Trait, traits: List<Trait>): String =
    if (Appearance.isSuperseded(trait, traits)) ", not played: the potion's face is better" else ""
