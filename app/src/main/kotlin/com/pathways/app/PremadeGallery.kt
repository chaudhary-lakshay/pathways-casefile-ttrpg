package com.pathways.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathways.model.Attr
import com.pathways.rules.PremadeSheet

/**
 * The character gallery: the app's launch screen.
 *
 * It was a single-column list of name-plus-skill-count rows, which made 23
 * characters look like 23 rows of the same thing. A grid of tiles is the
 * shape a reader expects from a library, and it lets each tile carry what
 * actually distinguishes one premade from another.
 *
 * **No cover art.** No sheet has a portrait field, and the downloaded asset
 * packs are overworld sprites (walk cycles, tilesets) whose licence terms
 * would need reading before anything shipped. So the "cover" is generated:
 * initials over a gradient chosen deterministically from the name, which
 * gives every character a stable, recognisable tile without inventing a fact
 * about them.
 *
 * ponytail: gradient picked by name hash from a fixed list. Swap in real art
 * the day someone draws 23 portraits; nothing else here changes.
 */

/**
 * The tile "covers". Drawn from Theme.kt's own palette family rather than
 * being fresh colours, so a gallery of 23 tiles still reads as one app.
 * Each pair is (top, bottom) of a vertical gradient.
 */
private val COVER_GRADIENTS = listOf(
    Color(0xFF59288A) to Color(0xFF2E0931), // arcane
    Color(0xFF7B5122) to Color(0xFF3D1F0C), // brass
    Color(0xFF186B47) to Color(0xFF0A2B1C), // verdigris
    Color(0xFF135E71) to Color(0xFF09394F), // teal
    Color(0xFF65142B) to Color(0xFF3A0C1B), // blood
    Color(0xFF3C3B66) to Color(0xFF190F29), // indigo
    Color(0xFF7D3564) to Color(0xFF361031), // plum
    Color(0xFF5C7759) to Color(0xFF253A2D), // moss
)

/**
 * Stable per-name gradient. `hashCode` is specified for String, so a
 * character keeps the same cover between runs and across devices.
 */
private fun gradientFor(name: String): Pair<Color, Color> =
    COVER_GRADIENTS[Math.floorMod(name.hashCode(), COVER_GRADIENTS.size)]

/**
 * One or two letters for the cover.
 *
 * The initial of each of the first two words, so "Barmaid Brawler" gives BB
 * and "Apothecary" gives A. Each word is searched for its first LETTER, so a
 * name beginning with a digit or a bracket still renders letters.
 */
private fun initialsOf(name: String): String =
    name.split(' ', '-')
        .mapNotNull { word -> word.firstOrNull { it.isLetter() } }
        .take(2)
        .joinToString("")
        .uppercase()
        .ifEmpty { "?" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremadeGallery(sheets: List<PremadeSheet>, onSelect: (PremadeSheet) -> Unit) {
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
        LazyVerticalGrid(
            // Adaptive, not a fixed 2: a tablet in landscape should fill its
            // width with tiles rather than stretch two of them.
            columns = GridCells.Adaptive(minSize = 156.dp),
            modifier = Modifier.padding(insets).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(sheets, key = { it.name }) { sheet ->
                PremadeTile(sheet) { onSelect(sheet) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremadeTile(sheet: PremadeSheet, onClick: () -> Unit) {
    // Four of the 23 sheets print SPI 9; the rest print 0. That is the one
    // fact on a premade that changes what the app can DO with it, so it is
    // the one thing badged.
    val isBeyonder = (sheet.published.spi ?: 0) > 0
    val (top, bottom) = gradientFor(sheet.name)

    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                // Book-cover proportions, so a phone shows whole tiles rather
                // than a row of letterboxes.
                .aspectRatio(3f / 4f)
                .background(Brush.verticalGradient(listOf(top, bottom))),
        ) {
            Text(
                initialsOf(sheet.name),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.align(Alignment.Center),
            )
            if (isBeyonder) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                ) {
                    Text(
                        "Beyonder",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                sheet.name,
                style = MaterialTheme.typography.titleSmall,
                // Two lines: the longest fixture name is "LOTM Blank
                // Character Sheet", which does not fit one at this width.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // The four attributes, which are what actually tell two premades
            // apart at a glance -- a bouncer and a librarian differ here long
            // before they differ in skill COUNT.
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for (attr in listOf(Attr.ST, Attr.DX, Attr.IQ, Attr.HT)) {
                    Text(
                        "${attr.name} ${sheet.attributes[attr] ?: 10}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
