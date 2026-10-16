package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.pathways.rules.InstancedPurchase
import com.pathways.rules.InstantiableTrait
import com.pathways.rules.PurchasableTraits

/**
 * Settling a row the book prices by its instance (Ch. 3).
 *
 * Two shapes, because the book prints two. A cell that ENUMERATES priced
 * options is settled by naming one, and there is nothing to choose inside the
 * range -- Shyness is Mild at 5 or Severe at 10 and nothing between. Every
 * other row prints an interval and is settled by describing the instance, with
 * the price chosen inside the bounds the book prints.
 *
 * **Buy is offered only for a purchase the rules layer licenses**, asked
 * through [PurchasableTraits.instanced] rather than by this screen re-deriving
 * the conditions. A control that throws when tapped is not a choice (6t), and
 * a screen holding its own copy of a rule is how this project's numbers
 * stopped reconciling six times.
 */
@Composable
internal fun InstancedTraitDialog(
    row: InstantiableTrait,
    onBuy: (InstancedPurchase) -> Unit,
    onDismiss: () -> Unit,
) {
    val enumerated = row.variants.isNotEmpty()
    var variant by remember(row.name) { mutableStateOf(row.variants.firstOrNull()?.first) }
    var instance by remember(row.name) { mutableStateOf("") }
    var cost by remember(row.name) { mutableIntStateOf(row.costMin) }

    val purchase = InstancedPurchase(
        row = row.name,
        variant = if (enumerated) variant else null,
        instance = if (enumerated) null else instance.trim().ifBlank { null },
        cost = if (enumerated) {
            row.variants.firstOrNull { it.first == variant }?.second ?: row.costMin
        } else {
            cost
        },
    )
    val licensed = PurchasableTraits.instanced(purchase)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = licensed != null,
                onClick = { onBuy(purchase) },
            ) { Text("Buy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(licensed?.name ?: row.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(row.notes, style = MaterialTheme.typography.bodySmall)
                if (enumerated) {
                    Text(
                        "The book prices each of these itself.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    row.variants.forEach { (name, points) ->
                        FilterChip(
                            selected = variant == name,
                            onClick = { variant = name },
                            label = {
                                Text(
                                    "$name — " +
                                        (if (row.advantage) points else -points).pointsLabel(),
                                )
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = instance,
                        onValueChange = { instance = it },
                        singleLine = true,
                        label = {
                            Text(
                                if (row.needsInstance) {
                                    "Name it — the book asks you to"
                                } else {
                                    "What is it? The book prices this row by the instance"
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            enabled = cost > row.costMin,
                            onClick = { cost -= 1 },
                        ) { Text("−") }
                        Text(
                            (if (row.advantage) cost else -cost).pointsLabel(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = cost < row.costMax,
                            onClick = { cost += 1 },
                        ) { Text("+") }
                    }
                    Text(
                        "The book prints ${row.costMin} to ${row.costMax} for this row.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                // Drawn named or not, two lines either way: the warning going
                // re-centred the dialog and moved Buy under the finger (play
                // session 5).
                Text(
                    // Said, never left as a dead button: a player who has
                    // not filled the instance in should be told which part
                    // is missing rather than watching Buy stay grey.
                    when {
                        licensed != null -> ""
                        enumerated -> "Name which one the character has."
                        else -> "The book prices this row by what it actually is, " +
                            "so name the instance before buying it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    minLines = 2,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
    )
}
