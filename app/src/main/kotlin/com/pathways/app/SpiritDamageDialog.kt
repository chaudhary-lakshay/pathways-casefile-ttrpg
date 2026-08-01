package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.Pool
import com.pathways.rules.content.ContentLoader

/**
 * Damage dealt to the spirit body (Ch. 5, p. 40-41).
 *
 * The trackers already let a GM move any pool by hand, so what this adds is
 * the CHOICE the book puts on the target: "the target chooses which of three
 * pools it reduces: HP, FP, or SPI". Applying it here rather than through the
 * steppers is what makes "One pool per instance -- you cannot split a single
 * hit across multiple pools" a rule the app keeps instead of a line it prints:
 * one amount, one pool, and the dialog closes.
 *
 * [hasSpirituality] is false for a mortal, who is offered HP and FP only --
 * "Non-Beyonders (SPI 0) ... have no spirituality to sacrifice". The sheet
 * hides the SPI tracker on the same test, so the two cannot disagree.
 *
 * SPI taken below zero is Soul Debt and buys CoR, which [BeyonderSession.spend]
 * charges. Nothing here needs to know that, and deliberately does not: a
 * second place that knows the rate is a second place to get it wrong.
 */
@Composable
fun SpiritDamageDialog(
    hasSpirituality: Boolean,
    onApply: (Pool, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val rules = remember { ContentLoader.spiritDamage() }
    val pools = remember(hasSpirituality) {
        if (hasSpirituality) Pool.entries else Pool.entries.filter { it != Pool.SPI }
    }
    var amount by remember { mutableIntStateOf(1) }
    var pool by remember { mutableStateOf(Pool.HP) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(pool, amount)
                    onDismiss()
                },
            ) { Text("Apply $amount to $pool") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Spirit body damage") },
        text = {
            // Scrolls: fifteen printed rules do not fit a phone, and an
            // AlertDialog does not scroll its own text slot.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text("Damage $amount", Modifier.weight(1f))
                    TextButton(onClick = { amount = (amount - 5).coerceAtLeast(1) }) { Text("−5") }
                    TextButton(onClick = { amount = (amount - 1).coerceAtLeast(1) }) { Text("−") }
                    TextButton(onClick = { amount += 1 }) { Text("+") }
                    TextButton(onClick = { amount += 5 }) { Text("+5") }
                }
                Row {
                    pools.forEach { option ->
                        FilterChip(
                            selected = pool == option,
                            onClick = { pool = option },
                            label = { Text(option.name) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                if (!hasSpirituality) {
                    Text(
                        "No SPI: a non-Beyonder has no spirituality to sacrifice.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                var section = ""
                rules.forEach {
                    if (it.section != section) {
                        section = it.section
                        Text(
                            section,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Text(it.rule, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
}
