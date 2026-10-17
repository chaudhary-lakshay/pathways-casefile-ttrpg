package com.pathways.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.GlossaryEntry
import kotlinx.coroutines.launch

/** One run of terms sharing an initial, as the book prints them. */
internal data class GlossaryGroup(val letter: String, val entries: List<GlossaryEntry>)

/**
 * The terms matching [query], cut into runs that share an initial, in the
 * book's order. Never sorted: the book prints `3d6` after `Sea of
 * Collective Subconscious`, so a letter met again later is a new group.
 */
internal fun glossaryGroups(entries: List<GlossaryEntry>, query: String): List<GlossaryGroup> {
    val shown = entries.filter { it.term.contains(query.trim(), ignoreCase = true) }
    val groups = mutableListOf<GlossaryGroup>()
    for (entry in shown) {
        val letter = entry.term.first().uppercase()
        val last = groups.lastOrNull()
        if (last?.letter == letter) groups[groups.lastIndex] = last.copy(entries = last.entries + entry)
        else groups += GlossaryGroup(letter, listOf(entry))
    }
    return groups
}

/**
 * The book's Glossary of Terms (p. 167-168), as Case File frame 07 draws
 * it: a page of its own, terms under their initials, a row of letters that
 * jumps to each, and a search.
 *
 * It hangs off the character gallery rather than any of the three character
 * screens because a glossary is not about a character: gating "what is CoR"
 * behind picking somebody would answer a question nobody asked.
 *
 * The order is the book's, which is not alphabetical: `3d6` prints between
 * `Sea of Collective Subconscious` and `Will`. The book's order already runs
 * by initial, so the letter groups ask for no sort ([glossaryGroups]), and
 * the screen agrees with the page a player is holding.
 *
 * What the frame draws and the book does not print is left out: a part of
 * speech beside a term ("n.", "place", "abbr.") and a "cf." link. The one
 * cross-reference the book prints, Loen Kingdom's "See Backlund.", is in its
 * definition. The search and the jump row are the owner's (2026-10-18); 6gn's
 * rule holds, so every definition is closed until tapped.
 */
@Composable
fun GlossaryScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val entries = remember { ContentLoader.glossary() }
    var query by rememberSaveable { mutableStateOf("") }
    var openTerm by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = glossaryGroups(entries, query)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // One list item per group, so the first visible item is the group in view.
    val inView by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BoxedIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Text(
                "PP. 167–168 · ${entries.size} ENTRIES",
                Modifier.weight(1f).clearAndSetSemantics {},
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.1.em),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        Text(
            "Glossary",
            Modifier.padding(top = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.displaySmall,
        )
        SearchLine(query) { query = it }
        FlowRow(
            Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            groups.forEachIndexed { index, group ->
                LetterBox(group.letter, current = index == inView) {
                    scope.launch { listState.animateScrollToItem(index) }
                }
            }
        }
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (groups.isEmpty()) {
                item { Text("No term matches.", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            itemsIndexed(groups, key = { index, group -> "$index${group.letter}" }) { _, group ->
                GroupBlock(group, openTerm) { term -> openTerm = if (openTerm == term) null else term }
            }
            // Room after the last group, so a jump to S or W lands it at the
            // top and the row lights the letter tapped. Without it the list
            // stopped short and lit H for a tap on S (seen on the SM-T570).
            // Short of a full page, so the last group stays in view.
            item(key = "tail") { Spacer(Modifier.fillParentMaxHeight(0.85f)) }
        }
    }
}

@Composable
private fun SearchLine(query: String, onQuery: (String) -> Unit) {
    val ink = MaterialTheme.colorScheme.outline
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .drawBehind { drawLine(ink, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, Modifier.size(22.dp))
        Box(Modifier.padding(start = 8.dp).weight(1f)) {
            val style = MaterialTheme.typography.bodyLarge.copy(fontFamily = PlexMono)
            if (query.isEmpty()) {
                Text("look up a term", Modifier.clearAndSetSemantics {}, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Look up a term" },
            )
        }
    }
}

/** A letter of the jump row: ink-filled for the group in view. 48 dp to tap. */
@Composable
private fun LetterBox(letter: String, current: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClickLabel = "Jump to $letter", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, if (current) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letter,
                style = MaterialTheme.typography.labelMedium,
                color = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** A big initial beside its terms, ruled off below. */
@Composable
private fun GroupBlock(group: GlossaryGroup, openTerm: String?, onToggle: (String) -> Unit) {
    Column {
        Row(Modifier.padding(vertical = 8.dp)) {
            Text(
                group.letter,
                Modifier.width(44.dp).clearAndSetSemantics {},
                style = MaterialTheme.typography.displaySmall,
                color = if (group.entries.any { it.term == openTerm }) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                for (entry in group.entries) {
                    if (entry.term == openTerm) OpenTerm(entry) { onToggle(entry.term) } else ClosedTerm(entry.term) { onToggle(entry.term) }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A closed term: the name, a dotted leader, and a chevron. */
@Composable
private fun ClosedTerm(term: String, onClick: () -> Unit) {
    val dots = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = "Closed" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(term, style = MaterialTheme.typography.bodyLarge)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .heightIn(min = 2.dp)
                .drawBehind {
                    drawLine(
                        dots,
                        Offset(0f, size.height / 2 + 6.dp.toPx()),
                        Offset(size.width, size.height / 2 + 6.dp.toPx()),
                        1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                    )
                },
        )
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, Modifier.size(20.dp))
    }
}

/** An open term: ruled in stamp red at its left, the term in Fraunces, then the book's definition. */
@Composable
private fun OpenTerm(entry: GlossaryEntry, onClick: () -> Unit) {
    val red = MaterialTheme.colorScheme.secondary
    Column(
        Modifier
            .padding(vertical = 4.dp)
            .drawBehind { drawLine(red, Offset(0f, 0f), Offset(0f, size.height), 2.dp.toPx()) }
            .padding(start = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { stateDescription = "Open" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(entry.term, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge.copy(fontFamily = Fraunces))
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, Modifier.size(20.dp))
        }
        Text(entry.definition, Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
