package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import com.pathways.rules.SelfControl
import com.pathways.rules.SelfControlCheck
import com.pathways.rules.SelfControlOutcome
import com.pathways.rules.TraitClause
import com.pathways.rules.TraitEffects

/**
 * The conditional clauses a character carries, each one a chip the GM turns on.
 *
 * One composable for all three bare Will rolls (self-control, Concentrate, the
 * Fright Check) plus the Fright Check's own rows. Unconditional clauses never
 * appear: they are what the character always is, so [TraitEffects.clauseBonus]
 * counts them whether or not anyone declared them, and a chip that changed
 * nothing would read as one that did.
 */
@Composable
internal fun ClauseChips(
    clauses: List<TraitClause>,
    declared: Set<String>,
    onDeclared: (Set<String>) -> Unit,
) {
    val conditional = clauses.filter { it.conditional }
    if (conditional.isEmpty()) return
    Text(
        "Does any of this hold?",
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow {
        conditional.forEach { clause ->
            TextButton(onClick = {
                onDeclared(
                    if (clause.key in declared) declared - clause.key else declared + clause.key,
                )
            }) {
                Text(
                    "${clause.shown}: ${clause.printed}",
                    color = if (clause.key in declared) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * One self-control roll at the table (Ch. 3, 6am).
 *
 * The consequence is [SelfControlCheck.printed] verbatim. Three sentence
 * shapes -- "or investigate any mystery", "to avoid angry outbursts", "to
 * undertake any sustained effort" -- mean a paraphrase would get one of them
 * backwards, so the book's own cell is quoted and nothing is generated.
 *
 * ponytail: one stepper, the [FrightDialog] idiom, and no skill-roll ledger,
 * because a Fright Check takes a GM modifier and no ledger either, and two
 * Will rolls behaving differently in one app is worse than either choice on
 * its own. Shock and reeling or fatigue do reach it, as they reach a Fright
 * Check: they arrive inside [standing].
 */
@Composable
internal fun SelfControlDialog(
    check: SelfControlCheck,
    /**
     * The target the row that opened this printed ([standingTargets]): the
     * book's number, with shock and reeling or fatigue ("-1 to all rolls")
     * already on it.
     */
    standing: Int,
    /** The character's own Will clauses ([TraitEffects.willClauses]). */
    clauses: List<TraitClause> = emptyList(),
    onDismiss: () -> Unit,
) {
    // Keyed on the check: a modifier left over from another disadvantage
    // would silently move this one's target -- the stale-state family 6k, 6y
    // and 6af each found once.
    var modifier by remember(check) { mutableIntStateOf(0) }
    var outcome by remember(check) { mutableStateOf<SelfControlOutcome?>(null) }
    // Keyed on the check for the same reason the modifier is: a circumstance
    // declared for one disadvantage is not declared for the next. Keyed on
    // [clauses] as well, because a declaration is a set of clause KEYS and a
    // changed trait list can retire the key one names -- the stale-state
    // family 6k, 6y and 6af each found once (Codex).
    var declared by remember(check, clauses) { mutableStateOf(emptySet<String>()) }
    // The book's number, the shock and the reeling are in [standing], which
    // the row that opened this printed. What is added here is only what is
    // asked here: the GM's stepper and the clauses declared below.
    // The trait total comes from rules, not from summing the chips here: a
    // screen that added them up would be the second copy of a rule that
    // already has one (6ag, 6ai).
    //
    // Read ONCE, by both the caption and the Roll handler. A review proposed
    // recomputing it inside onClick against a stale capture between a chip
    // tap and a roll tap; refused, because that is a second call site for the
    // number the caption prints, which is the drift the same review certified
    // absent. The refusal stands and its second reason did not: this said
    // "Compose recomposes on the state write, so the two taps are two
    // frames", and two taps a few milliseconds apart reached one
    // composition's handler on an SM-T570 (6ei). What makes a chip tap and a
    // roll tap safe here is that the handler rolls the `target` printed
    // below and reads nothing else (6eh). Two taps on the ROLL button are
    // guarded inline, the fix 6p needed.
    val fromTraits = TraitEffects.clauseBonus(clauses, declared)
    // On top of the row's own number, so the two cannot open apart.
    val target = standing + modifier + fromTraits.total

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Text(
                outcome?.let { if (it.held) "Held" else "Gave in" } ?: check.trait,
            )
        },
        text = {
            Column {
                // Quoted once, and turning red on a failure IS the
                // consequence -- printing it twice would read as two rules.
                Text(
                    check.printed,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (outcome?.held == false) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    check.spec.attribute?.let {
                        "keyed on $it${if (check.spec.offset > 0) "+" else ""}" +
                            "${if (check.spec.offset != 0) "${check.spec.offset}" else ""}" +
                            ", after any potion"
                    } ?: "a flat number the book prints — no attribute moves it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                val result = outcome
                if (result == null) {
                    ClauseChips(clauses, declared) { declared = it }
                    fromTraits.line?.let {
                        Text("includes $it", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { modifier -= 1 }) { Text("−1") }
                        Text(
                            (if (modifier > 0) "+" else "") + modifier,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        TextButton(onClick = { modifier += 1 }) { Text("+1") }
                    }
                    Text("3d6 ≤ $target", style = MaterialTheme.typography.titleLarge)
                    // Replaced by the result, AND guarded inline: two taps can
                    // both reach the handler before the button leaves the
                    // tree, which is the fix 6p needed for the divination
                    // dialog. Nothing is charged here, but the second tap
                    // would silently discard a rolled outcome and pull an
                    // extra draw from the dice.
                    TextButton(
                        onClick = {
                            if (outcome != null) return@TextButton
                            outcome = SelfControl.rollAgainst(check, target, appDice)
                        },
                    ) { Text("Roll") }
                } else {
                    Text(
                        "3d6: ${result.roll.total} vs ${result.target}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        },
    )
}
