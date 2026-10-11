package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The sheet's actions in two weights: four big buttons for what a turn
 * reaches for, and a row of chips for the rest.
 *
 * This replaces one grid of thirteen equal tiles, which spent 40% of a tablet
 * screen before a single stat and gave the Attack button the same weight as
 * Artifacts. Before the grid there was a scrolling row that hid nine actions,
 * and before that four labelled chip groups that read as four lists. The
 * chips keep every action one tap away. The big buttons are what is used
 * every turn.
 *
 * **Colour carries the category**, drawn from Sanzo Wada's combination #269
 * ([Wada269]): combat is Pale Burnt Lake, a Beyonder power is Aconite Violet,
 * managing the character is Raw Sienna, and a roll the GM called is neutral.
 */

/** What an action is FOR, which decides its colour. */
enum class ActionKind { ROLL, COMBAT, BEYONDER, MANAGE }

@Composable
private fun tintOf(kind: ActionKind): Color = when (kind) {
    ActionKind.ROLL -> MaterialTheme.colorScheme.onSurfaceVariant
    ActionKind.COMBAT -> Wada269.burntLake()
    ActionKind.BEYONDER -> Wada269.aconite()
    ActionKind.MANAGE -> Wada269.rawSienna()
}

/** One of the big buttons: icon over label, 64dp tall. The caller sets its width. */
@Composable
internal fun PrimaryAction(
    label: String,
    icon: ImageVector,
    kind: ActionKind,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.heightIn(min = 64.dp),
    ) {
        Column(
            Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Icon(icon, contentDescription = null, tint = tintOf(kind))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** A secondary action: an outlined chip in its category's colour. */
@Composable
internal fun ActionChip(
    label: String,
    icon: ImageVector,
    kind: ActionKind,
    onClick: () -> Unit,
) {
    val tint = tintOf(kind)
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = { Icon(icon, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = if (kind == ActionKind.ROLL) MaterialTheme.colorScheme.onSurface else tint,
            leadingIconContentColor = tint,
        ),
        border = BorderStroke(1.dp, if (kind == ActionKind.ROLL) MaterialTheme.colorScheme.outline else tint),
    )
}
