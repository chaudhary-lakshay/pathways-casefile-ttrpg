package com.pathways.app

import com.pathways.model.BeyonderState
import com.pathways.model.Character
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.pathways.model.SkillKey
import com.pathways.rules.CharmedObject
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    onRemoveInstanced: (Int, SavedInstancedTrait) -> Unit,
    /** What a bought Charmed Object's skill line says (6hk). */
    charmLine: String,
    onChooseCharm: () -> Unit,
) {
    Column {
        SectionHead("Traits", "Add a trait", onAdd) {
            if (bought.isEmpty() && instanced.isEmpty()) {
                Text(
                    "None bought in play — the sheet's own are on the sheet screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                TextButton(onClick = { onRemoveInstanced(index, purchase) }) { Text("Give back") }
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
                    // "Specify item and skill": the skill is the holder's to
                    // name, and choosing again is how it is changed.
                    if (name == CharmedObject.ROW) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(charmLine, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = onChooseCharm) { Text("Choose skill") }
                        }
                    }
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
 * What the builder says under a bought Charmed Object: the skill it charms,
 * that none is chosen, or that the chosen one is not a skill [held] by this
 * character, where no roll would offer the chip and nothing else says why.
 */
internal fun charmLine(chosen: SavedCharmedSkill?, held: Collection<SkillKey>): String {
    if (chosen == null) return "No skill chosen: no roll offers its +1."
    val label = if (chosen.specialization == null) chosen.name else "${chosen.name} (${chosen.specialization})"
    val has = held.any { it.name.equals(chosen.name, ignoreCase = true) && it.specialization.equals(chosen.specialization, ignoreCase = true) }
    return if (has) "Charms $label: +1 when used, lit on the roll." else "Charms $label, which this character has no level in."
}

/**
 * The skills a Charmed Object may name: every one [character] has a level in,
 * a potion's grants included, as the sheet's Skills tab lists them.
 */
internal fun charmableSkills(character: Character, state: BeyonderState?): Set<SkillKey> =
    liveSkillLevels(character.copy(beyonder = state)).keys

/**
 * The character's own skills, one of which a Charmed Object names. Untrained
 * skills are not listed.
 */
@Composable
internal fun CharmSkillPicker(skills: List<SkillKey>, onPick: (SkillKey) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Charmed Object's skill") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (skills.isEmpty()) Text("This character has no skill to charm yet.")
                skills.forEach { key ->
                    TextButton(onClick = { onPick(key) }) { Text(key.label()) }
                }
            }
        },
    )
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
    /** Why a row may not be bought now, beyond being held (God of Combat's ceilings, 6hf), or null. */
    refusalFor: (String) -> String?,
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
    // One open row, held outside the list's items, which forget their state
    // when scrolled off. Keyed by section too: a name in two sections must
    // not open twice (6gm's design review).
    var open by remember { mutableStateOf<String?>(null) }
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
                        val blocked = if (!PurchasableTraits.canBuy(row, held) || row.name in bought) {
                            ALREADY_HELD
                        } else {
                            refusalFor(row.name)
                        }
                        val key = "$heading/${row.name}"
                        PickerRow(
                            name = row.name,
                            blocked = blocked,
                            price = (if (row.advantage) row.cost else -row.cost).pointsLabel(),
                            notes = row.notes,
                            expanded = open == key,
                            onToggle = { open = if (open == key) null else key },
                            action = "Buy · ${(if (row.advantage) row.cost else -row.cost).pointsLabel()}",
                            onAction = { onBuy(row) },
                        )
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
                    val key = "instance/${row.name}"
                    PickerRow(
                        name = row.name,
                        blocked = if (already) ALREADY_HELD else null,
                        price = if (row.variants.isNotEmpty()) {
                            row.variants.joinToString(" / ") { (name, points) ->
                                "$name ${if (row.advantage) points else -points}"
                            }
                        } else {
                            val low = if (row.advantage) row.costMin else -row.costMax
                            val high = if (row.advantage) row.costMax else -row.costMin
                            "$low to $high pts"
                        },
                        notes = row.notes,
                        expanded = open == key,
                        onToggle = { open = if (open == key) null else key },
                        action = "Choose…",
                        onAction = { onSettle(row) },
                    )
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

/** What a held row says in place of its Buy button. */
private const val ALREADY_HELD = "already held"

/**
 * One row of the picker: its name and price, and its book text behind a tap.
 *
 * The tap OPENS the row and never buys (6gm): a player reads a trait before
 * paying for it, the way the Traits tab's roll chip is never the row's tap
 * (6gl). [action] labels the button that buys, or opens the instance
 * dialog, and it shows only on an open row a character can still take;
 * [blocked] stays on the closed row, the only line saying why it cannot be
 * taken: "already held", or a refusal such as God of Combat's ceilings.
 */
@Composable
private fun PickerRow(
    name: String,
    /** Why the row cannot be taken, printed after its name; null when it can. */
    blocked: String?,
    price: String,
    notes: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    action: String,
    onAction: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "Hide details" else "Show details", onClick = onToggle)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name + (blocked?.let { " — $it" } ?: ""),
                style = MaterialTheme.typography.bodyLarge,
                color = if (blocked != null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                price,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }
        if (expanded) {
            Text(notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            if (blocked == null) {
                FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = 6.dp)) {
                    Text(action)
                }
            }
        }
    }
}
