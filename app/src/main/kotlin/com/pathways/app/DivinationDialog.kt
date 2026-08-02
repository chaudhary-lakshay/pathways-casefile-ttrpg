package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.Divination
import com.pathways.rules.DivinationOutcome
import com.pathways.rules.RandomSource
import com.pathways.rules.RitualResolution
import com.pathways.rules.content.DivinationMethod

/**
 * One divination, in the book's four steps (Ch. 6.5, Sec. III).
 *
 * State the question's difficulty, choose a method, tick the circumstances
 * that apply, and roll. [Divination] does the arithmetic; what this adds is
 * the order, and the fact that the SPI is actually SPENT -- a divination that
 * costs nothing is the same defect the Soul Debt slice fixed one pool over.
 *
 * The Step 3 table's first row is deliberately not offered: "Question
 * difficulty / See Step 1 table" is a pointer at Step 1, which the chips above
 * already apply, and [Divination.modifierTotal] refuses it rather than letting
 * a caller charge the question twice.
 *
 * Rows printing a range ("+0 to +4") are applied at the end nearer zero, which
 * is the gentle one. The GM's harsher or kinder call is theirs to make out
 * loud -- the same reading the fright triggers and the darkness row settled.
 * ponytail: a stepper per range row when a GM asks for one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DivinationDialog(
    skillLevel: Int,
    conditionPenalty: Int,
    spiAvailable: Int,
    dice: RandomSource,
    onSpendSpi: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val difficulties = remember { Divination.difficulties() }
    val methods = remember { Divination.methods() }
    // The cross-reference row is not a modifier; Divination refuses it.
    val rows = remember { Divination.modifiers().filterNot { it.crossReference } }

    var difficulty by remember { mutableStateOf(difficulties.first()) }
    var method by remember { mutableStateOf(methods.first()) }
    var chosen by remember { mutableStateOf<Set<String>>(emptySet()) }
    var outcome by remember { mutableStateOf<DivinationOutcome?>(null) }
    var backlash by remember { mutableStateOf<String?>(null) }
    // Dowsing Rods prints "1-2 SPI", so the end is the GM's to pick. Reset
    // with the method, or a 2 chosen for the rods would follow them onto a
    // method that costs 1 (Codex).
    var spiChoice by remember(method) { mutableIntStateOf(method.spi) }

    val extras = Divination.modifierTotal(rows.filter { it.source in chosen }) + conditionPenalty
    val target = Divination.targetFor(skillLevel, difficulty, extras)
    val cost = Divination.spiFor(method, spiChoice)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (outcome != null) {
                TextButton(
                    onClick = {
                        // The backlash belongs to the roll that caused it;
                        // carrying it into the next question would report a
                        // critical the diviner never rolled.
                        outcome = null
                        backlash = null
                    },
                ) { Text("Ask again") }
            }
        },
        title = { Text("Divination Arts $skillLevel") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val answer = outcome
                if (answer != null) {
                    Result(answer, backlash)
                    return@Column
                }
                Text("Question", style = MaterialTheme.typography.titleSmall)
                FlowRow {
                    difficulties.forEach { option ->
                        FilterChip(
                            selected = option == difficulty,
                            onClick = { difficulty = option },
                            label = {
                                Text("${option.band.substringBefore(" ")} ${option.printedModifier}")
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Text(difficulty.example, style = MaterialTheme.typography.bodySmall)

                Text(
                    "Method",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow {
                    methods.forEach { option ->
                        FilterChip(
                            selected = option == method,
                            onClick = { method = option },
                            label = { Text("${option.method} ${spiLabel(option)}") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Text(
                    "${method.time}, ${spiLabel(method)} SPI — ${method.bestFor}",
                    style = MaterialTheme.typography.bodySmall,
                )
                method.spiMax?.let { most ->
                    // The one method whose cost is a range. Showing it while
                    // always charging the low end is how the app undercharges
                    // a debt the book makes permanent.
                    FlowRow {
                        (method.spi..most).forEach { option ->
                            FilterChip(
                                selected = option == spiChoice,
                                onClick = { spiChoice = option },
                                label = { Text("$option SPI") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }

                Text(
                    "Circumstances",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow {
                    rows.forEach { option ->
                        FilterChip(
                            selected = option.source in chosen,
                            onClick = {
                                chosen = if (option.source in chosen) {
                                    chosen - option.source
                                } else {
                                    chosen + option.source
                                }
                            },
                            label = {
                                Text("${option.source.substringBefore(" (")} ${option.printed}")
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Roll 3d6 ≤ $target", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Skill $skillLevel, question ${signed(difficulty.modifier)}" +
                        (if (extras != 0) ", circumstances ${signed(extras)}" else "") +
                        // What will actually be CHARGED, not the printed
                        // range: the range is above, beside the chips that
                        // choose inside it.
                        ". Costs $cost SPI.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (cost > spiAvailable) {
                    Text(
                        "Only $spiAvailable SPI left — going below 0 is Soul Debt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(
                    onClick = {
                        // One Ask spends once. Two taps can reach this handler
                        // before the button leaves the tree, and the second
                        // would charge the SPI again -- which below 0 buys
                        // Soul Debt, and that CoR never comes back (Codex).
                        if (outcome != null) return@TextButton
                        val result = Divination.attempt(skillLevel, difficulty, dice, extras)
                        outcome = result
                        // The clarity row prints no effects of its own: it says
                        // "treat as a ritual critical failure", so the ritual
                        // table is ROLLED rather than copied -- off the same
                        // injected source, never Random directly.
                        backlash = if (result.isCritical) {
                            RitualResolution.criticalFor((1..3).sumOf { dice.roll(6) }).outcome
                        } else {
                            null
                        }
                        onSpendSpi(cost)
                    },
                ) { Text("Ask") }
            }
        },
    )
}

@Composable
private fun Result(outcome: DivinationOutcome, backlash: String?) {
    Text(
        "3d6: ${outcome.roll.total} vs ${outcome.roll.target}",
        style = MaterialTheme.typography.titleLarge,
    )
    Text(
        "${outcome.clarity.result} — ${outcome.clarity.clarity}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(outcome.clarity.effect, style = MaterialTheme.typography.bodySmall)
    backlash?.let {
        Text(
            "Critical Failure Table: $it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** "1", or "1-2" for the one method whose cost the GM picks inside. */
private fun spiLabel(method: DivinationMethod): String =
    method.spiMax?.let { "${method.spi}-$it" } ?: "${method.spi}"

private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"
