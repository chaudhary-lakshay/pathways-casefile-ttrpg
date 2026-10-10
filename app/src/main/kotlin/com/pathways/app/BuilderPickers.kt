package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import com.pathways.rules.SkillLadder
import com.pathways.rules.Appearance
import com.pathways.rules.Wealth
import com.pathways.rules.content.AppearanceLevel
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SkillDefinition
import com.pathways.rules.content.WealthLevel

/**
 * The seven printed levels, poorest first.
 *
 * A vertical list rather than a chip row: a horizontally scrolling Row inside
 * an AlertDialog never receives the drag on a phone, which cost three
 * maneuvers their selectability in M4's damage slice and was invisible to
 * every test.
 */
@Composable
internal fun WealthPicker(
    chosen: String?,
    printed: WealthLevel?,
    onPick: (WealthLevel?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Economic status") },
        text = {
            LazyColumn {
                // The way back to the published sheet (Codex). Without it a
                // single tap is permanent, and the 20 of 23 premades that
                // print no wealth level could never be returned to printing
                // none.
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(null) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            "As printed" + if (chosen == null) " ✓" else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            printed?.let { "The sheet prints ${it.status}" }
                                ?: "The sheet prints no wealth level",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider()
                }
                items(Wealth.all()) { level ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(level) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            level.status + if (level.status == chosen) " ✓" else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "${level.pointsLabel()} · ${level.startingMoney} · " +
                                "reaction ${level.reactionModifier.signed()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(level.lifestyle, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        },
    )
}

/**
 * The eight printed levels, best first.
 *
 * A vertical list for the same reason [WealthPicker] is one.
 *
 * ponytail: "As printed" is the only way back, which on all 23 premades is
 * also the way to ordinary looks -- none of them prints an appearance level.
 * If a sheet ever printed one, ordinary would become unreachable for that
 * character; the upgrade is a separate "Ordinary" row storing a sentinel, and
 * it is not worth a saved-state distinction until a sheet needs it.
 */
@Composable
internal fun AppearancePicker(
    chosen: String?,
    printed: AppearanceLevel?,
    onPick: (AppearanceLevel?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Appearance") },
        text = {
            LazyColumn {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(null) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            "As printed" + if (chosen == null) " ✓" else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            printed?.let { "The sheet prints ${it.level}" }
                                ?: "The sheet prints no appearance level — ordinary looks",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider()
                }
                items(Appearance.all()) { level ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(level) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            level.level + if (level.level == chosen) " ✓" else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "${level.points.pointsLabel()} · " +
                                "reaction ${level.reactionModifier.signed()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
    )
}

/**
 * Every skill the book prints, minus the ones already owned.
 *
 * ponytail: no search box. 158 rows scroll fine on a phone, and a text field
 * is a keyboard, an IME and another piece of hoisted state for a list this
 * size. Add one when the list is the complaint.
 */
@Composable
internal fun SkillPicker(
    owned: Set<Pair<String, String?>>,
    onPick: (SkillDefinition) -> Unit,
    onDismiss: () -> Unit,
) {
    val skills = remember(owned) {
        ContentLoader.skills()
            .filterNot { (it.name.lowercase() to it.specialization) in owned }
            // Ch. 9 p. 85: the three SPI-based skills "cannot be raised with
            // character points ... they improve only through Sequence
            // progression and pathway bonuses". Withheld rather than offered
            // and then refused, the lesson the two Aim rows taught (6t).
            .filter { SkillLadder.isBuyableWithPoints(it.attr) }
            .sortedBy { it.name }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Add a skill") },
        text = {
            LazyColumn {
                items(skills) { definition ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(definition) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            definition.specialization?.let { "${definition.name} ($it)" }
                                ?: definition.name,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "${definition.attr.code}/${definition.difficulty.code}" +
                                if (definition.specializable) " - needs a specialization" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
    )
}
