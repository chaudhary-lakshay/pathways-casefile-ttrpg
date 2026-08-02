package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.Boons
import com.pathways.rules.RandomSource
import com.pathways.rules.RitualOutcome
import com.pathways.rules.SuccessRoll
import com.pathways.rules.content.BoonModifier

private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"

/**
 * The number to roll under, and -- when the boon-level doubling has driven it
 * below what 3d6 can show -- the exception that still gives it a chance.
 *
 * A bare "3d6 <= -512" reads as impossible. It is not: [SuccessRoll] treats a
 * rolled 3 or 4 as a critical success whatever the target, and Chapter 13
 * prints no override of that (Codex).
 */
internal fun targetLine(target: Int): String {
    val printed = "3d6 ≤ $target"
    if (target >= SuccessRoll.ALWAYS_SUCCEEDS_AT_OR_UNDER) return printed
    return "$printed — a rolled 3 or 4 still succeeds"
}

/** The ends a ranged option offers, nearer zero first ("+1 to +3" -> 1, 2, 3). */
private fun endsOf(row: BoonModifier): List<Int> {
    val best = row.best ?: return listOf(row.modifier)
    return if (best > row.modifier) (row.modifier..best).toList()
    else (row.modifier downTo best).toList()
}

/**
 * Petition a Hidden Existence for a boon (Ch. 13, p. 144-145).
 *
 * Reachable on the same gate as [RitualDialog] -- the chapter rolls
 * Ritualistic Magic, which no premade prints -- and resolved by the same
 * Chapter 7 machinery. What this dialog adds is the three things Chapter 13
 * puts on top: the sequence being asked for, the sacrifice, and how the
 * ritual itself was performed.
 *
 * The weight is NOT chosen here. The book fixes it at Moderate and the
 * "Heavy" it also prints is the running time, not the difficulty.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoonDialog(
    skillLevel: Int,
    conditionPenalty: Int,
    cor: Int,
    spiAvailable: Int,
    dice: RandomSource,
    onCorGained: (Int) -> Unit,
    onSpendSpi: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val granting = remember { Boons.granting() }
    val groups = remember { Boons.groups() }
    val byGroup = remember { Boons.modifiers().groupBy { it.group } }
    var sequence by remember { mutableIntStateOf(Boons.HIGHEST_SEQUENCE) }
    // The +0 row of each group: an unremarkable sacrifice and an ordinary
    // ritual. Defaulting to the best of either would hand it out for free,
    // the same reason preparation starts at Adequate in RitualDialog.
    val chosen = remember {
        mutableStateMapOf<String, BoonModifier>().apply {
            groups.forEach { group ->
                put(group, byGroup.getValue(group).first { it.modifier == 0 })
            }
        }
    }
    // What the GM picked inside a range, by BoonModifier.key -- never by
    // label, which the two groups are free to share.
    val picked = remember { mutableStateMapOf<String, Int>() }
    var spiChoice by remember { mutableIntStateOf(granting.spi) }
    var outcome by remember { mutableStateOf<RitualOutcome?>(null) }

    val picks = groups.mapNotNull { chosen[it] }
    // Only the options actually chosen right now. Filtering here rather than
    // pruning [picked] on every change means no path can leave a value behind
    // to modify a later roll -- the defect the attack dialog's "Another turn"
    // shipped with.
    val overrides = picks.mapNotNull { row -> picked[row.key]?.let { row.key to it } }.toMap()
    val effectiveSkill = skillLevel + conditionPenalty
    val target = Boons.targetFor(effectiveSkill, cor, sequence, picks, overrides)
    val cost = Boons.spiFor(spiChoice)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Text(
                outcome?.let { if (it.succeeded) "The boon is granted" else "The prayer fails" }
                    ?: "Petition for a boon",
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val result = outcome
                if (result == null) {
                    Text("Boon level", style = MaterialTheme.typography.titleSmall)
                    FlowRow {
                        (Boons.HIGHEST_SEQUENCE downTo Boons.LOWEST_SEQUENCE).forEach { option ->
                            FilterChip(
                                selected = option == sequence,
                                onClick = { sequence = option },
                                label = { Text("Seq $option") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Text(
                        "Sequence $sequence: ${signed(Boons.penaltyForSequence(sequence))}. " +
                            granting.boonLevelRule,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    groups.forEach { group ->
                        Text(
                            group,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        FlowRow {
                            byGroup.getValue(group).forEach { option ->
                                FilterChip(
                                    selected = chosen[group]?.label == option.label,
                                    onClick = { chosen[group] = option },
                                    label = { Text("${option.label} (${option.printed})") },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                        // Three of the eight options print a range rather than
                        // a number, and the end is the GM's; the default is
                        // the end nearer zero.
                        val selected = chosen[group]
                        if (selected?.best != null) {
                            FlowRow {
                                endsOf(selected).forEach { value ->
                                    FilterChip(
                                        selected =
                                            (picked[selected.key] ?: selected.modifier) == value,
                                        onClick = { picked[selected.key] = value },
                                        label = { Text(signed(value)) },
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        "Fuel",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    FlowRow {
                        (granting.spi..granting.spiMax).forEach { option ->
                            FilterChip(
                                selected = option == spiChoice,
                                onClick = { spiChoice = option },
                                label = { Text("$option SPI") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }

                    Text(
                        targetLine(target),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "${granting.skill} $skillLevel, ${granting.weight} " +
                            "${granting.weightModifier}" +
                            ", Sequence $sequence ${signed(Boons.penaltyForSequence(sequence))}" +
                            ", circumstances ${signed(Boons.modifierTotal(picks, overrides))}" +
                            (if (conditionPenalty != 0) ", $conditionPenalty hurt or tired" else "") +
                            (if (cor >= 3) ", -1 for $cor CoR" else "") +
                            ". Costs $cost SPI, takes ${granting.complexityTime}.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (cost > spiAvailable) {
                        Text(
                            "Only $spiAvailable SPI left — going below 0 is Soul Debt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "Bring:",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    granting.requirements.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = {
                            // One Pray spends once: a second tap before the
                            // button leaves the tree charges the fuel again,
                            // and below 0 that buys permanent CoR.
                            if (outcome != null) return@TextButton
                            val rolled = Boons.attempt(
                                skillLevel = effectiveSkill,
                                cor = cor,
                                sequence = sequence,
                                source = dice,
                                chosen = picks,
                                gmValue = overrides,
                            )
                            outcome = rolled
                            // Spent whether or not the Great One answers:
                            // "No effect. SPI and materials spent" (p. 62).
                            onSpendSpi(cost)
                            rolled.critical?.corGained?.let(onCorGained)
                        },
                    ) { Text("Pray") }
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
                    if (result.succeeded) {
                        // Section IV. None of it is a number, and the book
                        // hands all of it to the GM -- but a boon accepted
                        // without them on screen is a bargain half read.
                        Text(
                            "Sequence $sequence granted. The patron is aware of you now:",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        granting.consequences.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
    )
}
