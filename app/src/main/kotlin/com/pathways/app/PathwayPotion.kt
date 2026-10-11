package com.pathways.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.pathways.rules.DigestedCharacter
import com.pathways.rules.PotionDigestion
import com.pathways.rules.content.SequenceBlock

/**
 * What one potion grants, as cards a player scans: the stat gains as pills,
 * the skills with their level, then the book's own text for advantages,
 * drawbacks and abilities. Every word of that text is the book's; only the
 * layout is this screen's ([abilityParagraphs]).
 */
internal fun LazyListScope.potionContent(block: SequenceBlock, digested: DigestedCharacter) {
    item {
        PotionCard("Gains") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (block.stats.isEmpty()) Text("No stat gains", style = MaterialTheme.typography.bodyMedium)
                block.stats.forEach { Pill(it.attr, formatDelta(it.delta)) }
                if (digested.grants.spi != 0) Pill("SPI after digestion", digested.effectiveSpi.toString(), quiet = true)
            }
            block.stats.filter { it.note.isNotEmpty() }.forEach {
                Text("${it.attr} ${it.note}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            if (block.sequence < PotionDigestion.STARTING_SEQUENCE) {
                Text(
                    "Levels and totals count every potion from Sequence " +
                        "${PotionDigestion.STARTING_SEQUENCE} down to ${block.sequence}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    if (block.skills.isNotEmpty() || block.unresolvedSkills.isNotEmpty()) {
        item {
            PotionCard("Skills") {
                block.skills.forEach { grant ->
                    // The full identity, not the base name: Death grants Hidden
                    // Lore (Spirits) at Seq 8 and (Spirit World) at Seq 6.
                    val base = grant.name.substringBefore(" (")
                    val spec = if (" (" in grant.name) grant.name.substringAfter(" (").removeSuffix(")") else null
                    val level = digested.grants.skillLevels.entries.firstOrNull {
                        it.key.name == base && it.key.specialization == spec && it.key.difficulty == grant.difficulty
                    }?.value
                    SkillLine(grant.name, grant.note, level?.toString() ?: "+${grant.bonus}")
                }
                // Printed, and naming no skill the book lists (6gk): shown as
                // printed, with nothing computed from it.
                block.unresolvedSkills.forEach { printed ->
                    SkillLine(
                        printed.substringBefore("/"),
                        "names no skill the book lists; the GM assigns it",
                        printed.substringAfterLast(" "),
                    )
                }
            }
        }
    }
    if (block.advantages.isNotEmpty()) item { PotionCard("Advantages") { BookText(block.advantages) } }
    if (block.drawbacks.isNotEmpty()) item { PotionCard("Drawbacks") { BookText(block.drawbacks) } }
    item { PotionCard("Abilities") { Abilities(block.ability) } }
    if (block.reinforcedAbilities.isNotEmpty()) {
        item { PotionCard("Reinforced abilities") { Abilities(block.reinforcedAbilities) } }
    }
}

@Composable
private fun PotionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            content()
        }
    }
}

@Composable
private fun Pill(label: String, value: String, quiet: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (quiet) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                " $value",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (quiet) Color.Unspecified else MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun SkillLine(name: String, note: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (note.isNotEmpty()) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

/** Each named run as its own block: the name in the accent colour, then its text. */
@Composable
private fun Abilities(text: String) {
    abilityParagraphs(text).forEachIndexed { i, paragraph ->
        Column(Modifier.padding(top = if (i == 0) 0.dp else 14.dp)) {
            paragraph.heading?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (paragraph.lead.isNotEmpty()) BookText(paragraph.lead)
            paragraph.items.forEach { item ->
                Row(Modifier.padding(top = 4.dp, start = 4.dp)) {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    BookText(item)
                }
            }
        }
    }
}

/** The book's words at reading size, with the numbers a player acts on picked out. */
@Composable
private fun BookText(text: String) {
    val accent = MaterialTheme.colorScheme.tertiary
    Text(highlighted(text, accent), style = MaterialTheme.typography.bodyMedium)
}

private fun highlighted(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (range in keyTerms(text)) {
        append(text.substring(at, range.first))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(text.substring(range)) }
        at = range.last + 1
    }
    append(text.substring(at))
}

/** "+2", "-1", "+0.25" — the book's own notation, without a trailing ".0". */
internal fun formatDelta(delta: Double): String {
    val sign = if (delta < 0) "" else "+"
    return if (delta == delta.toInt().toDouble()) "$sign${delta.toInt()}" else "$sign$delta"
}
