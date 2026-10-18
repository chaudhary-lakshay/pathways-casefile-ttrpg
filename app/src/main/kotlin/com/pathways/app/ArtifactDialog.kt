package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.pathways.rules.Pools
import com.pathways.rules.SealedArtifacts
import com.pathways.rules.content.SealedArtifact

/**
 * One Sealed Artifact ability, used (Ch. 6, p. 47-53).
 *
 * Pick an artifact, pick an ability, read what the book says it does, and pay.
 * [SealedArtifacts] holds the costs; what this adds is that they are actually
 * SPENT -- an artifact whose SPI never leaves the pool is the defect the
 * divination and ritual slices each had to fix one subsystem over.
 *
 * **The grade is shown and never enforced.** "Power Equivalent (Seq 5-7)" is
 * how strong the ARTIFACT is; the wielder's Sequence is not tested against it,
 * because the only column that gates anything is Church Access and it gates on
 * institutional rank. A mortal may pick up a Grade 2 gauntlet here, which is
 * exactly how the book says artifacts are found.
 *
 * The 13 effects are unique prose -- duplicate an object, walk through a wall,
 * erase a memory -- so they are quoted rather than modelled. Only the pools
 * move. ponytail: no effect engine until an effect repeats.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtifactDialog(
    spiAvailable: Int,
    onSpendSpi: (Int) -> Unit,
    onSpendHp: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val artifacts = remember { SealedArtifacts.all() }
    var artifact by remember { mutableStateOf(artifacts.first()) }
    // Keyed on the artifact. An ability left selected across a switch would
    // make SealedArtifacts.costOf THROW, not merely mislead -- the stale-state
    // trap the ranged and ritual dialogs each found once.
    var ability by remember(artifact) { mutableStateOf(artifact.abilities.first()) }
    var used by remember(artifact, ability) { mutableStateOf<String?>(null) }

    val grade = SealedArtifacts.gradeOf(artifact)
    val cost = SealedArtifacts.costOf(artifact, ability)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            // "Spiritual Exhaustion: all Beyonder abilities deactivate; cannot
            // activate any SPI-cost abilities" -- an artifact's price is an
            // SPI cost like any other, so at 0 the button is withdrawn rather
            // than left to charge a debt the book does not allow.
            if (Pools.canAffordSpi(cost.spi, spiAvailable)) TextButton(
                onClick = {
                    // Two taps, always, to spend twice: the first clears the
                    // receipt and the second pays. So nothing charges the pool
                    // without the button having read "Use" when it was hit, and
                    // spending past 0 buys permanent CoR -- a double-spend here
                    // is not cosmetic (Codex, on divination).
                    if (used != null) {
                        used = null
                        return@TextButton
                    }
                    onSpendSpi(cost.spi)
                    if (cost.hp > 0) onSpendHp(cost.hp)
                    used = buildString {
                        append("Spent ${cost.spi} SPI")
                        if (cost.hp > 0) append(" and ${cost.hp} HP")
                        if (cost.perTurn) append(", one turn of maintenance")
                        append(".")
                    }
                },
                // False Majesty is "1 SPI/turn", so repeating IS its printed
                // rule. A guard clearable only by closing the dialog would make
                // the book's one maintained ability the hardest to use.
            ) { Text(if (used == null) "Use" else "Use again") }
        },
        dismissButton = { CloseButton(onDismiss) },
        title = { Text("Sealed Artifacts") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow {
                    artifacts.forEach { option ->
                        CaseChip(
                            selected = option == artifact,
                            onClick = { artifact = option },
                            label = { Text(shortName(option)) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Text(
                    "${artifact.name} — ${artifact.pathway} Pathway, " +
                        "Grade ${artifact.grade} (${grade.danger})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // Both printed columns, and which of them is a rule. Saying so
                // is cheaper than a Sequence check that would be wrong.
                Text(
                    "Power equivalent: ${grade.powerEquivalent} — how strong the " +
                        "artifact is, not what its wielder must be. " +
                        "Church access: ${grade.churchAccess}.",
                    style = MaterialTheme.typography.bodySmall,
                )
                // Lore: what it looks like and where it came from (6gl).
                RuleNote("Description") {
                    Text(artifact.appearance, style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Ability", style = MaterialTheme.typography.titleSmall)
                FlowRow {
                    artifact.abilities.forEach { option ->
                        CaseChip(
                            selected = option.name == ability.name,
                            onClick = { ability = option },
                            label = {
                                Text("${option.name} ${spiLabel(option.spi, option.perTurn)}")
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                // Reference prose closed, the cost below it open (6gl): the
                // ability and drawbacks read as a wall (play session 4).
                RuleNote("What ${ability.name} does") {
                    Text(ability.effect, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    costLine(cost.spi, cost.hp, cost.perTurn, artifact),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // This WAS a warning that spending past 0 is legal. It is not:
                // Soul Debt is what spirit body damage does to you, and the
                // pool table's own 0 SPI row forbids activating an SPI-cost
                // ability. The Use button is withdrawn above.
                if (!Pools.canAffordSpi(cost.spi, spiAvailable)) {
                    Text(
                        "Cannot be used — this costs ${cost.spi} SPI and only " +
                            "$spiAvailable is left. At 0 SPI a Beyonder's " +
                            "abilities deactivate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                used?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                RuleNote("Drawbacks and sealing") {
                    Column {
                        artifact.drawbacks.forEach {
                            Text(
                                "• $it",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        Text(
                            "Sealing: ${artifact.sealing}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        },
    )
}

/** The chip label: the distinctive word, since four full names will not fit. */
private fun shortName(artifact: SealedArtifact): String =
    if (artifact.name.startsWith("The ")) {
        artifact.name.removePrefix("The ")
    } else {
        artifact.name.substringBefore(" of ")
    }

private fun spiLabel(spi: Int, perTurn: Boolean) =
    if (perTurn) "$spi SPI/turn" else "$spi SPI"

private fun costLine(
    spi: Int,
    hp: Int,
    perTurn: Boolean,
    artifact: SealedArtifact,
): String {
    val turn = if (perTurn) " per turn maintained" else ""
    if (hp == 0) return "Costs $spi SPI$turn."
    // Naming which rule charges the HP, because three of the four artifacts
    // print an HP number in their drawbacks and only these two are charged.
    val why = if (artifact.hpPerUse != null) {
        "the artifact's own price for any use"
    } else {
        "this ability itself"
    }
    return "Costs $spi SPI$turn and $hp HP — $why."
}
