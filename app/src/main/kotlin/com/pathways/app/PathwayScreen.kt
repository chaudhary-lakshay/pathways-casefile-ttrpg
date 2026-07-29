package com.pathways.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pathways.rules.DigestedCharacter
import com.pathways.rules.PotionDigestion
import com.pathways.rules.PremadeSheet
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SequenceBlock

/** The Sequences a standard pathway covers, weakest first (Ch. 9-12). */
private val SEQUENCES = listOf(9, 8, 7, 6)

/**
 * Pick a pathway and Sequence, and see what drinking that potion does to this
 * character (M3).
 *
 * The screen shows grants, never a rewritten sheet: the potion's gains are
 * free, so the point budget shown with them is the character's own,
 * unchanged. That is the rule made visible -- if that line ever moves, the
 * engine has started billing gifts.
 */
@Composable
fun PathwayScreen(sheet: PremadeSheet, onBack: () -> Unit) {
    val blocks = remember { ContentLoader.pathways() }
    val pathways = remember(blocks) { blocks.map { it.pathway }.distinct().sorted() }
    var pathway by remember { mutableStateOf<String?>(null) }
    var sequence by remember { mutableIntStateOf(9) }

    val block = pathway?.let { chosen ->
        blocks.single { it.pathway == chosen && it.sequence == sequence }
    }
    val digested = remember(sheet, block) {
        block?.let { PotionDigestion.digest(sheet.toCharacter(), it) }
    }

    Scaffold { insets ->
        LazyColumn(Modifier.padding(insets).fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    TextButton(onClick = onBack) { Text("Back to sheet") }
                    Text(sheet.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        pathway?.let { "$it Pathway" } ?: "choose a pathway",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                HorizontalDivider()
            }

            if (block == null || digested == null) {
                items(pathways) { name ->
                    Text(
                        "$name Pathway",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pathway = name }
                            .padding(16.dp),
                    )
                    HorizontalDivider()
                }
            } else {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SEQUENCES.forEach { n ->
                            FilterChip(
                                selected = n == sequence,
                                onClick = { sequence = n },
                                label = { Text("Seq $n") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    TextButton(
                        onClick = { pathway = null },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("Choose another pathway") }
                    HorizontalDivider()
                }
                digestion(block, digested)
            }
        }
    }
}

private fun LazyListScope.digestion(block: SequenceBlock, digested: DigestedCharacter) {
    item {
        Column(Modifier.padding(16.dp)) {
            Text("Sequence ${block.sequence}: ${block.name}", style = MaterialTheme.typography.titleLarge)
            Text(
                "point budget unchanged: ${digested.budget.spent} of ${digested.budget.budget} spent " +
                    "— the potion's gains are free",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HorizontalDivider()

        SectionHeader("Stats")
        if (block.stats.isEmpty()) {
            GrantRow("no stat gains", "", "")
        } else {
            block.stats.forEach { gain -> GrantRow(gain.attr, formatDelta(gain.delta), gain.note) }
        }
        if (digested.grants.spi != 0) {
            GrantRow("SPI after digestion", digested.effectiveSpi.toString(), "")
        }

        if (block.advantages.isNotEmpty()) {
            SectionHeader("Advantages")
            Prose(block.advantages)
        }
        if (block.drawbacks.isNotEmpty()) {
            SectionHeader("Drawbacks")
            Prose(block.drawbacks)
        }
    }

    if (block.skills.isNotEmpty()) {
        item { SectionHeader("Skills") }
        items(block.skills) { grant ->
            // The engine resolved each grant to a level; fall back to the
            // book's raw "+N" only if a name somehow fails to match, rather
            // than showing a level that was never computed.
            val level = digested.grants.skillLevels.entries
                .firstOrNull { it.key.name == grant.name.substringBefore(" (") }
                ?.value
            GrantRow(grant.name, level?.toString() ?: "+${grant.bonus}", grant.note)
        }
    }

    item {
        SectionHeader("Ability")
        Prose(block.ability)
        if (block.reinforcedAbilities.isNotEmpty()) {
            SectionHeader("Reinforced abilities")
            Prose(block.reinforcedAbilities)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun GrantRow(label: String, value: String, note: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (note.isNotEmpty()) {
                Text(note, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

@Composable
private fun Prose(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
}

/** "+2", "-1", "+0.25" — the book's own notation, without a trailing ".0". */
private fun formatDelta(delta: Double): String {
    val sign = if (delta < 0) "" else "+"
    return if (delta == delta.toInt().toDouble()) "$sign${delta.toInt()}" else "$sign$delta"
}
