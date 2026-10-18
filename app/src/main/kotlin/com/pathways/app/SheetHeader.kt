package com.pathways.app

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathways.model.BeyonderState
import com.pathways.rules.PremadeSheet

/**
 * A character's file number: its place among the gallery's people, from one,
 * premades first and own characters after, as [App] lists them. Null for the
 * blank sheet, which is a starting point and nobody's file. The frames'
 * "FILE Nº" is decoration the owner chose to ship (2026-10-17); the gallery
 * and the sheet read this one numbering.
 */
internal fun fileNumber(sheet: PremadeSheet, sheets: List<PremadeSheet>): Int? =
    sheets.filterNot(::isTemplate).indexOfFirst { it.name == sheet.name }.takeIf { it >= 0 }?.plus(1)

/** "File Nº 06"; null for the blank sheet, which has no file. */
internal fun fileLabel(number: Int?): String? = number?.let { "File Nº ${fileDigits(it)}" }

/**
 * The open file's [fileLabel], which every dialog's form header prints at
 * its right (Case File, frames 13-22). Provided once around the sheet and
 * pathway screens, so no dialog takes a parameter for decoration.
 */
internal val LocalFileLabel = staticCompositionLocalOf<String?> { null }

/** The sheet bar's caption: "File Nº 06 · Beyonder" (Case File, frame 02). */
internal fun fileLine(number: Int?, isBeyonder: Boolean): String =
    listOfNotNull(fileLabel(number), if (isBeyonder) "Beyonder" else "Mortal")
        .joinToString(" · ")

/** How a header fact is inked (Case File, frames 02-04). */
internal enum class PillInk { LOST, STAMP, INK, MUTE }

/** One fact about the walk, in the sheet's header. */
internal data class HeaderFact(val text: String, val ink: PillInk)

/**
 * The walk's facts, in the order the header prints them. A boon is held
 * beside a potion walk, or by a mortal; neither, and there is nothing to
 * say. Lost comes first, and turns every fact, so a narrow screen cannot
 * wrap it out of sight.
 */
internal fun headerFacts(
    drunk: BeyonderState?,
    boon: String?,
    cor: Int,
    lost: Boolean,
    corrupted: Boolean,
): List<HeaderFact> {
    if (drunk == null && boon == null) return emptyList()
    val facts = listOfNotNull(
        when {
            lost -> HeaderFact("Lost to the Rampager", PillInk.LOST)
            corrupted -> HeaderFact("Lost to corruption", PillInk.LOST)
            else -> null
        },
        drunk?.let { HeaderFact("${it.pathway} · Seq ${it.sequence}", PillInk.STAMP) },
        drunk?.let { HeaderFact("Digested ${it.digestion}%", PillInk.INK) },
        boon?.let { HeaderFact(it, PillInk.INK) },
        HeaderFact("CoR $cor", PillInk.MUTE),
    )
    return if (lost || corrupted) facts.map { it.copy(ink = PillInk.LOST) } else facts
}

/**
 * The character's name in Fraunces, then the walk's facts. Out of the top
 * bar, so a long name wraps rather than ellipsizes; a heading, because it
 * left the bar's title role behind.
 *
 * The sheet rows below are still the mortal's: the diff they carry is
 * against the published sheet, and no published sheet records a pathway.
 */
@Composable
internal fun SheetTitle(name: String, facts: List<HeaderFact>) {
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        Text(
            name,
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
        )
        if (facts.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                facts.forEach { HeaderPill(it) }
            }
        }
    }
}

@Composable
private fun HeaderPill(fact: HeaderFact) {
    val mono = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
    when (fact.ink) {
        PillInk.STAMP -> Stamp(fact.text, MaterialTheme.colorScheme.secondary, mono)
        // Filled, so a loss reads apart from the stamp a healthy walk wears.
        PillInk.LOST -> Surface(color = MaterialTheme.colorScheme.error, shape = MaterialTheme.shapes.extraSmall) {
            Caps(
                fact.text,
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = mono,
                color = MaterialTheme.colorScheme.onError,
            )
        }
        PillInk.INK -> Caps(
            fact.text,
            Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline).padding(horizontal = 8.dp, vertical = 4.dp),
            style = mono,
            color = MaterialTheme.colorScheme.onSurface,
        )
        PillInk.MUTE -> Caps(
            fact.text,
            Modifier.border(1.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 8.dp, vertical = 4.dp),
            style = mono,
        )
    }
}

/**
 * The top bar's back and menu buttons: a 36 dp ink box as the frames draw
 * them, inside the 48 dp target every control keeps.
 */
@Composable
internal fun BoxedIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(36.dp)
            .clip(shape)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, Modifier.size(20.dp))
    }
}
