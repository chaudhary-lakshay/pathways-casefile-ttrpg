package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * The Case File's typed-form text: capitals, tracked wide.
 *
 * Capitals on screen only. The source string stays as written, because
 * `docs/HOW-TO-PLAY.md` is checked against the labels in source and a
 * screen reader should read the words, not spell them (Codex, slice 1's
 * design review: Compose `TextStyle` cannot uppercase).
 */
@Composable
internal fun Caps(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    spoken: String = text,
) {
    Text(
        text.uppercase(),
        // One text node in the source's words, so a reader, a test's
        // onNodeWithText and the device-pass dump all find "Close".
        modifier.clearAndSetSemantics { this.text = AnnotatedString(spoken) },
        color = color,
        style = style.copy(letterSpacing = 0.1.em),
    )
}

/** A 2 dp dashed rule round a box: the frames' mark for "start something" (frames 01 and 04). */
internal fun Modifier.dashedBorder(color: Color, width: Dp = 2.dp): Modifier = drawBehind {
    drawRect(
        color,
        style = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx() + width.toPx() * 2, 5.dp.toPx()))),
    )
}

/** A 1 dp dashed rule under a row: the frames' divider between a table's rows (frame 17). */
internal fun Modifier.dashedRule(color: Color): Modifier = drawBehind {
    val y = size.height - 0.5.dp.toPx()
    drawLine(
        color,
        start = androidx.compose.ui.geometry.Offset(0f, y),
        end = androidx.compose.ui.geometry.Offset(size.width, y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
    )
}

/** Every button's corners. Material draws its buttons as pills and takes no theme shape for them. */
internal val ButtonShape = RoundedCornerShape(2.dp)

/**
 * A roll dialog's title as a typed form: the form's code above, the title,
 * a 2 dp ink rule below. The code is decoration the owner chose to ship
 * (2026-10-17), one fixed string per dialog, so a screen reader skips it.
 * A Column, because the title slot stacks bare children.
 *
 * A null [form] is a dialog no frame draws and no code names (a picker, a
 * confirm): the title and its rule alone, the owner's ruling of 2026-10-18.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FormTitle(form: String?, file: String? = LocalFileLabel.current, title: @Composable () -> Unit) {
    Column {
        // The file number drops under the code where both do not fit: in one
        // Row at 390 dp and font 1.3 the code wrapped mid-word.
        if (form != null) FlowRow(
            Modifier.fillMaxWidth().clearAndSetSemantics {},
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.1.em)
            Text(form, Modifier.padding(end = 8.dp), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (file != null) Text(file.uppercase(), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        title()
        HorizontalDivider(Modifier.padding(top = 8.dp), thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
    }
}

/** The forms the frames print (Figma, frames 12-22): the chapter, then a letter. */
internal const val FORM_SKILL = "FORM 3-S · SKILL ROLL"
internal const val FORM_FRIGHT = "FORM 4-F · FRIGHT"
internal const val FORM_ATTACK = "FORM 5-A · ATTACK"
internal const val FORM_DEFEND = "FORM 5-D · DEFENCE"
internal const val FORM_DAMAGE = "FORM 5-W · INJURY"
internal const val FORM_PCR = "FORM 6-P · CONSUMPTION"
internal const val FORM_RITUAL = "FORM 7-R · RITUAL"
internal const val FORM_DIVINE = "FORM 7-D · DIVINATION"
internal const val FORM_SIGHT = "FORM 7-V · SIGHT"
internal const val FORM_DETECT = "FORM 7-X · COUNTER"
internal const val FORM_BOON = "FORM 13-B · PETITION"
internal const val FORM_INTAKE = "FORM 23-B · INTAKE"

/** The four roll dialogs no frame draws, coded the same way (the owner, 2026-10-18). */
internal const val FORM_UNTRAINED = "FORM 2-U · UNTRAINED"
internal const val FORM_ATTRIBUTE = "FORM 3-A · ATTRIBUTE ROLL"
internal const val FORM_CONTROL = "FORM 3-C · SELF-CONTROL"
internal const val FORM_CONCENTRATE = "FORM 5-C · CONCENTRATE"

/**
 * An action in a dialog's or a row's body, as frame 16 draws "ROLL HT 9":
 * an ink-ruled box, mono capitals. Every undrawn one takes it too (the
 * owner, 2026-10-18). A footer's actions are [CloseButton] and a filled
 * button, as the frames' footers are.
 */
@Composable
internal fun InkAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        shape = ButtonShape,
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
        Caps(label, style = MaterialTheme.typography.labelLarge.tabular(), color = LocalContentColor.current)
    }
}

/** A dialog's main footer action as frame 05 draws "Create": filled ink, mono capitals. */
@Composable
internal fun InkButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(shape = ButtonShape, onClick = onClick, enabled = enabled) {
        Caps(label, style = MaterialTheme.typography.labelLarge.tabular(), color = LocalContentColor.current)
    }
}

/**
 * A dialog's way out, in stamp red mono capitals as the frames draw it. Red
 * otherwise means harm or the Beyonder; the owner picked it for dismiss
 * anyway (2026-10-17), and this is the one place to change it.
 */
@Composable
internal fun CloseButton(
    onClick: () -> Unit,
    label: String = "Close",
    /** Ink for a dialog's other text action ("Another turn"), which is not a way out. */
    color: Color = MaterialTheme.colorScheme.secondary,
) {
    TextButton(
        onClick = onClick,
        shape = ButtonShape,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
    ) {
        Caps(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold),
            color = LocalContentColor.current,
        )
    }
}

/**
 * A secondary action as typed text, "[ Change ]" (Case File, frames 09 and
 * 23). A 48 dp target, and read as its label alone: the brackets are ink,
 * not words.
 */
@Composable
internal fun BracketAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    /** Underlined, as the frames draw the two that leave the hub's dialogs. */
    underlined: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "[ $label ]",
            Modifier.clearAndSetSemantics { text = AnnotatedString(label) },
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = PlexMono,
                textDecoration = if (underlined) TextDecoration.Underline else null,
            ),
            color = if (enabled) color else color.copy(alpha = 0.38f),
        )
    }
}
