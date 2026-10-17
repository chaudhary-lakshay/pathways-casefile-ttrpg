package com.pathways.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/**
 * The sheet's overflow menu: the roll log, a reset to the printed sheet, and
 * for an own character only ([onDelete] non-null), deleting it.
 */
@Composable
internal fun SheetMenu(
    name: String,
    throws: () -> List<LoggedThrow>,
    onReset: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    var logging by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    IconButton(onClick = { menu = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More")
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(text = { Text("Roll log") }, onClick = { menu = false; logging = true })
        DropdownMenuItem(text = { Text("Reset to sheet") }, onClick = { menu = false; confirming = true })
        if (onDelete != null) {
            DropdownMenuItem(text = { Text("Delete character") }, onClick = { menu = false; deleting = true })
        }
    }
    if (logging) RollLogDialog(throws()) { logging = false }
    if (confirming) ResetDialog(name, isOwn = onDelete != null, onDismiss = { confirming = false }, onReset = onReset)
    if (deleting && onDelete != null) {
        Confirm(
            title = "Delete $name?",
            body = "The character, everything it has done, and its roll log go. This cannot be undone.",
            action = "Delete",
            onDismiss = { deleting = false },
            onConfirm = onDelete,
        )
    }
}

/**
 * Every throw this character made since the app started, newest first: a
 * name, the dice and the target (the owner rejected text walls, 6gd).
 */
@Composable
private fun RollLogDialog(throws: List<LoggedThrow>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { CloseButton(onDismiss) },
        title = { Text("Roll log") },
        text = {
            if (throws.isEmpty()) {
                Text("No rolls yet.")
            } else {
                // Said once, not per row: the dialog that rolled owns the verdict.
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    item {
                        Text(
                            "Dice as thrown. A push or a table's own rule can change the verdict after.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(throws) { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(row.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(row.line(), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
    )
}

/**
 * Put a character back to its printed sheet.
 *
 * A drunk potion, a purchase, a wound or a loss to a failed PCR cannot be
 * undone in play, and that is the book's (p. 45). Starting over is not
 * play: the owner wanted to report an issue and start clean (2026-10-14).
 * So it is a menu item behind a confirmation, never a control on the sheet.
 */
@Composable
private fun ResetDialog(name: String, isOwn: Boolean, onDismiss: () -> Unit, onReset: () -> Unit) = Confirm(
    title = "Reset $name?",
    body = if (isOwn) {
        "Back to $name as built: potions, wounds, corruption, study, boons, traits gained in play, " +
            "declared states and the roll log are cleared. Purchases are kept. This cannot be undone."
    } else {
        // The whole save entry goes (MainActivity keeps none for a premade),
        // so every kept thing is named, as the own character's line names its
        // own: play session 4 reset a boon this line did not mention.
        "Back to the printed sheet: potions, purchases, wounds, corruption, study, boons, " +
            "traits gained in play, declared states and the roll log are cleared. This cannot be undone."
    },
    action = "Reset",
    onDismiss = onDismiss,
    onConfirm = onReset,
)

/** A destructive action behind one more tap. */
@Composable
private fun Confirm(title: String, body: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            // Both actions are idempotent, so a second tap of the same picture
            // is harmless: the entry is already gone.
            Button(shape = ButtonShape,
                onClick = { onDismiss(); onConfirm() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(action) }
        },
        dismissButton = { CloseButton(onDismiss, "Cancel") },
    )
}
