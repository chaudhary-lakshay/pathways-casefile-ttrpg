package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// The stepper shapes the owner picked from the 2026-10-13 component round:
// a joined one for a builder row, a wide one in a card for the two pathway
// trackers. Every step button is neutral: a colour on "-5%" would say the
// step is bad, and the book prints only the figure.

@Composable
private fun glyphColors() = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)

/** One step of a joined stepper: a square cell, read by [label] when it has one. */
@Composable
private fun StepGlyph(glyph: String, onClick: () -> Unit, enabled: Boolean, label: String?) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RectangleShape,
        colors = glyphColors(),
        modifier = if (label != null) Modifier.semantics { contentDescription = label } else Modifier,
    ) { Text(glyph, style = MaterialTheme.typography.titleLarge) }
}

/**
 * Minus, the value and plus as joined cells in one ink box, at a row's end
 * (Case File, frame 23). With no [value] it is the bare pair, for a row
 * whose label already prints the number.
 */
@Composable
internal fun JoinedStepper(
    value: String?,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
    valueColor: Color = Color.Unspecified,
    minusLabel: String? = null,
    plusLabel: String? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            StepGlyph("−", onMinus, minusEnabled, minusLabel)
            VerticalDivider(color = MaterialTheme.colorScheme.outline)
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge.tabular(),
                    color = valueColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 44.dp).padding(horizontal = 4.dp),
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outline)
            }
            StepGlyph("+", onPlus, plusEnabled, plusLabel)
        }
    }
}

/**
 * A titled card whose first row is a wide stepper: a step at each end and
 * the value centred between them. [content] is what the card says under it.
 */
@Composable
internal fun StepperCard(
    title: String,
    minus: String,
    plus: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    enabled: Boolean = true,
    valueColor: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onMinus, enabled = enabled, shape = ButtonShape, colors = glyphColors()) { Text(minus) }
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium.tabular(),
                    color = valueColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onPlus, enabled = enabled, shape = ButtonShape, colors = glyphColors()) { Text(plus) }
            }
            content()
        }
    }
}

/** A fact in a ruled box beside a row, never a control. */
@Composable
internal fun ChipTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.tabular(),
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * One row of the builder's ledger of sections. Each is its own list item; a
 * rule under every row but the [last]. Case File draws the ledger straight
 * on the paper, so there is no card ([first] is kept for the top gap).
 */
@Composable
internal fun SectionSurface(first: Boolean = false, last: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = if (first) 12.dp else 0.dp)) {
        content()
        if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * A builder section's head: "§ NAME" at the left, what is held in the
 * middle, and its one action, bracketed, at the right.
 */
@Composable
internal fun SectionHead(
    heading: String,
    action: String,
    onAction: () -> Unit,
    held: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Caps(
            "§ $heading",
            Modifier.widthIn(min = 104.dp).padding(end = 12.dp),
            spoken = heading,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(Modifier.weight(1f)) { held() }
        BracketAction(action, onAction, Modifier.padding(start = 8.dp))
    }
}

/** A section's name above its chips, in the roll dialogs: a typed-form label. */
@Composable
internal fun SectionLabel(text: String) {
    Caps(text, Modifier.padding(top = 16.dp, bottom = 6.dp))
}
