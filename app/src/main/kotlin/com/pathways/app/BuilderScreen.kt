package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.BASE_ATTRIBUTE
import com.pathways.model.Character
import com.pathways.model.MAX_ATTRIBUTE
import com.pathways.rules.AttributeCosts
import com.pathways.rules.Derive
import com.pathways.rules.PremadeSheet

/**
 * Spend points and watch the budget answer back (M3).
 *
 * ponytail: attributes only. They are the whole of the cost curve worth
 * seeing live -- the ladder doubles every level, so ST 9 -> 12 is the
 * difference between affordable and impossible -- while skills and traits are
 * list editing, which is a bigger screen for a smaller lesson. Add them when
 * a character is worth saving.
 *
 * The budget is [com.pathways.rules.PointBudget]'s, unchanged: 70 + capped
 * disadvantages + capped quirks, and surplus is normal. It validates
 * `spent <= budget`, never `spent == budget` -- Hunter-Tracker leaves 26
 * unspent and is not thereby broken.
 */
@Composable
fun BuilderScreen(sheet: PremadeSheet, onBack: () -> Unit) {
    val start = remember(sheet) { sheet.toCharacter() }
    var character by remember(sheet) { mutableStateOf(start) }
    val report = remember(character) { Derive.of(character).budget }

    Scaffold { insets ->
        LazyColumn(Modifier.padding(insets).fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    TextButton(onClick = onBack) { Text("Back to sheet") }
                    Text(sheet.name, style = MaterialTheme.typography.headlineSmall)
                }
                BudgetBar(report.spent, report.budget)
                report.violations.forEach { violation ->
                    Text(
                        violation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(top = 12.dp))
            }

            items(Attr.entries) { attr ->
                AttributeRow(
                    attr = attr,
                    level = character.attributes[attr],
                    startingLevel = start.attributes[attr],
                    onChange = { delta -> character = character.withAttribute(attr, delta) },
                )
                HorizontalDivider()
            }
        }
    }
}

/**
 * A level and what the NEXT one costs.
 *
 * The next level's price is the number that governs the decision, and the
 * ladder doubles every step, so a running total alone hides the cliff.
 */
@Composable
private fun AttributeRow(attr: Attr, level: Int, startingLevel: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(attr.name, style = MaterialTheme.typography.titleMedium)
            // costOf is CUMULATIVE -- what standing at that level costs in
            // total. The number that governs the next click is the
            // difference, and on a doubling ladder the two diverge fast: at
            // IQ 11 the cumulative figure for 12 is 140, but the click costs
            // 80.
            val spent = AttributeCosts.costOf(attr, level)
            val next = if (level < MAX_ATTRIBUTE) AttributeCosts.costOf(attr, level + 1) - spent else null
            Text(
                buildString {
                    append("$spent pts spent here")
                    next?.let { append(" — next level costs $it") }
                    if (level != startingLevel) append(" — sheet prints $startingLevel")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = { onChange(-1) }, enabled = level > BASE_ATTRIBUTE) { Text("−") }
        Text(
            "$level",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        TextButton(onClick = { onChange(1) }, enabled = level < MAX_ATTRIBUTE) { Text("+") }
    }
}

@Composable
private fun BudgetBar(spent: Int, budget: Int) {
    val over = spent > budget
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            if (over) "$spent of $budget points — over budget" else "$spent of $budget points, ${budget - spent} spare",
            style = MaterialTheme.typography.titleSmall,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        LinearProgressIndicator(
            progress = { if (budget == 0) 0f else (spent.toFloat() / budget).coerceIn(0f, 1f) },
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** [Attributes] has no per-[Attr] setter; the builder needs one. */
private fun Character.withAttribute(attr: Attr, delta: Int): Character {
    val level = (attributes[attr] + delta).coerceIn(BASE_ATTRIBUTE, MAX_ATTRIBUTE)
    val raised = when (attr) {
        Attr.ST -> attributes.copy(st = level)
        Attr.DX -> attributes.copy(dx = level)
        Attr.IQ -> attributes.copy(iq = level)
        Attr.HT -> attributes.copy(ht = level)
    }
    return copy(attributes = raised)
}
