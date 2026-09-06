package com.pathways.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.content.ContentLoader

/**
 * The book's Glossary of Terms (p. 167-168), as a reference sheet.
 *
 * It hangs off the character gallery rather than any of the three character
 * screens because a glossary is not about a character: `SHEET`, `PATHWAY` and
 * `BUILDER` all require one to be selected, and gating "what is CoR" behind
 * picking somebody would answer a question nobody asked. That is the warrant
 * -- NOT that the theme picker's button happens to sit in the same top bar.
 * The two controls have opposite semantics (that one mutates a preference the
 * whole app reads, this one only displays), and this file's placement rests on
 * the gallery being the one screen that needs no character.
 *
 * The order is the book's, which is not alphabetical: `3d6` prints between
 * `Sea of Collective Subconscious` and `Will`. Sorting it here would make the
 * screen disagree with the page a player is holding.
 *
 * ponytail: no search box. 25 entries is two flicks; add one if the reference
 * surface ever grows to the appendices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlossarySheet(onDismiss: () -> Unit) {
    val entries = remember { ContentLoader.glossary() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Title only. The book's closing note -- "This glossary covers only
            // the terms unique to this book. For full GURPS terminology, refer
            // to the GURPS Basic Set: Characters (4th Edition)." -- is book
            // text the extractor deliberately drops, and typing it in here
            // would be a screen restating content nothing joins it to.
            item {
                Text(
                    "Glossary",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            items(entries, key = { it.term }) { entry ->
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        entry.term,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(entry.definition, style = MaterialTheme.typography.bodyMedium)
                }
            }

            item { HorizontalDivider(Modifier.padding(top = 6.dp)) }
        }
    }
}
