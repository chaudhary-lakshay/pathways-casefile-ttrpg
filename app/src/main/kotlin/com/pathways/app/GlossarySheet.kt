package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
 * picking somebody would answer a question nobody asked. This file's
 * placement rests on the gallery being the one screen that needs no character.
 *
 * The order is the book's, which is not alphabetical: `3d6` prints between
 * `Sea of Collective Subconscious` and `Will`. Sorting it here would make the
 * screen disagree with the page a player is holding.
 *
 * ponytail: a scrolling `Column`, not a `LazyColumn`, and no search box. 25
 * short rows do not need lazy layout, and a lazy list inside a
 * `ModalBottomSheet` puts the list's scroll and the sheet's drag-to-dismiss on
 * the same gesture -- a question this repo cannot answer without a device,
 * and one a plain scrolling column never asks. Add both if the reference
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
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // Title only. The book's closing note -- "This glossary covers only
            // the terms unique to this book. For full GURPS terminology, refer
            // to the GURPS Basic Set: Characters (4th Edition)." -- is book
            // text the extractor deliberately drops, and typing it in here
            // would be a screen restating content nothing joins it to.
            Text(
                "Glossary",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            // Terms only, each definition closed until tapped: 25 open
            // definitions read as one text wall (the owner's rule, 6gn).
            for (entry in entries) {
                RuleNote(entry.term) {
                    Text(entry.definition, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
