package com.pathways.app

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathways.model.Attr
import com.pathways.model.BeyonderState
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
 * the four attributes. Four to five characters per screen, and the row carries more
 * than the tile did.
 *
 * The filters exist because 23 near-identical ordinary people is the actual
 * problem this screen has, and "which of these is already supernatural" is the
 * one question the save can answer instantly ([walkOf]).
 *
 * **A row is the published sheet plus the walk.** Every number on it is the
 * sheet's, as printed; the pathway is the save's, because no sheet prints one.
 * Nothing here is digested: a save from before the floors existed throws
 * where a potion is digested on top of it ([digestedWith]), and this is the
 * launch screen.
 */

/** The filter across the top. `TEMPLATE` is not a person; see [isTemplate]. */
internal enum class Roster(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Filled.Groups),
    MORTAL("Mortal", Icons.Filled.Person),
    // An eye: what a Beyonder has that a mortal does not is Spirit Vision, and
    // the sheet's own Divine action already carries this icon for the same
    // idea.
    BEYONDER("Beyonders", Icons.Filled.Visibility),
}

/**
 * The blank sheet is a starting point, not a character: it has no attributes
 * worth scanning and no traits, and offering it among the people is what made
 * the first gallery show a row with "0 skills" as though someone had rolled it.
 * Matched on the fixture's own name.
 */
private fun isTemplate(sheet: PremadeSheet): Boolean =
    sheet.name.contains("Blank", ignoreCase = true)

/**
 * The pathway [sheet] has been walked onto in play, or null for a mortal.
 * The gallery's one answer to "is this a Beyonder": the tabs, their counts,
 * the row's mark and its pathway cell all ask here.
 *
 * Until 6ft the answer was the sheet's printed SPI, which filed every
 * character walked in the app under Mortal and two who had drunk nothing
 * under Beyonders: four sheets print SPI 9 and none plays it
 * ([PremadeSheet.toCharacter]). SPI is not the question either way -- the
 * Chained potion grants none at Sequence 9. Whether a character has a pool
 * to spend is the sheet screen's, asked of the played maximum.
 */
internal fun walkOf(sheet: PremadeSheet, saved: Map<String, SavedSession>): BeyonderState? =
    saved[sheet.name]?.beyonder

/**
 * Who a tab holds. Its size is printed on the tab: one labelled "Beyonders"
 * is a question; one labelled "Beyonders (4)" is an answer, and it tells the
 * player how much list is behind it before they spend a tap finding out.
 */
internal fun rosterOf(
    roster: Roster,
    people: List<PremadeSheet>,
    saved: Map<String, SavedSession>,
): List<PremadeSheet> = when (roster) {
    Roster.ALL -> people
    Roster.MORTAL -> people.filter { walkOf(it, saved) == null }
    Roster.BEYONDER -> people.filter { walkOf(it, saved) != null }
}

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
 *
 * **Three separators, and one deliberate non-separator.** A hyphen, an em dash
 * and a MINUS SIGN (U+2212) each introduce effect prose: without the third,
 * "Clueless -3 to all social skill rolls; generally miss social cues..." never
 * isolates to "Clueless" and the row loses the trait entirely.
 *
 * A COLON does not separate name from effect, and splitting on it would be a
 * quiet data loss: "Wealth: Comfortable" and "Poverty: Struggling" are whole
 * two-part names, and cutting them to "Wealth" and "Poverty" drops the level
 * that distinguishes one row from the other. "Paranoia: Bodies of water" is the
 * same shape -- a specification, not a description.
 *
 * The length cap is 32, not 18. At 18 this silently dropped "High Pain
 * Threshold", "Lightning Calculator", "Legal Enforcement Powers", "Sense of
 * Duty (Coworkers)" and "Pacifism (Cannot Harm Innocents)" -- precisely the
 * characterful names the chip exists to show.
 */
private fun chipTraits(sheet: PremadeSheet): List<String> =
    (sheet.advantages + sheet.disadvantages)
        .map {
            it.name.substringBefore(" - ")
                .substringBefore(" — ")
                .substringBefore(" −")
                .trim()
        }
        .filter { it.length in 1..32 }
        .take(2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremadeGallery(
    sheets: List<PremadeSheet>,
    saved: Map<String, SavedSession>,
    themePrefs: MutableState<ThemePrefs>,
    onSelect: (PremadeSheet) -> Unit,
) {
    var roster by remember { mutableStateOf(Roster.ALL) }
    var themeOpen by remember { mutableStateOf(false) }
    var glossaryOpen by remember { mutableStateOf(false) }
    val people = remember(sheets) { sheets.filterNot(::isTemplate) }
    val templates = remember(sheets) { sheets.filter(::isTemplate) }
    // Not remembered: the save is a plain map, not snapshot state, and 22
    // lookups cost nothing. It changes only while a sheet is selected, when
    // this screen is not composed, so each return reads it fresh.
    val shown = rosterOf(roster, people, saved)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    // "Characters", not "Pathways". A top bar names the SCREEN
                    // you are on, and this one named the app -- which told a
                    // reader nothing they did not already know, and clashed
                    // with the Pathway screen that genuinely is about pathways.
                    title = { Text("Characters", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        Icon(
                            Icons.Filled.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                        )
                    },
                    actions = {
                        // The glossary's only entry point, and it is here for
                        // its own reason rather than the theme picker's: the
                        // other three screens all require a selected
                        // character, and "what is CoR" is not a question about
                        // one. This is the only character-independent screen
                        // the app has. See [GlossarySheet].
                        IconButton(onClick = { glossaryOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Glossary")
                        }
                        // The theme picker's only entry point. It lives here
                        // because this is the one screen every session starts
                        // on; burying it a tap deeper would make the choice
                        // exist without being reachable.
                        IconButton(onClick = { themeOpen = true }) {
                            Icon(Icons.Filled.Palette, contentDescription = "Theme")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        // The launch screen's heading is the one place the
                        // accent is a brand mark rather than "you can do this".
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        },
        bottomBar = {
            // A footer, matching the sheet screen. The counts stay on the
            // labels: a destination reading "Beyonders (4)" says how much list
            // is behind it before a tap finds out.
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                for (option in Roster.entries) {
                    NavigationBarItem(
                        selected = roster == option,
                        onClick = { roster = option },
                        icon = { Icon(option.icon, contentDescription = null) },
                        label = { Text("${option.label} (${rosterOf(option, people, saved).size})") },
                    )
                }
            }
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.padding(insets).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.name }) { sheet ->
                PremadeRow(sheet, walkOf(sheet, saved)) { onSelect(sheet) }
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

    if (glossaryOpen) {
        GlossarySheet(onDismiss = { glossaryOpen = false })
    }

    if (themeOpen) {
        ThemePicker(
            prefs = themePrefs.value,
            onChange = { themePrefs.value = it },
            onDismiss = { themeOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremadeRow(sheet: PremadeSheet, walk: BeyonderState?, onClick: () -> Unit) {
    val beyonder = walk != null
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
            // Small, and marked by what the character IS. The one thing colour
            // is spent on here is the one distinction that changes what the app
            // can do with the character.
            //
            // The mark is a BORDER and the initials, both in bare `secondary`,
            // rather than a filled `secondaryContainer`. Material spends
            // `secondaryContainer` on selected chrome -- every FilterChip and
            // NavigationBarItem in this app, 79 of them -- so a filled swatch
            // here read as "this row is selected" rather than "this person is a
            // Beyonder, and under a rotated preset it sat next to an identical
            // pill in the nav bar. Bare `secondary` is the accent chrome never
            // touches, which is why the other three Beyonder cues (the label,
            // the walk, ActionGroup's tint) already use it.
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (beyonder) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                border = if (beyonder) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                } else {
                    null
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

                // The four numbers the sheet prints, in one monospaced-feeling
                // row, then the walk. Not SPI: a sheet plays 0, and a
                // walked character's needs the potions digested. A
                // FlowRow, so "Wheel of Fortune 9" wraps on a narrow screen.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (attr in listOf(Attr.ST, Attr.DX, Attr.IQ, Attr.HT)) {
                        StatCell(attr.name, "${sheet.attributes[attr] ?: 10}")
                    }
                    if (walk != null) StatCell(walk.pathway, "${walk.sequence}", accent = true)
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
