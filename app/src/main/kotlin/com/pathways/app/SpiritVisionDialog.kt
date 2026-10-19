package com.pathways.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathways.rules.SpiritVision

/**
 * Spirit Vision: "Cost: 1 SPI per activation, regardless of duration." v6.9i
 * prints no roll, so this screen charges the activation and shows the two
 * colour tables a look is read with, and the pathway refinements beside them.
 *
 * **The two colour tables are the point of the ability, not decoration.**
 * They share seven colour names and mean unrelated things by them -- Purple
 * is "Spirituality, madness" in the Astral table and the BRAIN in the Ether
 * one -- so they render as two separate lists, never merged on the colour.
 */
@Composable
fun SpiritVisionDialog(
    spiAvailable: Int,
    onSpendSpi: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val activation = SpiritVision.activation()
    var activations by remember { mutableIntStateOf(0) }
    // The count as drawn, for the second-tap guard.
    val drawn = activations

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FormTitle(FORM_SIGHT) { Text(SpiritVision.TRAIT) } },
        text = steadyBody {
            Column {
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (drawn > 0) {
                        Text(
                            "Active. It lasts as long as you keep it open; looking again later is a new activation.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    ColourTable(
                        "Astral Projection — emotional state",
                        SpiritVision.astralColors().map { it.color to it.meaning },
                    )
                    ColourTable(
                        "Ether Body — physical health",
                        // The INVERSE order to the table above: this one is keyed
                        // by body region and the colour is the value. Printed the
                        // way the book prints each, so neither is transposed.
                        SpiritVision.etherBody().map { it.region to it.color },
                    )
                    Text(
                        SpiritVision.etherBodyNote(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RuleNote("Pathway refinements") {
                        SpiritVision.pathwayDifferences().forEach {
                            Text("${it.name}: ${it.ability}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                RollFooter(
                    lines = listOfNotNull(
                        "$activation SPI per activation, regardless of duration.",
                        // Both printed rows: the state, and what acting in it costs.
                        if (spiAvailable > 0) null else "Drained: abilities deactivate at 0 SPI, and each use costs 1 CoR.",
                    ),
                    caption = "No roll",
                    target = null,
                    label = if (drawn == 0) "Activate ($activation SPI)" else "Activate again ($activation SPI)",
                    icon = Icons.Filled.Visibility,
                ) {
                    // A second tap of the same picture is dropped (6ei):
                    // each spend below 0 is permanent CoR.
                    if (activations != drawn) return@RollFooter
                    onSpendSpi(activation)
                    activations = drawn + 1
                }
            }
        },
        confirmButton = { CloseButton(onDismiss) },
    )
}

@Composable
private fun ColourTable(heading: String, rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            heading,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        for ((key, value) in rows) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    key,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = 8.dp),
                    fontWeight = FontWeight.Medium,
                )
                Text(value, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
