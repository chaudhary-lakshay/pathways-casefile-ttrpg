package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.pathways.model.Trait
import com.pathways.rules.AttributeRoll
import com.pathways.rules.ClauseBonus
import com.pathways.rules.SuccessRoll
import com.pathways.rules.TraitClauses

/**
 * A bare Will or Per roll, from its row on the Vitals tab (play session 4:
 * the book asks for these, a Sealed Artifact's daily Will roll among them,
 * and nothing could make one). [AttributeRoll] composes the target; the GM's
 * figure is the stepper, as the daily recovery roll's is.
 */
@Composable
internal fun AttributeRollDialog(
    kind: AttributeRoll.Kind,
    level: Int,
    traits: List<Trait>,
    shock: Int,
    hurt: Int,
    /** The held rows' declared "-N to all rolls" (6gs); neither roll is physical. */
    states: ClauseBonus,
    onDismiss: () -> Unit,
) {
    val dice = LocalDice.current
    val clauses = remember(kind, traits) { AttributeRoll.clauses(kind, traits) }
    var declared by remember(clauses) { mutableStateOf(emptySet<String>()) }
    var gm by remember { mutableIntStateOf(0) }
    var outcome by remember { mutableStateOf<RollResult?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { CloseButton(onDismiss) },
        dismissButton = {
            if (outcome != null) CloseButton({ outcome = null }, "Roll again", MaterialTheme.colorScheme.onSurface)
        },
        title = { FormTitle(FORM_ATTRIBUTE) { Text("${kind.label} roll") } },
        text = steadyBody {
            val result = outcome
            if (result != null) {
                VerdictCard(label(result.outcome), result.outcome.isSuccess, result)
                return@steadyBody
            }
            val fromTraits = TraitClauses.clauseBonus(clauses, declared)
            val target = AttributeRoll.targetFor(kind, level, traits, shock, hurt, fromTraits.total + states.total + gm)
            Column {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    TargetCard(target, baseTarget = level)
                    ClauseChips(clauses, declared) { declared = it }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text("GM", style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { gm -= 1 }) { Text("−1") }
                        Text(signed(gm), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { gm += 1 }) { Text("+1") }
                    }
                }
                RollFooter(
                    AttributeRoll.partsFor(kind, level, traits, shock, hurt) + listOfNotNull(
                        fromTraits.line,
                        states.line,
                        gm.takeIf { it != 0 }?.let { "GM ${signed(it)}" },
                    ),
                    caption = SuccessRoll.printed(target),
                    target = target,
                ) {
                    // A one-shot: a second tap of this picture is dropped (6ei).
                    if (outcome != null) return@RollFooter
                    outcome = SuccessRoll.against(target, dice.dice("${kind.label} roll"))
                }
            }
        },
    )
}
