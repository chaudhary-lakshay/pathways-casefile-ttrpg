package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.pathways.rules.RandomSource
import com.pathways.rules.RitualOutcome
import com.pathways.rules.RitualResolution

/** The skill every ritual is rolled against (Ch. 7). */
const val RITUAL_SKILL = "Ritualistic Magic"

/**
 * Cast a ritual (Ch. 7, p. 67-68).
 *
 * The caster's skill level is the character's own, so this dialog is only
 * reachable when they have the skill -- no premade prints it, which is why
 * buying it in the builder had to come first.
 *
 * Injury, fatigue and spiritual attrition arrive as [conditionPenalty] --
 * the same [com.pathways.rules.Conditions] the sheet's skill rolls use, minus
 * the corruption penalty, which [RitualResolution] applies itself.
 *
 * CoR is charged automatically on the three critical rows that print a cost
 * ("Corruption. Gain 1 CoR."), read by the extractor rather than scraped from
 * the text here. The rest of a critical result stays the GM's: an entity
 * noticing you is not a number.
 */
@Composable
fun RitualDialog(
    skillLevel: Int,
    conditionPenalty: Int,
    cor: Int,
    dice: RandomSource,
    onCorGained: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val weights = remember { RitualResolution.weights() }
    var weight by remember { mutableStateOf(weights.first()) }
    var outcome by remember { mutableStateOf<RitualOutcome?>(null) }
    // Injury and fatigue reach a ritual like any other roll; the CoR penalty
    // is NOT included in [conditionPenalty] because RitualResolution applies
    // it itself, and counting it twice would penalise a corrupted caster
    // once for the ladder and once again for the same ladder.
    val effectiveSkill = skillLevel + conditionPenalty
    val target = RitualResolution.targetFor(effectiveSkill, weight, cor)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Text(
                outcome?.let { if (it.succeeded) "The ritual holds" else "The ritual fails" }
                    ?: "Cast a ritual",
            )
        },
        text = {
            Column {
                val result = outcome
                if (result == null) {
                    Row {
                        weights.forEach { option ->
                            FilterChip(
                                selected = option.weight == weight.weight,
                                onClick = { weight = option },
                                label = { Text(option.weight) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Text(
                        weight.categories,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text("3d6 ≤ $target", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$RITUAL_SKILL $skillLevel, ${weight.weight} ${weight.modifier}" +
                            (if (conditionPenalty != 0) ", $conditionPenalty hurt or tired" else "") +
                            (if (cor >= 3) ", -1 for $cor CoR" else ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = {
                            val rolled = RitualResolution.attempt(effectiveSkill, weight, cor, dice)
                            outcome = rolled
                            rolled.critical?.corGained?.let(onCorGained)
                        },
                    ) { Text("Roll") }
                } else {
                    Text(
                        "3d6: ${result.roll.total} vs ${result.target}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    result.consequence?.let { consequence ->
                        Text(
                            "${consequence.printed} — ${consequence.consequence}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    result.critical?.let { critical ->
                        Text(
                            "Critical Failure Table ${result.criticalRoll} " +
                                "(${critical.printed}): ${critical.outcome}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        critical.corGained?.let {
                            Text("+$it CoR charged.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
    )
}
