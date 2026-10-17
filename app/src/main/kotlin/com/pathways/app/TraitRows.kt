package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    // One root, so the rule below stacks under the row in its list item.
    Column { Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "Hide details" else "Show details", onClick = onToggle)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.shown, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(priceLine, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        roll?.let {
            AssistChip(
                onClick = onRoll,
                label = { Text(it, style = MaterialTheme.typography.labelLarge.tabular()) },
                leadingIcon = { Icon(Icons.Filled.Casino, contentDescription = null, Modifier.size(18.dp)) },
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (expanded) Details(row)
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Details(row: TraitReport.HeldTrait) {
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // The book's own cell, ruled in the stamp at its left (frame 08).
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.secondary))
            Text(
                row.cell ?: "This sheet's name matches no row in the book's list, so nothing is read from it.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        for (note in row.notes) {
            // Every reason is said where the row opens, the two blanket ones
            // too: the legend is closed by default, and an open row that
            // printed only its cell would read as counted (the diff review).
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
    val ink = MaterialTheme.colorScheme.primary
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    // Filled only where the app does all of it; an outline is a part, or the
    // GM's. "GM call" was "Not applied", which a player read as "this trait
    // does nothing" -- the row's rule still holds, the GM applies it.
    val (label, color, filled) = when (status) {
        Applied.APPLIED -> Triple("Applied", ink, true)
        Applied.PARTLY -> Triple("Partly applied", ink, false)
        Applied.ROLL_ONLY -> Triple("Roll only", ink, false)
        Applied.GM -> Triple("GM call", grey, false)
        // Not "not in the book": "Absent-Minded" resembles a book row it does
        // not match, and 4.1 records rather than repairs it.
        Applied.NOT_IN_BOOK -> Triple("Unmatched name", MaterialTheme.colorScheme.error, false)
    }
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = if (filled) color else Color.Transparent,
        border = if (filled) null else BorderStroke(1.dp, color),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (filled) MaterialTheme.colorScheme.onPrimary else color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** What each word means, said once for the tab rather than under every row. */
@Composable
internal fun TraitLegend() {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 8.dp)) {
        TextButton(onClick = { open = !open }, shape = ButtonShape) {
            Text(
                if (open) "Hide what the tags mean" else "What the tags mean",
                style = MaterialTheme.typography.labelLarge.tabular(),
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (open) {
            listOf(
                Applied.APPLIED to "every rule on the row reaches the rolls it names.",
                Applied.PARTLY to "open the row for the part that is not applied, and why.",
                Applied.ROLL_ONLY to TraitReport.ROLLED_ONLY + ".",
                Applied.GM to TraitReport.UNREAD + ", unless the row says why.",
                Applied.NOT_IN_BOOK to "the sheet's spelling matches no row in the book's list, so nothing is read from it.",
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

/** A sheet list's heading, "§ TITLE · n", over an ink rule (Case File, frames 02-04, 08). */
@Composable
internal fun SheetSection(title: String, count: Int? = null) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)) {
        Caps(
            "§ $title" + count?.let { " · $it" }.orEmpty(),
            Modifier.padding(bottom = 8.dp),
            spoken = title + count?.let { ", $it" }.orEmpty(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}
