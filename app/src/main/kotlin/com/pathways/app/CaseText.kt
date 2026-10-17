package com.pathways.app

import androidx.compose.foundation.clickable
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
internal fun Modifier.dashedBorder(color: Color): Modifier = drawBehind {
    drawRect(
        color,
        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))),
    )
}

/** Every button's corners. Material draws its buttons as pills and takes no theme shape for them. */
internal val ButtonShape = RoundedCornerShape(2.dp)

/**
 * A roll dialog's title as a typed form: the form's code above, the title,
 * a 2 dp ink rule below. The code is decoration the owner chose to ship
 * (2026-10-17), one fixed string per dialog, so a screen reader skips it.
 * A Column, because the title slot stacks bare children.
 */
@Composable
internal fun FormTitle(form: String, file: String? = null, title: @Composable () -> Unit) {
    Column {
        Row(Modifier.clearAndSetSemantics {}) {
            val style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.1.em)
            Text(form, Modifier.weight(1f).padding(end = 8.dp), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/**
 * A dialog's way out, in stamp red mono capitals as the frames draw it. Red
 * otherwise means harm or the Beyonder; the owner picked it for dismiss
 * anyway (2026-10-17), and this is the one place to change it.
 */
@Composable
internal fun CloseButton(onClick: () -> Unit, label: String = "Close") {
    TextButton(
        onClick = onClick,
        shape = ButtonShape,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
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
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
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
            color = color,
        )
    }
}
