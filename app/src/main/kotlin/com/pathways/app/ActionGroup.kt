package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The sheet's actions in two weights: four big buttons for what a turn
 * reaches for, and bracketed words for the rest.
 *
 * This replaces one grid of thirteen equal tiles, which spent 40% of a tablet
 * screen before a single stat and gave the Attack button the same weight as
 * Detect. Before the grid there was a scrolling row that hid nine actions,
 * and before that four labelled chip groups that read as four lists. The
 * bracketed words keep every action one tap away. The big buttons are what
 * is used every turn.
 *
 * **Red means harm or the Beyonder, and nothing else** (Case File, frame 09):
 * Damage's icon, and the Beyonder's four words. Every other action is ink.
 * This replaces the one-neutral-colour ruling of 2026-10-17, which rejected
 * a hue per category as "colourful AI slop"; one red with two meanings is
 * the rule `a9be8f0` set for the whole app.
 */

/**
 * The four big buttons as one ruled grid (Case File, frame 09): the ink
 * shows through a 1 dp gap between the cells, so the rules need no dividers
 * of their own.
 */
@Composable
internal fun ActionGrid(content: @Composable RowScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.outline,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(1.dp), content = content)
    }
}

/**
 * One of the big buttons: icon over a capitalised label, 64dp tall. The
 * caller sets its width. [harm] reds the icon, as Damage's is drawn.
 */
@Composable
internal fun PrimaryAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    harm: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxHeight().heightIn(min = 64.dp),
    ) {
        Column(
            Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (harm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Caps(
                label,
                style = MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Center),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * A secondary action, "[ Boon ]". The Beyonder's own ([mystical]) are in
 * the stamp red that marks the Beyonder everywhere else.
 */
@Composable
internal fun ActionChip(
    label: String,
    mystical: Boolean = false,
    underlined: Boolean = false,
    onClick: () -> Unit,
) {
    BracketAction(
        label,
        onClick,
        color = if (mystical) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
        underlined = underlined,
    )
}
