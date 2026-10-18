package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.pathways.rules.StudyMethod

// The builder as frames 23-24 draw it: a typed form bar, the title and the
// sheet's name, the budget in an ink box of pips, and § heads over a rule.

internal const val FORM_LEDGER = "FORM 2-P · POINT LEDGER"

/** How many pips the budget box draws, whatever the budget (frame 23). */
internal const val BUDGET_PIPS = 30

/**
 * The pips filled for [spent] of [budget]. A pip fills only when all its
 * points are spent, so the last one waits for the budget to be met; over
 * budget fills them all and the box turns red. Nothing spent, or less than
 * nothing (a sheet whose drawbacks pay back more than it buys), fills none.
 */
internal fun budgetPips(spent: Int, budget: Int): Int = when {
    spent <= 0 -> 0
    spent >= budget -> BUDGET_PIPS
    else -> Math.floorDiv(spent * BUDGET_PIPS, budget)
}

/**
 * A secondary row's name as frame 23 prints it, on the owner's ruling
 * (2026-10-18). The book's name is what a screen reader hears. A row the
 * frame does not draw keeps the book's name.
 */
internal fun secondaryShortName(key: String, characteristic: String): String = when (key) {
    "hp" -> "HP"
    "per" -> "Per"
    "fp" -> "FP"
    "basicSpeed" -> "Speed"
    "basicMove" -> "Move"
    else -> characteristic
}

/**
 * Whether a skill row's study stepper drops under the tag rather than
 * sitting beside it. By the window and text size alone, never the row's
 * text, so no row reflows as it is read: at 390 dp and font 1.3 the
 * stepper took half the row and wrapped "Public Speaking" a word a line.
 *
 * ponytail: one threshold in text-scaled dp, between the frame's own 390
 * at 1.0 and the 300 that wrapped; measure the row if it ever varies.
 */
internal fun ledgerStacks(screenWidthDp: Int, fontScale: Float): Boolean =
    screenWidthDp / fontScale < STUDY_BESIDE_MIN

private const val STUDY_BESIDE_MIN = 360f

/** Back, boxed, and the form's code (frames 23-24): what stays put while the ledger scrolls. */
@Composable
internal fun LedgerBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxedIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back to sheet", onBack)
        Caps(
            FORM_LEDGER,
            Modifier.weight(1f).padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelMedium.tabular(),
        )
    }
}

/** "Spend points" in Fraunces, the sheet's name in mono under it. */
@Composable
internal fun LedgerTitle(name: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Spend points",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(name, style = MaterialTheme.typography.bodyMedium.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The budget in an ink box (frame 23): the points spent, the budget, what is
 * spare as a boxed tag, and [BUDGET_PIPS] pips. Red over budget. One Canvas
 * for the pips, sized by [pipSize]: a BoxWithConstraints in a sticky header
 * is the trap 6id's pool pips fell into.
 */
@Composable
internal fun BudgetCard(spent: Int, budget: Int) {
    val over = spent > budget
    val accent = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(2.dp, if (over) accent else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Where the ledger stacks, the spare tag takes a line of its own: beside
        // it at 390 dp and font 1.3, "of 100 points" wrapped. Chosen by the
        // window. The tag holds one line, so "9 over" to "10 over" on a very
        // narrow window cannot grow the pinned card (the review sub-agent);
        // beside it, the budget never passes 115 (70 + 40 + 5, PointBudget),
        // so the label's three digits fit at 360 dp.
        val stacks = ledgerStacks(LocalConfiguration.current.screenWidthDp, LocalDensity.current.fontScale)
        val spare: @Composable (Modifier) -> Unit = { modifier ->
            Text(
                if (over) "${spent - budget} over budget" else "${budget - spent} spare",
                style = MaterialTheme.typography.labelLarge.tabular().copy(fontWeight = FontWeight.Bold),
                color = accent,
                maxLines = 1,
                modifier = modifier
                    .border(1.5.dp, accent, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp).semantics(mergeDescendants = true) {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$spent",
                    style = MaterialTheme.typography.headlineLarge.tabular().copy(fontWeight = FontWeight.Bold),
                    color = accent,
                )
                Text(
                    " of $budget points",
                    style = MaterialTheme.typography.bodyLarge.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!stacks) spare(Modifier.padding(start = 8.dp))
            }
            if (stacks) spare(Modifier.padding(top = 6.dp))
            BudgetPips(budgetPips(spent, budget), accent)
        }
    }
}

@Composable
private fun BudgetPips(filled: Int, color: androidx.compose.ui.graphics.Color) {
    val rule = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().padding(top = 12.dp).height(PIP_LARGEST)) {
        val gap = PIP_GAP_BUDGET.toPx()
        val pip = pipSize(BUDGET_PIPS, size.width.toDp(), PIP_LARGEST, PIP_GAP_BUDGET)?.toPx()
            ?: ((size.width - gap * (BUDGET_PIPS - 1)) / BUDGET_PIPS)
        val stroke = 1.dp.toPx()
        val top = (size.height - pip) / 2
        repeat(BUDGET_PIPS) { i ->
            val at = Offset(i * (pip + gap), top)
            if (i < filled) drawRect(color, at, Size(pip, pip))
            else drawRect(rule, at + Offset(stroke / 2, stroke / 2), Size(pip - stroke, pip - stroke), style = Stroke(stroke))
        }
    }
}

private val PIP_LARGEST = 16.dp
private val PIP_GAP_BUDGET = 3.dp

/**
 * "STUDY BY" over two joined cells in an ink box, the picked one filled
 * (frame 24). Each cell takes half, so a pick changes no width.
 */
@Composable
internal fun StudyToggle(method: StudyMethod, onPick: (StudyMethod) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Caps("Study by", Modifier.padding(bottom = 6.dp))
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(Modifier.height(IntrinsicSize.Min).selectableGroup()) {
                StudyMethod.entries.forEachIndexed { i, entry ->
                    if (i > 0) VerticalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
                    val picked = entry == method
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .then(if (picked) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                            .selectable(selected = picked, role = Role.RadioButton, onClick = { onPick(entry) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Caps(
                            entry.label,
                            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge.tabular().copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                            color = if (picked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** A § head with no action, over its rule: Attributes and Secondary characteristics. */
@Composable
internal fun LedgerHead(heading: String) {
    LedgerHeadRow(heading, action = null)
}

/**
 * "§ NAME" at the left, its one bracketed action at the right, a 1.5 dp ink
 * rule under both (frames 23-24). The section's content goes under the rule.
 */
@Composable
internal fun LedgerHeadRow(heading: String, action: (@Composable () -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 20.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Caps(
                "§ $heading",
                Modifier.weight(1f).semantics { heading() },
                spoken = heading,
                style = MaterialTheme.typography.labelLarge.tabular().copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            action?.invoke()
        }
        HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)
    }
}
