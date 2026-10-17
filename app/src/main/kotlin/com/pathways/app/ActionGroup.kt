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
 * **Every action is one neutral colour; the icon carries the category.**
 * Until 2026-10-17 each category had its own hue (combat `error`, Beyonder
 * `secondary`, management `primary`); the owner picked a single accent
 * instead, because a colour per category read as "colourful AI slop".
 */

/** One of the big buttons: icon over label, 64dp tall. The caller sets its width. */
@Composable
internal fun PrimaryAction(
    label: String,
    icon: ImageVector,
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
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** A secondary action: a neutral outlined chip. */
@Composable
internal fun ActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = { Icon(icon, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    )
}
