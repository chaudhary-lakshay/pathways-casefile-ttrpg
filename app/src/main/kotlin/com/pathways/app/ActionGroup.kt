package com.pathways.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
 * The sheet screen's action palette: twelve actions in one wrapping grid.
 *
 * Three shapes have now been tried for these twelve. A single
 * `horizontalScroll` row hid nine of them off-screen. Four labelled chip groups
 * made all twelve visible but stacked to roughly 340dp and read as four
 * separate lists rather than one palette. This is the third: a fixed-width
 * tile per action, icon over label, wrapped -- about 240dp for the same twelve,
 * scannable by icon on a screen the player has already seen a hundred times.
 *
 * **Colour carries the category**, which is why `Theme.kt` defines three
 * accents. Brass is reserved for the screen's one primary button, so the hub
 * spends the other two plus a neutral:
 * a GM-called roll is neutral, combat is `error`, a Beyonder power is
 * `secondary` (arcane), management is `tertiary` (verdigris). A player looking
 * for "Ritual" finds the purple cluster without reading a word.
 *
 * The caller still decides membership: the Beyonder actions are conditional,
 * and passing none of them simply yields a shorter grid rather than an empty
 * heading.
 */

/** What an action is FOR, which decides its colour. */
enum class ActionKind { ROLL, COMBAT, BEYONDER, MANAGE }

@Composable
private fun tintOf(kind: ActionKind): Color = when (kind) {
    ActionKind.ROLL -> MaterialTheme.colorScheme.onSurfaceVariant
    ActionKind.COMBAT -> MaterialTheme.colorScheme.error
    ActionKind.BEYONDER -> MaterialTheme.colorScheme.secondary
    ActionKind.MANAGE -> MaterialTheme.colorScheme.tertiary
}

@Composable
fun ActionHub(modifier: Modifier = Modifier, content: @Composable FlowRowScopeMarker.() -> Unit) {
    FlowRow(
        modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRowScopeMarker.content()
    }
}

/**
 * ponytail: a marker object rather than a receiver-typed scope. `FlowRowScope`
 * carries `Modifier.weight`, which these fixed-width tiles must not use -- a
 * weighted tile stretches to fill its row and the grid stops being a grid.
 */
object FlowRowScopeMarker

@Composable
fun FlowRowScopeMarker.HubAction(
    label: String,
    icon: ImageVector,
    kind: ActionKind,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        // A fixed width, so the tiles line up into columns instead of forming
        // ragged rows that shift whenever a label's length changes.
        modifier = Modifier.width(104.dp),
    ) {
        Column(
            Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tintOf(kind))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
