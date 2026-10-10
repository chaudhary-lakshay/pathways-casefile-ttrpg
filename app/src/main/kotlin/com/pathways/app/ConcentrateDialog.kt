package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.Concentration
import com.pathways.rules.ConcentrationOutcome
import com.pathways.rules.TraitClause
import com.pathways.rules.TraitClauses

/**
 * The Concentrate maneuver's own roll (Ch. 5, p. 36).
 *
 * **The roll is conditional, so the button is withdrawn until it is asked
 * for.** "Roll Will-3 if injured or defending" -- a caster concentrating in
 * peace rolls nothing, and offering the roll anyway would tax every act of
 * focus in the book. [Concentration.attempt] refuses that call outright, so a
 * Roll button here would be one that throws (6t).
 *
 * One chip, because the book prints one sentence, one number and one effect
 * for both circumstances -- unlike the three defence refusals, which print
 * three sentences and where a trait cancels exactly one (6bg).
 */
@Composable
internal fun ConcentrateDialog(
    will: Int,
    /** The character's clauses for this roll ([TraitClauses.concentrationClauses]). */
    clauses: List<TraitClause> = emptyList(),
    shock: Int = 0,
    hurt: Int = 0,
    onDismiss: () -> Unit,
) {
    var disturbed by remember { mutableStateOf(false) }
    // Keyed on [clauses] and NOT on [disturbed]: a changed trait list retires
    // the keys a declaration names (Codex), while whether a Beyonder ability
    // disturbed the focus is the same fact before and after the GM says the
    // character is injured -- clearing it there would make the player
    // re-declare something they already said.
    var declared by remember(clauses) { mutableStateOf(emptySet<String>()) }
    // Cleared with the declaration, because an outcome belongs to the
    // circumstance it was rolled under -- the rule the Major Wound's HT roll
    // (6n) and the entity's answer (6z) both follow.
    var outcome by remember(disturbed) { mutableStateOf<ConcentrationOutcome?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(outcome?.let { if (it.held) "Held the focus" else "Lost the focus" } ?: "Concentrate") },
        text = {
            Column {
                Text(Concentration.printed(), style = MaterialTheme.typography.bodySmall)
                FilterChip(
                    selected = disturbed,
                    onClick = { disturbed = !disturbed },
                    label = { Text("Injured or defending") },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                val result = outcome
                when {
                    !disturbed -> Text(
                        "No roll: the book asks for one only when the character " +
                            "is injured or defending.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    result == null -> {
                        ClauseChips(clauses, declared) { declared = it }
                        // From rules, and read by BOTH the caption and the
                        // roll below -- the two call sites this dialog has
                        // always had are why the term is computed once (6ai).
                        val fromTraits = TraitClauses.clauseBonus(clauses, declared)
                        val target = Concentration.targetFor(will, shock, hurt, fromTraits.total)
                        Text(
                            "3d6 ≤ $target" +
                                " — Will $will${Concentration.PENALTY}" +
                                (if (shock != 0) ", $shock shock" else "") +
                                (if (hurt != 0) ", $hurt hurt or tired" else "") +
                                (fromTraits.line?.let { ", $it" } ?: ""),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        // Guarded inline as well as by the tree: two taps can
                        // both reach the handler before it leaves (6p).
                        TextButton(
                            onClick = {
                                // `disturbed` is read here on purpose: it
                                // is whether the book calls for a roll at
                                // all, not a term of the target above.
                                if (outcome != null || !disturbed) return@TextButton
                                outcome = Concentration.rollAgainst(target, disturbed, appDice)
                            },
                        ) { Text("Roll") }
                    }
                    else -> {
                        Text(
                            "3d6: ${result.roll.total} vs ${result.target}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        // The book prints the roll and stops. Saying so beats
                        // inventing the only consequence in the rule (6aj).
                        if (!result.held) {
                            Text(
                                "The book prints no consequence for a lost focus — " +
                                    "what it costs is the GM's.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
    )
}
