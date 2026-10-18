package com.pathways.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.SkillDefaults

/**
 * Pick a skill the sheet does not have, to roll at its default (Ch. 2, p. 10).
 *
 * A picker and nothing more: the roll itself is the sheet's own [RollDialog],
 * so an untrained roll takes what the trackers cost, what the traits add and
 * what the GM says of the scene through the one path every other skill roll
 * uses. A second roll here would be the two-targets-for-one-character defect.
 *
 * Every sentence on it is [SkillDefaults]', because each is a claim about
 * what this app does and does not do, and those are the ones that go stale.
 */
@Composable
fun UntrainedDialog(
    rows: List<SkillDefaults.Untrained>,
    weaponNote: String?,
    onPick: (SkillDefaults.Untrained) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(rows, query) {
        rows.filter { it.key.label().contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { CloseButton(onDismiss) },
        title = { FormTitle(FORM_UNTRAINED) { Text("Roll a skill untrained") } },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Find a skill") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(shown) { row ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onPick(row) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                // Ink: the dialog's text slot greys, and grey
                                // is what a blocked row looks like (6il).
                                Text(row.key.label(), color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    row.source,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("${row.level}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    // Below the rows, where a player who did not find a skill
                    // is looking: these say why it is not there.
                    item { Caveat(SkillDefaults.NOT_LISTED) }
                    item { Caveat(SkillDefaults.PLAIN_ROLL) }
                    weaponNote?.let { item { Caveat(it) } }
                }
            }
        },
    )
}

@Composable
private fun Caveat(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}
