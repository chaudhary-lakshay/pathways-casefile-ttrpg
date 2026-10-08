package com.pathways.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.Trait
import com.pathways.rules.InstancedPurchase
import com.pathways.rules.InstantiableTrait
import com.pathways.rules.PurchasableTrait
import com.pathways.rules.PurchasableTraits

/**
 * Traits bought in play, with the way back beside each one.
 *
 * The published sheet's own traits are NOT listed here: they are read-only and
 * already shown on the sheet screen, and giving one back needs a suppression
 * the save has no shape for. This row is what the player added.
 */
@Composable
internal fun TraitsRow(
    bought: List<String>,
    instanced: List<Pair<SavedInstancedTrait, Trait?>>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    canRemove: (String) -> Boolean,
    onRemoveInstanced: (Int) -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Traits",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAdd) { Text("Add a trait") }
        }
        if (bought.isEmpty() && instanced.isEmpty()) {
            Text(
                "None bought in play — the sheet's own are on the sheet screen.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        // Removed by INDEX, never by name: two purchases of one row are legal
        // and a bare ranged row composes to the book's own name for both, so
        // "Sense of Duty" can name two rows here and could not say which.
        instanced.forEachIndexed { index, (purchase, trait) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        // The instance is display, not identity: a bare ranged
                        // row keeps the book's own name and shows what it is
                        // beside it, because "Sense of Duty (Patients)"
                        // resolves to no printed row.
                        listOfNotNull(
                            trait?.name ?: purchase.row,
                            purchase.instance?.takeIf { trait?.name == purchase.row },
                        ).joinToString(" — "),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        trait?.let {
                            val row = PurchasableTraits.instantiableNamed(purchase.row)
                            (if (row?.advantage == true) it.cost else -it.cost).pointsLabel()
                        } ?: "Not applied — the book does not license this purchase, " +
                            "so it costs nothing.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { onRemoveInstanced(index) }) { Text("Give back") }
            }
        }
        bought.forEach { name ->
            val row = PurchasableTraits.named(name)
            val removable = canRemove(name)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        // A saved name the book no longer prints is kept in
                        // the file and billed by nothing, so the row says so
                        // rather than showing a price it cannot justify.
                        row?.let {
                            (if (it.advantage) it.cost else -it.cost).pointsLabel()
                        } ?: "The book no longer prints this trait, so it costs nothing.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    // Spiritual Fortitude is +3 maximum CoR: giving it back
                    // can bring the ceiling onto the total, or below 1.
                    if (!removable) {
                        Text(
                            "Kept: giving it back would bring maximum CoR to the CoR total or below 1.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = { onRemove(name) }, enabled = removable) { Text("Give back") }
            }
        }
    }
}

/**
 * Ch. 3's advantage and disadvantage lists, priced.
 *
 * Advantages and drawbacks are two headed sections rather than one list: they
 * move the budget in opposite directions, and a player scrolling one list of
 * signed numbers has to read every price to tell which is which.
 *
 * The rows this app cannot price are named at the bottom with the book's own
 * reason. Silently omitting rows a player can read in the book is the quiet
 * gap this project records instead.
 */
@Composable
internal fun TraitPicker(
    held: List<Trait>,
    bought: List<String>,
    onBuy: (PurchasableTrait) -> Unit,
    onSettle: (InstantiableTrait) -> Unit,
    onDismiss: () -> Unit,
) {
    val offered = remember { PurchasableTraits.offered() }
    val instantiable = remember { PurchasableTraits.instantiable() }
    val withheld = remember { PurchasableTraits.unoffered() }
    val sections = remember {
        val blessings = PurchasableTraits.blessingNames()
        listOf(
            Triple(
                "Advantages",
                "Cost points.",
                offered.filter { it.advantage && it.name !in blessings },
            ),
            Triple(
                "The Seven Orthodox Gods Blessings",
                PurchasableTraits.blessingRequirement(),
                offered.filter { it.name in blessings },
            ),
            // Excluded by name here too, though every blessing costs points
            // and so bills as an advantage today. The asymmetry was the
            // reviewer's finding: a blessing that ever billed the other way
            // would render in its own section AND in this one. Nothing can
            // reach that, which is when this repo closes a permissive path
            // rather than after the next caller finds it (6l).
            Triple(
                "Drawbacks",
                "Refund points, up to 40.",
                offered.filter { !it.advantage && it.name !in blessings },
            ),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Buy a trait") },
        text = {
            LazyColumn {
                // The blessings get their own head rather than sitting inside
                // Advantages. Seven rows at 75 points each, unlabelled among
                // rows costing 1 to 5, read as a pricing error -- and the
                // table's printed requirement would have nowhere to live,
                // which is how a field ends up extracted and never rendered
                // (6ai). It is SHOWN and gates nothing: this app models no
                // faith and no reconciliation, so whether a character is a
                // confirmed believer is the GM's to say (6af).
                sections.forEach { (heading, subtitle, rows) ->
                    item {
                        Text(
                            heading,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(subtitle, style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                    }
                    items(rows) { row ->
                        // Refused rather than offered and then rejected: a
                        // second copy bills its points and changes nothing,
                        // and a chip that cannot be taken is not a choice.
                        val already = !PurchasableTraits.canBuy(row, held) || row.name in bought
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !already) { onBuy(row) }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                row.name +
                                    if (already) " — already held" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (already) {
                                    MaterialTheme.colorScheme.outline
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                (if (row.advantage) row.cost else -row.cost).pointsLabel(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(row.notes, style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
                item {
                    Text(
                        "Priced by the instance",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "The book prints a range, or asks you to name the instance. " +
                            "Say which, and the price follows.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HorizontalDivider()
                }
                items(instantiable) { row ->
                    // Most of these may be taken more than once -- one premade
                    // carries two Vows -- so what repeats is a purchase and
                    // not a row. A row that enumerates variants is the
                    // exception: those are severities of one condition, and a
                    // character is Mild or Severe, never both.
                    val already = !PurchasableTraits.canInstantiate(row, held)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !already) { onSettle(row) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            row.name + if (already) " — already held" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (already) {
                                MaterialTheme.colorScheme.outline
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(
                            if (row.variants.isNotEmpty()) {
                                row.variants.joinToString(" / ") { (name, points) ->
                                    "$name ${if (row.advantage) points else -points}"
                                }
                            } else {
                                val low = if (row.advantage) row.costMin else -row.costMax
                                val high = if (row.advantage) row.costMax else -row.costMin
                                "$low to $high pts"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(row.notes, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
                items(withheld) { group ->
                    Text(
                        "Not offered — ${group.reason}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        group.names.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}
