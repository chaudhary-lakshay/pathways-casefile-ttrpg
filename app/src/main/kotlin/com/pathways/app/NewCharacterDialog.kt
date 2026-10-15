package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pathways.rules.PremadeSheet

/**
 * A new character: a name, and the sheet it starts as. The blank sheet is
 * the default, since that is what a player building their own starts from;
 * a premade is offered to copy too. Everything after is the builder's.
 */
@Composable
internal fun NewCharacterDialog(
    bases: List<PremadeSheet>,
    taken: Set<String>,
    onDismiss: () -> Unit,
    onCreate: (name: String, base: PremadeSheet) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var base by remember { mutableStateOf(bases.first()) }
    val refusal = newCharacterRefusal(name, taken)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New character") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    // Said only once something is typed: an empty field is not an error yet.
                    supportingText = { if (name.isNotEmpty() && refusal != null) Text(refusal) },
                    isError = name.isNotEmpty() && refusal != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Starts as", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(bases, key = { it.name }) { option ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(selected = option == base, role = Role.RadioButton) { base = option }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == base, onClick = null)
                            Text(
                                if (isTemplate(option)) "Blank sheet" else option.name,
                                Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name.trim(), base) }, enabled = refusal == null) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
