package com.pathways.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.RollResult
import com.pathways.rules.FrightCheck
import com.pathways.rules.FrightKind
import com.pathways.rules.FrightOutcome
import com.pathways.rules.SuccessRoll
import com.pathways.rules.Shock
import com.pathways.rules.TraitClause
import com.pathways.rules.StunRecovery
import com.pathways.rules.ClauseBonus
import com.pathways.rules.TraitClauses
import com.pathways.rules.content.FrightRecovery
import com.pathways.rules.TraitEffects
import com.pathways.rules.content.FrightTrigger

/**
 * A Fright Check at the table (Ch. 5, p. 43).
 *
 * ponytail: the trigger list IS the interface. Two of the book's five rows
 * print a range and one is open-ended, so the app offers the gentler end it
 * prints and shows the range beside it -- the severity inside that range is
 * the GM's call, exactly like the acting-quality ranges on the pathway screen.
 */
@Composable
internal fun FrightDialog(
    will: Int,
    /** For the Confusion column's recovery roll, which is the only IQ roll here. */
    iq: Int,
    /**
     * For row 12's fright duration, which is the only HT term on this screen.
     *
     * No roll in this table is HT. The cell counts down from it and rolls
     * Will, which is exactly why the duration could not ride on the recovery.
     */
    ht: Int,
    clauses: List<TraitClause> = emptyList(),
    /**
     * The subset of [clauses] that name a bare Will roll rather than the
     * Fright Check itself.
     *
     * The recovery roll is a second bare Will roll in two of three columns, so
     * a clause like "-1 to Will rolls for the day" reaches it — but the rows
     * scoped to the Fright Check do not, because the check is over. Passed
     * separately rather than re-filtered here: which clause names which roll
     * is [TraitEffects]' business, and a composable deciding it would be the
     * second copy.
     */
    willOnlyClauses: List<TraitClause> = emptyList(),
    /**
     * Combat Reflexes' "+6 to recover from stun", already resolved.
     *
     * Whether it APPLIES is still the rules' call, not this screen's:
     * [FrightCheck.recoveryBonus] gates it on the row's own state, so row 12's
     * Retching, Ecstasy and Dazed get nothing.
     */
    stunRecoveryBonus: Int = 0,
    shock: Int = 0,
    /** Reeling and fatigue: "-1 to all rolls", the phrase shock is read by. */
    hurt: Int = 0,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(FrightKind.FRIGHT) }
    var outcome by remember { mutableStateOf<FrightOutcome?>(null) }
    var picked by remember { mutableStateOf<FrightTrigger?>(null) }
    var modifier by remember { mutableIntStateOf(0) }
    // Which circumstances the GM says hold. Keyed on the clause, because one
    // cell prints two ("-2 on direct exposure; -1 in consecrated buildings")
    // and a key that could not tell them apart would move both at once (6r).
    var declared by remember(clauses) { mutableStateOf(emptySet<String>()) }
    // Keyed on the check, so a new Fright Check clears the last one's recovery
    // roll without a second clearing site (6cp: the key is the guard).
    var recovery by remember(outcome) { mutableStateOf<RollResult?>(null) }
    val triggers = remember { FrightCheck.triggers() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(outcome?.let { if (it.passed) "Held their nerve" else "Fright Check failed" } ?: "Fright Check") },
        text = {
            Column {
                val result = outcome
                val chosen = picked
                if (result == null && chosen != null) {
                    // Three of the book's five rows do not print a single
                    // number -- "-2 to -6" and "-8 or worse" are severities the
                    // GM chooses. Rolling the gentlest end for them would
                    // quietly turn their failures into successes.
                    Text(chosen.trigger, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "the book prints ${chosen.printed}" +
                            if (chosen.openEnded) " — no floor, so worse is legal" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { modifier -= 1 }) { Text("−1") }
                        Text(
                            (if (modifier > 0) "+" else "") + modifier,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        TextButton(onClick = { modifier += 1 }) { Text("+1") }
                    }
                    // The character's own traits. Unconditional clauses are
                    // counted without asking; each conditional one is a chip,
                    // because whether this character is in a church or facing
                    // the thing they fear is the GM's to say and this app
                    // holds no scene.
                    ClauseChips(clauses, declared) { declared = it }
                    // ONE value, displayed and rolled: computing the caption
                    // from one expression and the roll from another is how
                    // these numbers stopped reconciling (6ag, 6ai). The trait
                    // total comes from rules for the same reason -- a screen
                    // that summed its own chips would be the second copy.
                    val fromTraits = TraitClauses.clauseBonus(clauses, declared)
                    val effective = modifier + shock + hurt + fromTraits.total
                    // Composed once: the caption and the roll are this value.
                    val target = FrightCheck.targetFor(will, effective)
                    Text("3d6 vs $target", style = MaterialTheme.typography.bodySmall)
                    if (shock != 0) {
                        Text(
                            "includes $shock from shock — \"${Shock.printed()}\"",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (hurt != 0) {
                        Text(
                            "includes $hurt from the HP and FP trackers",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    fromTraits.line?.let {
                        Text("includes $it", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        // Guarded rather than relying on the button leaving the
                        // tree: two taps before a recomposition would otherwise
                        // roll twice and keep the second, the re-entrancy the
                        // divination dialog was hardened for (6p).
                        enabled = outcome == null,
                        onClick = {
                            if (outcome == null) {
                                outcome = FrightCheck.rollAgainst(target, appDice)
                            }
                        },
                    ) {
                        Text("Roll")
                    }
                } else if (result == null) {
                    Text("3d6 vs Will $will, modified by what was witnessed.")
                    Row {
                        FrightKind.entries.forEach { option ->
                            TextButton(onClick = { kind = option }) {
                                Text(
                                    option.name.lowercase(),
                                    color = if (option == kind) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                    triggers.forEach { trigger ->
                        Text(
                            "${trigger.trigger} (${trigger.printed})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    picked = trigger
                                    modifier = trigger.modifier
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                } else {
                    Text(
                        "3d6: ${result.roll.total} vs ${result.target}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (!result.passed) {
                        Text("failed by ${result.margin}; effect roll ${result.effectRoll}")
                        Text(
                            "${result.effect?.printed}: ${result.effect?.let { FrightCheck.text(it, kind) }}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        // One band works its duration out from the character
                        // ("Retching for (25 - HT) sec"); the other two that
                        // print a duration already print a number. Null
                        // elsewhere, so no line appears.
                        result.effect?.let { FrightCheck.durationFor(it, kind) }?.let { held ->
                            Text(
                                FrightCheck.durationLine(held, ht = ht, will = will, iq = iq),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        // Six of the nine bands print no way out at all, so
                        // this is null for them and no control appears. The
                        // cell's own sentence above already said so.
                        result.effect?.let { FrightCheck.recoveryFor(it, kind) }?.let { out ->
                            FrightRecoveryPanel(
                                recovery = out,
                                // Both of these are the rules' call, asked once
                                // each: the column decides the attribute, and
                                // the same fact decides which clause list can
                                // reach the roll.
                                attributeLevel = FrightCheck.levelFor(out, will, iq),
                                bonus = if (StunRecovery.isStun(out)) stunRecoveryBonus else 0,
                                clauseBonus = if (FrightCheck.takesWillClauses(out)) {
                                    TraitClauses.clauseBonus(willOnlyClauses, declared)
                                } else {
                                    ClauseBonus(0, null)
                                },
                                shock = shock,
                                hurt = hurt,
                                rolled = recovery,
                                onRoll = { target ->
                                    // One roll a tap: a second from the same
                                    // picture would roll again (6p).
                                    if (recovery == null) recovery = SuccessRoll.against(target, appDice)
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

/**
 * The way out of a Fright Effects row, where the table prints one.
 *
 * Three of the nine bands do. The other six hand out a quirk, a disadvantage
 * or a permanent loss with no roll to escape, so nothing renders for them —
 * a control there would grant a way back the book withholds.
 *
 * **One roll, not one a turn.** Rows 4-9 say "roll vs. Will each sec to
 * recover" and this app holds no clock, so the button says "now" and the
 * cell's own sentence — already printed above — is what says the book asks
 * again. The same reading `DamageDialog` makes of the Major Wound's stun.
 *
 * The duration each cell prints is shown and never computed: row 12 counts
 * down from "(25 − HT)", "(25 − Will)" and "(25 − IQ)", and two of those three
 * are not the attribute that column rolls.
 */
@Composable
private fun FrightRecoveryPanel(
    recovery: FrightRecovery,
    attributeLevel: Int,
    bonus: Int,
    clauseBonus: ClauseBonus,
    shock: Int,
    hurt: Int,
    rolled: RollResult?,
    onRoll: (Int) -> Unit,
) {
    // ONE expression, printed and rolled, for the reason the Major Wound panel
    // states: two would let the caption and the roll drift.
    val target = attributeLevel + bonus + clauseBonus.total + shock + hurt
    Column(Modifier.padding(top = 8.dp)) {
        if (rolled == null) {
            TextButton(onClick = { onRoll(target) }) {
                Text("Roll ${recovery.attribute} $target to recover now")
            }
            if (bonus != 0) {
                Text(
                    "includes +$bonus from ${TraitEffects.COMBAT_REFLEXES} — " +
                        "\"+6 to recover from stun\"",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            clauseBonus.line?.let {
                Text("includes $it", style = MaterialTheme.typography.bodySmall)
            }
            return@Column
        }
        // The roll's OWN target and verdict, never the recomputed ones: a
        // clause chip or a tracker moving while the result is on screen would
        // otherwise reprint an old roll against a number it was not made
        // against (Codex).
        Text("3d6: ${rolled.total} vs ${rolled.target}", style = MaterialTheme.typography.titleMedium)
        Text(
            if (rolled.outcome.isSuccess) "Recovered." else "Still there; the row asks again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
