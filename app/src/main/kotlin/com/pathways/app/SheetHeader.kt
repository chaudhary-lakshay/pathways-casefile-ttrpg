package com.pathways.app

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathways.model.BeyonderState

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
            style = MaterialTheme.typography.displaySmall,
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
    val mono = MaterialTheme.typography.labelLarge.copy(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold)
    when (fact.ink) {
        PillInk.STAMP -> Stamp(fact.text, MaterialTheme.colorScheme.secondary)
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
            Modifier.border(2.dp, MaterialTheme.colorScheme.outline).padding(horizontal = 10.dp, vertical = 4.dp),
            style = mono,
            color = MaterialTheme.colorScheme.onSurface,
        )
        PillInk.MUTE -> Caps(
            fact.text,
            Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 10.dp, vertical = 4.dp),
            style = mono,
        )
    }
}

/**
 * The top bar's back and menu buttons, boxed in [ink] as the frames draw
 * them. A plain modifier, not a composable one: the caller reads the colour.
 */
internal fun Modifier.boxed(ink: Color): Modifier = border(1.dp, ink, RoundedCornerShape(6.dp))
