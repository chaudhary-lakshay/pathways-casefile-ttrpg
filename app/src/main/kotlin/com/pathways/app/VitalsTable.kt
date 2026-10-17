package com.pathways.app

import com.pathways.rules.AttributeRoll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The derived values as frame 03 draws them: the notice that counts the
 * disagreements, stamped AMENDED when there are any, then a table of STAT,
 * the SHEET's figure and the number played NOW. A row the published sheet
 * disagrees with is red, its sheet figure struck; Will and Per roll.
 *
 * [sheetFigures] is each row's published figure by label, null for an own
 * character, which has no published sheet and so no SHEET column.
 */
@Composable
internal fun VitalsCard(
    diffSummary: String?,
    amended: Boolean,
    amendedOnly: Boolean,
    onAmendedOnly: (Boolean) -> Unit,
    lines: List<PlayedLine>,
    sheetFigures: Map<String, String?>?,
    onRoll: (AttributeRoll.Kind) -> Unit,
) {
    val red = MaterialTheme.colorScheme.error
    val tint = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    Column(Modifier.padding(vertical = 8.dp)) {
        if (diffSummary != null) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (amended) tint else MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.5.dp, if (amended) red else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        val count = diffSummary.substringBefore(" values")
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold)) { append(count) }
                                append(diffSummary.removePrefix(count))
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(end = 72.dp),
                        )
                        if (amended) {
                            Text(
                                if (amendedOnly) "Show all values" else "Show amendments",
                                style = MaterialTheme.typography.labelMedium,
                                color = red,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .heightIn(min = 48.dp)
                                    .clickable(role = Role.Button) { onAmendedOnly(!amendedOnly) }
                                    .semantics { stateDescription = if (amendedOnly) "Showing amendments only" else "Showing all values" }
                                    .wrapContentHeight(),
                            )
                        }
                    }
                }
                if (amended) {
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 4.dp)) {
                        Stamp("Amended", red, MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
        SheetSection("Secondary characteristics")
        TableHead(listOfNotNull("Stat", sheetFigures?.let { "Sheet" }, "Now"))
        lines.filter { !amendedOnly || it.disagreement != null }.forEach { line ->
            val differs = line.disagreement != null
            // Will and Per open their bare roll; the rest are numbers.
            val rolls = AttributeRoll.Kind.entries.firstOrNull { it.label == line.label }
            Column(Modifier.background(if (differs) tint else Color.Transparent)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (rolls == null) {
                                Modifier
                            } else {
                                Modifier.clickable(onClickLabel = "Roll ${rolls.label}", role = Role.Button) { onRoll(rolls) }
                            },
                        )
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                spelledOut(line.label),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (differs) red else Color.Unspecified,
                            )
                            if (rolls != null) RollGlyph(MaterialTheme.colorScheme.outline, 28.dp)
                        }
                        // The engine's number for the unedited sheet, where the
                        // character has moved off it and the sheet disputes it.
                        line.basis?.let {
                            Text(it.removePrefix(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    sheetFigures?.let { figures ->
                        val figure = figures[line.label] ?: "—"
                        Text(
                            figure,
                            style = MaterialTheme.typography.titleMedium.tabular().copy(
                                textDecoration = if (differs) TextDecoration.LineThrough else null,
                            ),
                            color = if (differs) red else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            // A reader hears neither red nor a strike.
                            modifier = Modifier.width(72.dp).semantics {
                                contentDescription = if (differs) "sheet $figure, disputed" else "sheet $figure"
                            },
                        )
                    }
                    Text(
                        line.number,
                        style = MaterialTheme.typography.titleLarge.tabular().copy(fontWeight = FontWeight.SemiBold),
                        color = if (differs) red else Color.Unspecified,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(72.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/**
 * A secondary's name as frame 03 spells it. Display only: the label stays
 * the comparison's, which pairs the rows and names Will and Per's rolls.
 */
internal fun spelledOut(label: String): String = when (label) {
    "HP" -> "Hit Points"
    "FP" -> "Fatigue Points"
    "Per" -> "Perception"
    else -> label
}

/** A table's column heads in muted mono capitals, the first left and the rest right. */
@Composable
internal fun TableHead(heads: List<String>, widths: List<Dp> = heads.drop(1).map { 72.dp }) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Caps(heads.first(), Modifier.weight(1f))
            heads.drop(1).zip(widths).forEach { (head, width) ->
                Box(Modifier.width(width), contentAlignment = Alignment.CenterEnd) { Caps(head) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
