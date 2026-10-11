package com.pathways.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathways.rules.TraitReport
import com.pathways.rules.TraitReport.Applied

/**
 * One held trait, collapsed to what a player scans: its name, its price, a
 * word for how much of it this app applies, and its roll where it has one.
 * The book's text and every reason open under it. The word is
 * [TraitReport.applied], so it says nothing the notes do not (6gl).
 */
@Composable
internal fun TraitRow(
    row: TraitReport.HeldTrait,
    priceLine: String,
    roll: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRoll: () -> Unit,
) {
    val status = TraitReport.applied(row)
    Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.shown, style = MaterialTheme.typography.titleMedium)
                Text(priceLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(status)
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Hide details" else "Show details",
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        // Its own control, never the row's tap: the number is the one the
        // dialog rolls (6ga), and opening the text must not roll it.
        roll?.let { AssistChip(onClick = onRoll, label = { Text(it) }, modifier = Modifier.padding(top = 4.dp)) }
        if (expanded) Details(row)
    }
}

@Composable
private fun Details(row: TraitReport.HeldTrait) {
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            row.cell ?: "This sheet's name matches no row in the book's list, so nothing is read from it.",
            style = MaterialTheme.typography.bodyMedium,
        )
        for (note in row.notes) {
            // The two blanket sentences are the legend's; a row's own reason,
            // and every clause, are said here.
            val blanket = note.reason == TraitReport.UNREAD || note.reason == TraitReport.ROLLED_ONLY
            if (note.printed == row.cell && blanket) continue
            Column {
                if (note.printed != row.cell) {
                    Text("Not applied: ${note.printed}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    note.reason.replaceFirstChar { it.uppercase() } + ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: Applied) {
    val (label, color) = when (status) {
        Applied.APPLIED -> "Applied" to MaterialTheme.colorScheme.primaryContainer
        Applied.PARTLY -> "Partly applied" to MaterialTheme.colorScheme.tertiaryContainer
        Applied.ROLL_ONLY -> "Roll only" to MaterialTheme.colorScheme.secondaryContainer
        Applied.GM -> "GM applies" to MaterialTheme.colorScheme.surfaceVariant
        Applied.NOT_IN_BOOK -> "Not in the book" to MaterialTheme.colorScheme.errorContainer
    }
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** What each word means, said once for the tab rather than under every row. */
@Composable
internal fun TraitLegend() {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 8.dp)) {
        TextButton(onClick = { open = !open }) { Text(if (open) "Hide what the tags mean" else "What the tags mean") }
        if (open) {
            listOf(
                Applied.APPLIED to "every rule on the row reaches the rolls it names.",
                Applied.PARTLY to "open the row for the part that is not applied, and why.",
                Applied.ROLL_ONLY to TraitReport.ROLLED_ONLY + ".",
                Applied.GM to TraitReport.UNREAD + ", unless the row says why.",
                Applied.NOT_IN_BOOK to "the sheet prints a name the book's list does not, so nothing is read from it.",
            ).forEach { (status, meaning) ->
                Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.Top) {
                    StatusPill(status)
                    Text(
                        meaning.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** A list's heading, with its count. */
@Composable
internal fun TraitSection(title: String, count: Int) {
    Text(
        "${title.uppercase()} · $count",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
