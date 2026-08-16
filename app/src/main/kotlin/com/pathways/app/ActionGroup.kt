package com.pathways.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A labelled group of actions that wraps instead of scrolling.
 *
 * The sheet screen used to put every action -- thirteen of them -- in one
 * `horizontalScroll` row. Nine were off-screen on a 1080px phone with nothing
 * indicating they existed, and the row sat ABOVE the character's name, so the
 * first thing after tapping a character was a strip of unexplained verbs.
 *
 * Wrapping is the fix rather than a wider scroller: a `FlowRow` cannot hide a
 * child, so an action that exists is an action that is visible. The label
 * says what the group is FOR, which is the part a bare verb cannot carry --
 * "Detect" means nothing until it sits under "Rolls the GM calls for".
 *
 * The caller decides whether a group appears at all. A group whose members
 * are all conditional (the Beyonder powers) must be wrapped in the same
 * condition by the caller, or it renders as a heading with nothing under it.
 */
@Composable
fun ActionGroup(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/**
 * One action inside an [ActionGroup].
 *
 * ponytail: a thin wrapper over [AssistChip], because every call site would
 * otherwise repeat the same colours block twelve times. It exists to stop
 * that repetition, not to abstract the chip.
 */
@Composable
fun Action(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
