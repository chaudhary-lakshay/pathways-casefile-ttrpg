package com.pathways.app

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

/**
 * The sheet's overflow menu: put a character back to its printed sheet.
 *
 * A drunk potion, a purchase, a wound or a loss to a failed PCR cannot be
 * undone in play, and that is the book's (p. 45). Starting over is not
 * play: the owner wanted to report an issue and start clean (2026-10-14).
 * So it is a menu item behind a confirmation, never a control on the sheet.
 */
@Composable
internal fun ResetMenu(name: String, onReset: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    IconButton(onClick = { menu = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More")
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(
            text = { Text("Reset to sheet") },
            onClick = { menu = false; confirming = true },
        )
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Reset $name?") },
            text = {
                Text(
                    "Back to the printed sheet: potions, purchases, wounds, " +
                        "corruption and declared states are cleared. This cannot be undone.",
                )
            },
            confirmButton = {
                // Idempotent, so a second tap of the same picture is harmless:
                // the entry is already gone and the session is rebuilt again.
                Button(
                    onClick = { confirming = false; onReset() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}
