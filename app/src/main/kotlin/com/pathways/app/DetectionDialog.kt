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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.AntiDivinationTechniques
import com.pathways.rules.Detection
import com.pathways.rules.DetectionResult
import com.pathways.rules.RandomSource
import com.pathways.rules.content.DivinationAwareness

/**
 * Feeling a divination land on you (Ch. 6.5, Sec. 1, 2 and 4).
 *
 * The other side of [DivinationDialog], and a separate screen for a reason:
 * the roll is the TARGET's, and this app holds one character. In the
 * divination dialog that character is the diviner and the target is a declared
 * Sequence with no sheet; here the character IS the target, so their own SPI
 * and Per are the roll.
 *
 * Nothing gates who may open it. Sec. 2 scopes itself to Beyonders in prose,
 * but the roll it defines is SPI or Per whichever is higher and a mortal has a
 * Per -- who suspects they are watched is the GM's call, the same reading that
 * left the Sealed Artifact grade table unenforced.
 *
 * The Sec. 1 row is a GATE first: it says whether there is a roll at all. A
 * row granting automatic awareness withdraws the Sense button, because the
 * book gives that character the answer without dice and rolling anyway could
 * take it away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetectionDialog(
    spi: Int,
    per: Int,
    dice: RandomSource,
    onDismiss: () -> Unit,
) {
    val awarenessRows = remember { Detection.awareness() }
    val situations = remember { Detection.modifiers() }
    // Only the rows carrying a number reach this roll. The candle's -3 is
    // about sensing a RITUAL and the other four items do something else
    // entirely, so Detection refuses to sum them.
    val items = remember { Detection.items().filter { it.detectionBonus != null } }
    val wards = remember {
        AntiDivinationTechniques.all().filter { it.detectionBonus != null }
    }

    var awareness by remember { mutableStateOf<DivinationAwareness?>(null) }
    // Three of the four printed bonuses are scoped to what the divination is
    // ABOUT, so the bonus is offered rather than applied.
    var takeBonus by remember(awareness) { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Set<String>>(emptySet()) }
    var wardIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var carried by remember { mutableStateOf<Set<String>>(emptySet()) }
    var result by remember { mutableStateOf<DetectionResult?>(null) }

    val pickedWards = wards.filter { it.id in wardIds }
    val pickedItems = items.filter { it.name in carried }
    // One charm, priced generally in Sec. 2 and specifically in Sec. 4.
    // Detection throws on the pair rather than summing it, so the general chip
    // withdraws instead of offering a throw -- the lesson the two Aim rows
    // taught: a chip that cannot be taken is not a choice.
    val charmTaken = pickedItems.isNotEmpty()
    val pickedSituations = situations.filter {
        it.situation in chosen &&
            !(charmTaken && it.situation.equals(Detection.GENERIC_CHARM, ignoreCase = true))
    }
    val bonus = if (takeBonus) awareness?.bonus ?: 0 else 0
    val modifier = Detection.modifierTotal(
        situations = pickedSituations,
        techniques = pickedWards,
        items = pickedItems,
        awarenessBonus = bonus,
    )
    // The row's own roll, when it grants one: four rows name an attribute and
    // it is not always the higher of the two, so the roll a Fool is granted is
    // a Per roll even when their SPI is better. See Detection.attributeFor.
    val freeRoll = awareness?.freeRoll
    val target = Detection.targetFor(spi, per, modifier, freeRoll)
    val automatic = awareness?.isAutomatic == true

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (result != null) {
                // The answer belongs to the divination it was rolled against.
                TextButton(onClick = { result = null }) { Text("Sense again") }
            }
        },
        title = { Text("Am I being divined?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val answer = result
                if (answer != null) {
                    Text(
                        "3d6: ${answer.roll.total} vs ${answer.roll.target} — " +
                            answer.row.result,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(answer.row.effect, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        answer.parts.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    return@Column
                }

                Text(
                    "Pathway awareness (Sec. 1)",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Some pathways feel it without rolling. Leave this off if " +
                        "yours is not listed.",
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow {
                    FilterChip(
                        selected = awareness == null,
                        onClick = { awareness = null },
                        label = { Text("None") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    awarenessRows.forEach { row ->
                        FilterChip(
                            selected = awareness == row,
                            onClick = { awareness = row },
                            label = { Text(row.pathway.substringBefore(" (")) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                awareness?.let { row ->
                    Text(
                        "${row.sequence} — ${row.detection}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    row.divinerPenalty?.let {
                        // A row inside the awareness table that grants no
                        // awareness. Quoted, never applied: it is the DIVINER's
                        // penalty, and at the GM's discretion.
                        Text(
                            "This row grants no detection roll. Its ${signedInt(it)} " +
                                "is on the diviner's roll, at the GM's discretion.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    row.bonus?.let { value ->
                        FilterChip(
                            selected = takeBonus,
                            onClick = { takeBonus = !takeBonus },
                            label = { Text("Take ${signedInt(value)}") },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        row.bonusText?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Text(
                    "Situation (Sec. 2)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow {
                    situations.forEach { row ->
                        val superseded =
                            charmTaken && row.situation.equals(Detection.GENERIC_CHARM, ignoreCase = true)
                        FilterChip(
                            selected = row.situation in chosen && !superseded,
                            enabled = !superseded,
                            onClick = {
                                chosen =
                                    if (row.situation in chosen) chosen - row.situation
                                    else chosen + row.situation
                            },
                            label = { Text("${row.situation} ${signedInt(row.modifier)}") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                if (charmTaken) {
                    Text(
                        "The charm you named prices itself, so Sec. 2's general " +
                            "charm row does not also apply.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text(
                    "Your own protection (Sec. 3, 4)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "The SPI a technique costs is spent on the tracker; nothing " +
                        "here holds a clock for its duration.",
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow {
                    wards.forEach { row ->
                        FilterChip(
                            selected = row.id in wardIds,
                            onClick = {
                                wardIds =
                                    if (row.id in wardIds) wardIds - row.id
                                    else wardIds + row.id
                            },
                            label = {
                                Text("${row.name} ${signedInt(row.detectionBonus ?: 0)}")
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    items.forEach { row ->
                        FilterChip(
                            selected = row.name in carried,
                            onClick = {
                                carried =
                                    if (row.name in carried) carried - row.name
                                    else carried + row.name
                            },
                            label = {
                                Text(
                                    row.name.substringBefore(" (") + " " +
                                        signedInt(row.detectionBonus ?: 0),
                                )
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (automatic) {
                    Text(
                        "Automatic awareness — no roll.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        awareness?.detection.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "Roll 3d6 ≤ $target",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        // The rules object authors this sentence, because it is
                        // the one the target above is computed from. A second
                        // copy here is how the caption came to say "the higher
                        // of SPI and Per" over a target taken from the row's
                        // own attribute.
                        Detection.attributeLine(spi, per, freeRoll) +
                            (if (modifier != 0) ", ${signedInt(modifier)}" else "") +
                            ". Free action, costs no SPI.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = {
                            // One roll per sensing; the re-entrancy guard the
                            // divination and ritual dialogs both needed.
                            if (result != null) return@TextButton
                            result = Detection.attempt(
                                spi = spi,
                                per = per,
                                situations = pickedSituations,
                                techniques = pickedWards,
                                items = pickedItems,
                                awarenessBonus = bonus,
                                awarenessLabel = awareness?.pathway,
                                freeRoll = freeRoll,
                                source = dice,
                            )
                        },
                    ) { Text("Sense") }
                }
            }
        },
    )
}

private fun signedInt(n: Int) = if (n >= 0) "+$n" else "$n"
