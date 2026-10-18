package com.pathways.app

import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.pathways.model.Attr
import com.pathways.rules.PremadeSheet

/**
 * What the blank sheet starts as, read off its fixture: frame 05 printed
 * "at 10", and the fixture holds 9.
 */
internal fun blankHint(blank: PremadeSheet): String {
    val levels = Attr.entries.map { it to blank.attributes.getValue(it) }
    val same = levels.map { it.second }.distinct().singleOrNull()
    return if (same != null) {
        "Blank sheet starts every attribute at $same."
    } else {
        "Blank sheet starts at ${levels.joinToString(", ") { (attr, level) -> "$attr $level" }}."
    }
}

/** The premades a character may be copied from, the blank sheet aside, by name. */
internal fun templateChoices(bases: List<PremadeSheet>, query: String): List<PremadeSheet> =
    bases.filterNot(::isTemplate).filter { it.name.contains(query.trim(), ignoreCase = true) }

/** Why Create is off: the name's refusal first, then no template picked ([base] null). */
internal fun createRefusal(name: String, taken: Set<String>, base: PremadeSheet?): String? =
    newCharacterRefusal(name, taken) ?: if (base == null) "Pick a template." else null

/** The list's one height, so a filter that empties it moves no button (6ia). */
private val TEMPLATE_LIST_HEIGHT = 240.dp

/**
 * A new character: a name, and the sheet it starts as, as Case File frame
 * 05 draws it. The blank sheet is the default, since that is what a player
 * building their own starts from; a premade is offered to copy too, as
 * printed: [SavedSession.basedOn] carries no walk, so the list prints no
 * pathway beside a name. Everything after is the builder's.
 *
 * The template list is drawn under Blank too, dimmed, and a tap on a row
 * picks it and turns to Template. Every line under the name and the list
 * holds its height, so neither typing nor the filter moves Create.
 */
@Composable
internal fun NewCharacterDialog(
    bases: List<PremadeSheet>,
    taken: Set<String>,
    /** The file number the character will take: decoration, frame 05's "FILE Nº". */
    file: Int,
    onDismiss: () -> Unit,
    onCreate: (name: String, base: PremadeSheet) -> Unit,
) {
    val blank = remember(bases) { bases.first(::isTemplate) }
    var name by remember { mutableStateOf("") }
    var fromTemplate by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<PremadeSheet?>(null) }
    var query by remember { mutableStateOf("") }
    val base = if (fromTemplate) picked else blank
    val refusal = createRefusal(name, taken, base)
    val nameRefusal = newCharacterRefusal(name, taken).takeIf { name.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Box {
                FormTitle(FORM_INTAKE, file = "File Nº ${fileDigits(file)}") { Text("New character") }
                // Frame 05's paperclip, decoration on the form's corner, above
                // the file number (the dialog clips, so it cannot overhang).
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = (-24).dp, y = (-22).dp).size(24.dp).rotate(45f),
                )
            }
        },
        text = {
            // Scrolls once large text outgrows the window (390 dp at font
            // 1.3 cut the hint off); the template list keeps its fixed height.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Caps("Subject name")
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    // Reserved, so the refusal arriving moves nothing below it.
                    supportingText = { Text(nameRefusal.orEmpty()) },
                    isError = nameRefusal != null,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Caps("Starts as", Modifier.padding(top = 4.dp, bottom = 8.dp))
                StartsAs(fromTemplate) { fromTemplate = it }
                TemplateList(bases, query, onQuery = { query = it }, picked = picked, active = fromTemplate) {
                    picked = it
                    fromTemplate = true
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, contentDescription = null, Modifier.padding(top = 2.dp).size(14.dp))
                    Text(
                        when {
                            !fromTemplate -> blankHint(blank)
                            picked == null -> "Pick a template."
                            else -> "Starts as ${picked?.name}'s sheet as printed."
                        },
                        Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        minLines = 2,
                    )
                }
            }
        },
        confirmButton = {
            Button(shape = ButtonShape, onClick = { onCreate(name.trim(), base ?: return@Button) }, enabled = refusal == null) {
                Caps("Create", style = MaterialTheme.typography.labelLarge.tabular(), color = LocalContentColor.current)
            }
        },
        dismissButton = { CloseButton(onDismiss, "Cancel") },
    )
}

/** BLANK SHEET | TEMPLATE, the open one filled in ink. */
@Composable
private fun StartsAs(fromTemplate: Boolean, onPick: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Row(Modifier.fillMaxWidth().border(2.dp, MaterialTheme.colorScheme.outline, shape).selectableGroup()) {
        Segment("Blank sheet", Icons.Filled.Description, open = !fromTemplate, Modifier.weight(1f)) { onPick(false) }
        Segment("Template", Icons.Filled.ContentCopy, open = fromTemplate, Modifier.weight(1f)) { onPick(true) }
    }
}

/** One cell of an ink segmented row: filled when [open]. Shared with the pathway screen's tabs. */
@Composable
internal fun Segment(label: String, icon: ImageVector?, open: Boolean, modifier: Modifier, inset: Dp = 0.dp, onClick: () -> Unit) {
    val ink = if (open) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .heightIn(min = 48.dp)
            .background(if (open) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(4.dp))
            .selectable(selected = open, role = Role.RadioButton, onClick = onClick)
            // Off the border where a label wraps: "Digestion & corruption"
            // ran flush against the divider at 390 dp, and the same 8 dp
            // broke "Blank sheet", which fits, onto two lines (6il).
            .padding(horizontal = inset),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        Caps(
            label,
            Modifier.padding(start = if (icon != null) 6.dp else 0.dp),
            style = MaterialTheme.typography.labelLarge.tabular().copy(textAlign = TextAlign.Center),
            color = ink,
        )
    }
}

/** The filter and the premades, numbered as the gallery files them. */
@Composable
private fun TemplateList(
    bases: List<PremadeSheet>,
    query: String,
    onQuery: (String) -> Unit,
    picked: PremadeSheet?,
    active: Boolean,
    onPick: (PremadeSheet) -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val choices = templateChoices(bases, query)
    Column(Modifier.padding(top = 12.dp).border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.padding(start = 8.dp).weight(1f)) {
                if (query.isEmpty()) {
                    Text("Filter templates…", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(Modifier.height(TEMPLATE_LIST_HEIGHT).alpha(if (active) 1f else 0.55f).selectableGroup()) {
            if (choices.isEmpty()) {
                item { Text("No template matches.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
            }
            items(choices, key = { it.name }) { option ->
                val selected = active && option == picked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = selected, role = Role.RadioButton) { onPick(option) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        fileNumber(option, bases)?.let(::fileDigits).orEmpty(),
                        Modifier.width(28.dp).clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RadioButton(selected = selected, onClick = null)
                    Text(option.name, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
