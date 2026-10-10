package com.pathways.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathways.rules.RandomSource
import com.pathways.rules.SpiritVision
import com.pathways.rules.SpiritVisionResult
import com.pathways.rules.TraitClause

/**
 * Reading a soul (Ch. 7.6, p. 77-78).
 *
 * **The failure row is why this screen is shaped the way it is.** The table
 * prints "Failure: Incorrect reading", which chapter 4's sibling tables do
 * not: a failed reading is not the absence of an answer, it is a WRONG answer
 * the character believes. So the result area shows the row's own words either
 * way and never announces "you failed" beside them -- a screen that prints
 * the verdict has modelled the die and not the rule, and hands the player
 * information their character does not have. The dice and the margin are
 * shown, because a GM at the table needs them; the reading is not editorialised.
 *
 * **The two colour tables are the point of the ability, not decoration.**
 * Spirit Vision tells you what a colour MEANS, so a reading is unusable
 * without them. They are also two tables sharing seven colour names and
 * meaning unrelated things by them -- Purple is "Spirituality, madness" in the
 * Astral table and the BRAIN in the Ether one -- so they render as two
 * separate lists, never merged on the colour.
 *
 * ponytail: no maintenance clock. The book charges 1 SPI per minute to keep
 * it running and this app tracks no clock (docs/HOW-TO-PLAY.md's standing
 * refusal); activation is charged, the rate is printed, and the minutes are
 * the GM's.
 *
 * **Looking again is a fresh activation and is charged as one**, which is
 * `ArtifactDialog`'s "Use again" exactly. The button names the price so the
 * second press is not a cheaper-looking version of the first, and it
 * disables itself when the pool cannot pay: [spiAvailable] is computed by the
 * caller from `session.spiSpent`, which is `mutableIntStateOf`, so spending
 * recomposes the caller and this dialog is handed the new figure.
 *
 * Nothing about spending SPI moves the target. [SpiritVision.targetFor] is
 * `per + alertness + shock + hurt`, and those read the ATTRIBUTE, a trait and
 * the HP/FP pools --
 * the reading `DetectionDialog` already needed, after passing a remaining
 * pool there made a caster who had spent SPI worse at noticing.
 */
@Composable
fun SpiritVisionDialog(
    /** The book's own name for it on this pathway; not always "Spirit Vision". */
    form: String,
    per: Int,
    spiAvailable: Int,
    shock: Int,
    hurt: Int,
    /** [com.pathways.rules.TraitEffects.alertnessBonus]; required, so no caller forgets it. */
    alertness: Int,
    /**
     * [com.pathways.rules.TraitEffects.visionClause]: Acute Vision, which the
     * GM declares on this roll or not (6fh). Required, like [alertness].
     */
    visionClause: TraitClause?,
    dice: RandomSource,
    onSpendSpi: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var result by remember { mutableStateOf<SpiritVisionResult?>(null) }
    val activation = SpiritVision.activation()
    var visionDeclared by remember { mutableStateOf(false) }
    val vision = visionClause?.takeIf { visionDeclared }
    val target = SpiritVision.targetFor(per, shock, hurt, alertness, vision)
    // Composed once, beside the target: the caption prints these and the
    // result carries them, and a second call would be a second answer.
    val parts = SpiritVision.partsFor(per, shock, hurt, alertness, vision)
    val affordable = spiAvailable >= activation

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(form) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Activate for $activation SPI, then ${SpiritVision.maintenance()} SPI " +
                        "per minute to maintain. Roll against Perception to interpret.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Roll $target or under" +
                        parts.joinToString(
                            prefix = "  (", postfix = ")",
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // The skill roll dialog's declaration, for the one clause
                // this roll can take (6fh).
                visionClause?.let { clause ->
                    TextButton(onClick = { visionDeclared = !visionDeclared }) {
                        Text(
                            "${clause.shown}: ${clause.printed}",
                            color = if (visionDeclared) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                if (!affordable) {
                    Text(
                        "Not enough SPI: $spiAvailable left, $activation to activate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                result?.let { outcome ->
                    HorizontalDivider()
                    // The reading, in the book's own words. Deliberately NOT
                    // labelled with the outcome: "Incorrect reading" is what
                    // the character comes away believing, and printing
                    // "Failure" above it tells the player something the rule
                    // does not give them.
                    Text(
                        outcome.row.effect,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "rolled ${outcome.roll.total} vs ${outcome.roll.target}" +
                            "  ·  margin ${outcome.roll.margin}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider()
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = affordable,
                onClick = {
                    // Charged before the roll, like every other SPI-priced
                    // action here: the book spends to activate, and a reading
                    // that failed still cost the point.
                    onSpendSpi(activation)
                    result = SpiritVision.rollAgainst(target, dice, parts)
                },
            ) { Text(if (result == null) "Look ($activation SPI)" else "Look again ($activation SPI)") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
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
