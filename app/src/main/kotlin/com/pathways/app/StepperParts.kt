package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// The stepper shapes the owner picked from the 2026-10-13 component round:
// a joined one for a builder row, a wide one in a card for the two pathway
// trackers. Every step button is neutral: a colour on "-5%" would say the
// step is bad, and the book prints only the figure.

private val STEP_SHAPE = RoundedCornerShape(10.dp)

@Composable
private fun neutralColors() = ButtonDefaults.filledTonalButtonColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun glyphColors() = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)

/** Minus, the value and plus in one container, at a builder row's end. */
@Composable
internal fun JoinedStepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
    valueColor: Color = Color.Unspecified,
) {
    Surface(shape = STEP_SHAPE, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onMinus, enabled = minusEnabled, shape = STEP_SHAPE, colors = glyphColors()) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = valueColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 44.dp),
            )
            TextButton(onClick = onPlus, enabled = plusEnabled, shape = STEP_SHAPE, colors = glyphColors()) { Text("+", style = MaterialTheme.typography.titleLarge) }
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
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Surface(
                shape = STEP_SHAPE,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp),
            ) {
                Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = onMinus, enabled = enabled, shape = STEP_SHAPE, colors = neutralColors()) { Text(minus) }
                    Text(
                        value,
                        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                        color = valueColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = onPlus, enabled = enabled, shape = STEP_SHAPE, colors = neutralColors()) { Text(plus) }
                }
            }
            content()
        }
    }
}

/** A filled, neutral chip: a fact beside a row, never a control. */
@Composable
internal fun ChipTag(text: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * One row of the builder's card of sections. Each is its own list item, so
 * the card's corners are the [first] row's top and the [last] row's bottom.
 */
@Composable
internal fun SectionSurface(first: Boolean = false, last: Boolean = false, content: @Composable () -> Unit) {
    val top = if (first) 12.dp else 0.dp
    val bottom = if (last) 12.dp else 0.dp
    Surface(
        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = top),
    ) {
        Column {
            content()
            if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * A builder section's head: its name as a caption at the left, what is held
 * in the middle, and its one action at the right.
 */
@Composable
internal fun SectionHead(
    heading: String,
    action: String,
    onAction: () -> Unit,
    held: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            heading.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 104.dp).padding(end = 12.dp),
        )
        Column(Modifier.weight(1f)) { held() }
        FilledTonalButton(onClick = onAction, shape = STEP_SHAPE, colors = neutralColors(), modifier = Modifier.padding(start = 8.dp)) {
            Text(action)
        }
    }
}
