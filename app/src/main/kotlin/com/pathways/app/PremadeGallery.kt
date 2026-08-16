package com.pathways.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathways.model.Attr
import com.pathways.rules.PremadeSheet

/**
 * The character gallery: the app's launch screen.
 *
 * **This is the second attempt.** The first was a grid of tiles with a 3:4
 * gradient "cover" and the character's initials over it. It looked designed and
 * it was wrong: roughly 60% of every tile rendered two letters that carry no
 * information, the gradients were decorative fills (two of the eight literally
 * purple, the named generated-AI tell), and the traits that actually separate
 * one premade from another were not shown at all. Two and a half characters fit
 * on a screen.
 *
 * This app is a reference tool used mid-game, so the metric is information per
 * screenful, not atmosphere (docs/DESIGN-DIRECTION.md). Hence full-width rows:
 * a small monogram, the name, the two traits that characterise the person, and
 * all five stats. Four to five characters per screen, and the row carries more
 * than the tile did.
 *
 * The filters exist because 23 near-identical ordinary people is the actual
 * problem this screen has, and "which of these is already supernatural" is the
 * one question the data can answer instantly.
 */

/** The filter across the top. `TEMPLATE` is not a person; see [isTemplate]. */
private enum class Roster(val label: String) {
    ALL("All"),
    MORTAL("Mortal"),
    BEYONDER("Beyonders"),
}

/**
 * The blank sheet is a starting point, not a character: it has no attributes
 * worth scanning and no traits, and offering it among the people is what made
 * the first gallery show a row with "0 skills" as though someone had rolled it.
 * Matched on the fixture's own name.
 */
private fun isTemplate(sheet: PremadeSheet): Boolean =
    sheet.name.contains("Blank", ignoreCase = true)

private fun isBeyonder(sheet: PremadeSheet): Boolean = (sheet.published.spi ?: 0) > 0

/**
 * One or two letters for the monogram. The initial of each of the first two
 * words, so "Barmaid Brawler" gives BB and "Apothecary" gives A. Each word is
 * searched for its first LETTER, so a name starting with a digit or bracket
 * still renders letters.
 */
private fun initialsOf(name: String): String =
    name.split(' ', '-')
        .mapNotNull { word -> word.firstOrNull { it.isLetter() } }
        .take(2)
        .joinToString("")
        .uppercase()
        .ifEmpty { "?" }

/**
 * The trait names worth showing on a row.
 *
 * Seven fixtures store the trait's name and the book's whole effect cell as one
 * string (6br), which would render a paragraph inside a chip. Those are cut at
 * the first separator, and anything still long is dropped rather than
 * ellipsised -- a chip reading "Sanctity - Your connection to the div..."
 * informs nobody.
 */
private fun chipTraits(sheet: PremadeSheet): List<String> =
    (sheet.advantages + sheet.disadvantages)
        .map { it.name.substringBefore(" - ").substringBefore(" — ").trim() }
        .filter { it.length in 1..18 }
        .take(2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremadeGallery(sheets: List<PremadeSheet>, onSelect: (PremadeSheet) -> Unit) {
    var roster by remember { mutableStateOf(Roster.ALL) }
    val people = remember(sheets) { sheets.filterNot(::isTemplate) }
    val templates = remember(sheets) { sheets.filter(::isTemplate) }
    val shown = remember(people, roster) {
        when (roster) {
            Roster.ALL -> people
            Roster.MORTAL -> people.filterNot(::isBeyonder)
            Roster.BEYONDER -> people.filter(::isBeyonder)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pathways", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.padding(insets).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (option in Roster.entries) {
                        FilterChip(
                            selected = roster == option,
                            onClick = { roster = option },
                            label = { Text(option.label) },
                        )
                    }
                }
            }

            items(shown, key = { it.name }) { sheet ->
                PremadeRow(sheet) { onSelect(sheet) }
            }

            // Below the people, not among them.
            items(templates, key = { it.name }) { sheet ->
                OutlinedButton(
                    onClick = { onSelect(sheet) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                    Text("  Start from a blank sheet")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremadeRow(sheet: PremadeSheet, onClick: () -> Unit) {
    val beyonder = isBeyonder(sheet)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Small, and tinted by what the character IS. Arcane for a
            // Beyonder, neutral for everyone else: the one thing colour is
            // spent on here is the one distinction that changes what the app
            // can do with the character.
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (beyonder) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (beyonder) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(initialsOf(sheet.name), style = MaterialTheme.typography.titleSmall)
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        sheet.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (beyonder) {
                        Text(
                            "  BEYONDER",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                // The five numbers, in one monospaced-feeling row. SPI is
                // included because it is the stat the whole Beyonder half of
                // the app turns on, and the first gallery left it out.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (attr in listOf(Attr.ST, Attr.DX, Attr.IQ, Attr.HT)) {
                        StatCell(attr.name, "${sheet.attributes[attr] ?: 10}")
                    }
                    StatCell("SPI", "${sheet.published.spi ?: 0}", accent = beyonder)
                }

                val traits = chipTraits(sheet)
                if (traits.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (trait in traits) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Text(
                                    trait,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            "${sheet.skills.size} skills",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp, top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, accent: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            " $value",
            style = MaterialTheme.typography.labelLarge,
            color = if (accent) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
