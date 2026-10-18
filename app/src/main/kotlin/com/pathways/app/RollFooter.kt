package com.pathways.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
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
 * Whether a pinned foot puts its button under the target rather than beside
 * it. Decided by the window and the text size alone, never by the caption:
 * a FlowRow that wrapped on the caption's length moved Roll to another line
 * when a pick lengthened "3d6 ≤ 9" to "3d6 ≤ 16, 17–18 fail" (Codex, 6ii).
 *
 * ponytail: one threshold in text-scaled dp, measured on the SM-T570 (600
 * dp at 1.0 fits the longest caption beside the widest button; 390 dp at
 * 1.3 squeezed it to a letter a line). Measure the slot instead if a
 * foot ever sits outside a dialog.
 */
internal fun footerStacks(screenWidthDp: Int, fontScale: Float): Boolean =
    screenWidthDp / fontScale < FOOTER_ONE_LINE_MIN

private const val FOOTER_ONE_LINE_MIN = 520f

/**
 * The boxed target and its caption, then the button: beside them on a wide
 * window, under them on a narrow one ([footerStacks]). The caption keeps the
 * box's height either way, so a pick that wraps it to two lines grows nothing.
 */
@Composable
internal fun FooterRow(
    target: Int?,
    button: @Composable () -> Unit,
    /** False where the target card above already boxes the number (frame 17). */
    boxed: Boolean = true,
    caption: @Composable () -> Unit,
) {
    val stacks = footerStacks(LocalConfiguration.current.screenWidthDp, LocalDensity.current.fontScale)
    val targetAndCaption: @Composable RowScope.() -> Unit = {
        if (boxed) BoxedTarget(target)
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .padding(start = if (boxed) 10.dp else 0.dp, end = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) { caption() }
    }
    if (stacks) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, content = targetAndCaption)
            button()
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            targetAndCaption()
            button()
        }
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
