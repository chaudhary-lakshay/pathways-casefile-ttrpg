package com.pathways.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pathways.model.BeyonderState
import java.util.Locale

/** The gallery's pieces, as Case File frame 01 draws them. Display only: every tap is the caller's. */

private val NUMBER_WIDTH = 40.dp
private val STAT_WIDTH = 30.dp
private val FOLDER = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)

/** A file number as both the gallery and the sheet print it, "06". */
internal fun fileDigits(number: Int): String = String.format(Locale.ROOT, "%02d", number)

/**
 * The index's kicker, the title and the glossary. The kicker is frame 01's
 * decoration, kept as drawn on "be truthful to the figma screen"; a reader
 * skips it. The frame's palette button is left out: there is one theme.
 */
@Composable
internal fun GalleryHead(onGlossary: () -> Unit) {
    Column(Modifier.padding(top = 16.dp)) {
        Row(Modifier.clearAndSetSemantics {}) {
            Kicker("Midnight Poem · Case Index", Modifier.weight(1f))
            Kicker("Vol. III")
        }
        HorizontalDivider(Modifier.padding(top = 6.dp), thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)
        Row(Modifier.padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Characters",
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.displaySmall,
            )
            BoxedIconButton(Icons.AutoMirrored.Filled.MenuBook, "Glossary", onGlossary)
        }
    }
}

@Composable
private fun Kicker(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.1.em),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The three filters as folder tabs over the table, each with its count: a
 * tab reading "Beyonders 8" says how much list is behind it. The open
 * tab's count is in stamp red, as drawn.
 */
@Composable
internal fun RosterTabs(roster: Roster, counts: Map<Roster, Int>, onRoster: (Roster) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (option in Roster.entries) {
            val open = option == roster
            val count = counts.getValue(option)
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .background(
                        if (open) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        FOLDER,
                    )
                    .border(
                        1.5.dp,
                        if (open) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
                        FOLDER,
                    )
                    .selectable(selected = open, role = Role.Tab, onClick = { onRoster(option) })
                    .semantics { contentDescription = "${option.label}, $count" }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = PlexMono,
                    fontWeight = if (open) FontWeight.SemiBold else FontWeight.Normal,
                    letterSpacing = 0.05.em,
                )
                val ink = if (open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                Text(option.label.uppercase(), Modifier.clearAndSetSemantics {}, style = style, color = ink)
                Text(
                    " $count",
                    Modifier.clearAndSetSemantics {},
                    style = style,
                    color = if (open) MaterialTheme.colorScheme.secondary else ink,
                )
            }
        }
    }
}

/** The table's column heads under a 2 dp rule: NO., SUBJECT, then the four attributes. */
@Composable
internal fun GalleryTableHead() {
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainer).tableSides()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).clearAndSetSemantics {}) {
            HeadCell("No.", Modifier.width(NUMBER_WIDTH))
            HeadCell("Subject", Modifier.weight(1f))
            for (attr in listOf("ST", "DX", "IQ", "HT")) HeadCell(attr, Modifier.width(STAT_WIDTH), TextAlign.End)
        }
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun HeadCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text.uppercase(),
        modifier,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.05.em),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
    )
}

/** The table's two side rules, drawn per item so the list stays lazy. */
@Composable
private fun Modifier.tableSides(): Modifier {
    val rule = MaterialTheme.colorScheme.outlineVariant
    return drawBehind {
        val w = 1.5.dp.toPx()
        drawLine(rule, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
        drawLine(rule, Offset(size.width - w / 2, 0f), Offset(size.width - w / 2, size.height), w)
    }
}

/**
 * One person: the file number, the name in Fraunces, the walk's stamp, the
 * two traits and skill count, and the four attributes as played. A lost
 * character's row is red, its stamp filled, as the sheet's loss pill is;
 * "lost" is said aloud, since the colour alone says nothing to a reader.
 */
@Composable
internal fun GalleryRow(
    file: Int?,
    name: String,
    walk: BeyonderState?,
    lost: Boolean,
    numbers: PlayedRow,
    onClick: () -> Unit,
) {
    val red = MaterialTheme.colorScheme.secondary
    Column(
        Modifier
            .background(if (lost) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer)
            .tableSides()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.Top) {
            Text(
                file?.let(::fileDigits).orEmpty(),
                Modifier.width(NUMBER_WIDTH).padding(top = 4.dp).clearAndSetSemantics {},
                style = MaterialTheme.typography.labelMedium,
                color = if (lost) red else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize))
                WalkStamp(walk, lost)
                Text(
                    galleryNote(numbers, spoken = false),
                    Modifier.semantics { contentDescription = galleryNote(numbers, spoken = true) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val played = numbers.attributes
            for ((label, value) in listOf("ST" to played.st, "DX" to played.dx, "IQ" to played.iq, "HT" to played.ht)) {
                Text(
                    "$value",
                    Modifier.width(STAT_WIDTH).padding(top = 4.dp).semantics { contentDescription = "$label $value" },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = PlexMono,
                        fontWeight = if (isStrong(value)) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    textAlign = TextAlign.End,
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Red outline for a walk, grey for a mortal, filled red for a loss. Straight, as frame 01 draws it. */
@Composable
private fun WalkStamp(walk: BeyonderState?, lost: Boolean) {
    val text = walkStamp(walk)
    val red = MaterialTheme.colorScheme.secondary
    val line = when {
        lost -> red
        walk != null -> red
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Caps(
        text,
        Modifier
            .then(if (lost) Modifier.background(red) else Modifier)
            .border(1.5.dp, line)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = when {
            lost -> MaterialTheme.colorScheme.onSecondary
            walk != null -> red
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        spoken = if (lost) "$text, lost" else text,
    )
}

/**
 * The blank sheet, opened unnamed: below the people, in the dashed box the
 * frames give a fresh start. Frame 01 does not draw it; the owner kept it
 * (2026-10-18), since a save made on it is reachable from nowhere else.
 */
@Composable
internal fun BlankSheetButton(onClick: () -> Unit) {
    Row(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .dashedBorder(MaterialTheme.colorScheme.outline)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(20.dp))
        Caps(
            "Start from a blank sheet",
            Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** New character, pinned at the foot as frame 01's ink bar. */
@Composable
internal fun OpenFileButton(onClick: () -> Unit) {
    Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Icon(Icons.Filled.CreateNewFolder, contentDescription = null, Modifier.size(20.dp))
            Caps(
                "Open new file",
                Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
