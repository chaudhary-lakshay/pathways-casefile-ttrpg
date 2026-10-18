package com.pathways.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A pinned foot as the frames draw it (14, 17-22): a 2 dp ink rule over a
 * paper band holding the target's terms, the target and the button.
 */
@Composable
internal fun FooterBand(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            content = content,
        )
    }
}

/**
 * The target in an ink box. Always laid out: with no number (a roll the
 * book skips or forbids) the box is blank and unread, so a pick that brings
 * the number back cannot grow the foot under a finger.
 */
@Composable
internal fun BoxedTarget(target: Int?) {
    Box(
        Modifier
            .heightIn(min = 56.dp)
            .widthIn(min = 56.dp)
            .then(if (target == null) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(2.dp, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            target?.toString() ?: "0",
            style = MaterialTheme.typography.headlineMedium.tabular().copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/** The foot's one action, in mono capitals; the source keeps the words as written. */
@Composable
internal fun RollButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: ImageVector = Icons.Filled.Casino,
) {
    Button(shape = ButtonShape, onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Caps(
            label,
            Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold),
            color = LocalContentColor.current,
        )
    }
}

/**
 * The boxed target and its caption, then the button: one line where they
 * fit, the button under them where they do not. In one Row at 390 dp and
 * font 1.3 the caption was squeezed to a letter a line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FooterRow(
    target: Int?,
    button: @Composable () -> Unit,
    /** False where the target card above already boxes the number (frame 17). */
    boxed: Boolean = true,
    caption: @Composable () -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (boxed) BoxedTarget(target)
            Box(Modifier.padding(start = if (boxed) 10.dp else 0.dp, end = 10.dp)) { caption() }
        }
        button()
    }
}

/**
 * The foot of a bare roll's choices: what moved the target, the target, and
 * Roll, pinned below the scrolling choices as in the Attack and ritual
 * dialogs. With nothing to list one empty line is still laid out, so the
 * first declared clause cannot grow the dialog under a finger. [target] is
 * null where [caption] says no roll is made.
 */
@Composable
internal fun RollFooter(
    lines: List<String>,
    caption: String,
    target: Int?,
    enabled: Boolean = true,
    label: String = "Roll",
    icon: ImageVector = Icons.Filled.Casino,
    onRoll: () -> Unit,
) {
    FooterBand {
        lines.ifEmpty { listOf("") }.forEach {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FooterRow(target, button = { RollButton(label, onRoll, enabled, icon) }) {
            Text(caption, style = MaterialTheme.typography.labelLarge.tabular())
        }
    }
}
