package com.pathways.app

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pathways.model.Attributes
import com.pathways.model.BeyonderState
import com.pathways.rules.PremadeSheet
import com.pathways.rules.TraitGrants

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
 * the file number, the name, the walk's stamp, the two traits that
 * characterise the person, and the four attributes, a table as Case File
 * frame 01 draws it ([GalleryRow]).
 *
 * The filters exist because 23 near-identical ordinary people is the actual
 * problem this screen has, and "which of these is already supernatural" is the
 * one question the save can answer instantly ([walkOf]).
 *
 * **A row is the character as played** ([playedRow]): the attributes, the
 * skill count and the two trait chips are what a tap opens, bought and
 * digested, and the pathway is the save's, because no sheet prints one.
 * The chips are the first two traits HELD, in sheet order. A wealth or a
 * face picked in the builder replaces the sheet's row and joins the end of
 * the list, so the sheet's own leaves the chips; a level a potion added is
 * printed at the sum (6fx). Both reach one sheet today, Fortune Teller.
 * A bought face a potion beat is held and not played, and is not
 * filtered here: it joins the end of the list, behind every printed row,
 * and each sheet prints at least four traits a chip can show with its wealth
 * and face rows set aside, so it cannot reach the first two.
 *
 * Until 6fw nothing here was digested, because a save from before the
 * floors existed throws where a potion is digested on top of it
 * ([digestedWith]) and this is the launch screen. That reason named a repair
 * only a session could make; the repair writes nothing and is read here
 * ([repairedEdits]).
 */

/** The folder tabs over the table. The blank sheet is not a person; see [isTemplate]. */
internal enum class Roster(val label: String) {
    ALL("All"),
    MORTAL("Mortal"),
    BEYONDER("Beyonders"),
}


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

/** What a row prints of the character: the four attributes, how many skills are held, and two traits. */
internal data class PlayedRow(val attributes: Attributes, val skills: Int, val chips: List<String>)

/**
 * [sheet]'s row as PLAYED: what was bought in the builder, then the potions
 * of the walk digested on top, which is the character a tap opens. Until 6fw
 * the row printed the sheet's own numbers beside the walk, so a Docks Bouncer
 * on Twilight Giant 9 read ST 11 here and rolled damage off ST 14 a tap later.
 *
 * Through [repairedEdits], the repair a session makes on opening: a save
 * from before the floors and ceilings throws where it is digested as saved.
 * Nothing is written from here.
 */
internal fun playedRow(sheet: PremadeSheet, saved: SavedSession?): PlayedRow {
    val walk = saved?.beyonder
    val bought = saved?.repairedEdits(sheet)?.applyTo(sheet.toCharacter()) ?: sheet.toCharacter()
    return PlayedRow(
        bought.attributesWith(walk),
        liveSkillLevels(bought.copy(beyonder = walk)).size,
        // The list the sheet's rolls read ([BeyonderSession.traitsInPlay]).
        chipTraits(TraitGrants.withGrantedTraits(bought.traits, walk, bought.boon).map { it.name }),
    )
}

/**
 * [playedRow], or the sheet as printed for a save that does not digest.
 *
 * [BeyonderSession.drink] digests a potion before it stores one, so a stored
 * walk digested under the catalogue that stored it; a walk this catalogue
 * lacks takes a content change between two builds. On the sheet that throw
 * costs one character. Here it would cost the launch screen and with it
 * every other save, so it costs the row its played numbers and is logged.
 */
internal fun rowFor(sheet: PremadeSheet, saved: SavedSession?): PlayedRow =
    try {
        playedRow(sheet, saved)
    } catch (failure: Exception) {
        Log.w("PremadeGallery", "${sheet.name}'s save does not digest; its row is the sheet's", failure)
        playedRow(sheet, null)
    }

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
 * The trait names worth showing on a row, from [names] in held order.
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
internal fun chipTraits(names: List<String>): List<String> =
    names
        .map {
            it.substringBefore(" - ")
                .substringBefore(" — ")
                .substringBefore(" −")
                .trim()
        }
        .filter { it.length in 1..32 }
        .take(2)

/** A row's line under the name (frame 01): the two traits, then the skill count, "sk." on screen and spelled out aloud. */
internal fun galleryNote(row: PlayedRow, spoken: Boolean): String =
    (row.chips.joinToString(", ").takeIf { it.isNotEmpty() }?.let { listOf(it) }.orEmpty() +
        "${row.skills} ${if (spoken) "skills" else "sk."}").joinToString(" · ")

/** The row's stamp: the walk, or Mortal. Capitals are drawn, not stored. */
internal fun walkStamp(walk: BeyonderState?): String = walk?.let { "${it.pathway} ${it.sequence}" } ?: "Mortal"

/** Frame 01 inks an attribute heavy at 10 and above, the book's average. Display only. */
internal fun isStrong(value: Int): Boolean = value >= 10

/** The gallery list's two fixed items. A lazy list throws on a repeated key. */
internal const val GALLERY_HEAD_KEY = "head"
internal const val GALLERY_TABS_KEY = "tabs"

/** A person's or the blank sheet's list key, prefixed so no name can repeat a fixed item's (Codex, 6ig). */
internal fun rowKey(name: String): String = "row:$name"

/** The file number the next character created takes: own characters join the end ([fileNumber]). */
internal fun nextFileNumber(sheets: List<PremadeSheet>): Int = sheets.count { !isTemplate(it) } + 1

/**
 * The gallery as Case File frame 01 draws it: the index head, folder tabs
 * over a ruled table, and Open new file pinned at the foot. The glossary
 * takes the whole screen when open, in the activity window, so the paper
 * and the column hold; the list's place is kept under it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremadeGallery(
    sheets: List<PremadeSheet>,
    saved: Map<String, SavedSession>,
    /** What a new character may be copied from, the blank sheet first. */
    bases: List<PremadeSheet>,
    onCreate: (name: String, base: PremadeSheet) -> Unit,
    onSelect: (PremadeSheet) -> Unit,
) {
    var roster by remember { mutableStateOf(Roster.ALL) }
    var creating by remember { mutableStateOf(false) }
    var glossaryOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val people = remember(sheets) { sheets.filterNot(::isTemplate) }
    val templates = remember(sheets) { sheets.filter(::isTemplate) }
    // Not remembered: the save is a plain map, not snapshot state, and 22
    // lookups cost nothing. It changes only while a sheet is selected, when
    // this screen is not composed, so each return reads it fresh.
    val shown = rosterOf(roster, people, saved)

    // The glossary's only entry point, and it is here for its own reason:
    // the other three screens all require a selected character, and "what
    // is CoR" is not a question about one. See [GlossaryScreen].
    if (glossaryOpen) {
        GlossaryScreen(onBack = { glossaryOpen = false })
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { OpenFileButton(onClick = { creating = true }) },
    ) { insets ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(insets).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        ) {
            item(key = GALLERY_HEAD_KEY) { GalleryHead(onGlossary = { glossaryOpen = true }) }
            // The filter stays in reach over a long list.
            stickyHeader(key = GALLERY_TABS_KEY) {
                Column {
                    RosterTabs(roster, Roster.entries.associateWith { rosterOf(it, people, saved).size }) { roster = it }
                    GalleryTableHead()
                }
            }
            items(shown, key = { rowKey(it.name) }) { sheet ->
                // ponytail: two digestions a row, on the main thread, each
                // time the row scrolls into view (a lazy item's `remember`
                // goes with it). 22 rows of a few potions each; hoist one
                // map above the list when a profiler says so.
                val entry = saved[sheet.name]
                val numbers = remember(sheet, entry) { rowFor(sheet, entry) }
                GalleryRow(
                    file = fileNumber(sheet, sheets),
                    name = sheet.name,
                    walk = walkOf(sheet, saved),
                    lost = entry?.isLost == true,
                    numbers = numbers,
                ) { onSelect(sheet) }
            }
            // Below the people, not among them.
            items(templates, key = { rowKey(it.name) }) { sheet ->
                BlankSheetButton(onClick = { onSelect(sheet) })
            }
        }
    }

    if (creating) {
        NewCharacterDialog(
            bases = bases,
            taken = takenNames(sheets, saved),
            file = nextFileNumber(sheets),
            onDismiss = { creating = false },
            onCreate = { name, base -> creating = false; onCreate(name, base) },
        )
    }
}
