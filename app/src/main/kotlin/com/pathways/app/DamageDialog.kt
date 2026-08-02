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
import com.pathways.model.RollResult
import com.pathways.rules.Pool
import com.pathways.rules.RandomSource
import com.pathways.rules.SuccessRoll
import com.pathways.rules.Wounds
import com.pathways.rules.content.ContentLoader

/**
 * One hit: how much, and which pool absorbs it (Ch. 5, p. 40-42).
 *
 * The trackers already let a GM move any pool by hand, so what this adds is
 * the two things a single hit decides that a stepper cannot.
 *
 * **Which pool.** Spirit body damage lets the target choose: "the target
 * chooses which of three pools it reduces: HP, FP, or SPI". Applying it here
 * is what makes "One pool per instance -- you cannot split a single hit across
 * multiple pools" a rule the app keeps rather than a line it prints: one
 * amount, one pool, and the dialog closes. [hasSpirituality] is false for a
 * mortal, who is offered HP and FP only -- "Non-Beyonders (SPI 0) ... have no
 * spirituality to sacrifice". The sheet hides the SPI tracker on the same
 * test, so the two cannot disagree.
 *
 * **Whether it is a Major Wound.** That threshold is a property of the single
 * hit, which is exactly what the pool steppers destroy by moving one point at
 * a time. The HT roll is offered rather than made: the book says "make an
 * immediate HT roll", and a roll the GM did not ask for is one they cannot
 * decline for a hit they have already resolved at the table.
 *
 * SPI taken below zero is Soul Debt and buys CoR, which [BeyonderSession.spend]
 * charges. Nothing here needs to know that, and deliberately does not: a
 * second place that knows the rate is a second place to get it wrong.
 */
@Composable
fun DamageDialog(
    hasSpirituality: Boolean,
    maxHp: Int,
    ht: Int,
    dice: RandomSource,
    onApply: (Pool, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val rules = remember { ContentLoader.spiritDamage() }
    val pools = remember(hasSpirituality) {
        if (hasSpirituality) Pool.entries else Pool.entries.filter { it != Pool.SPI }
    }
    var amount by remember { mutableIntStateOf(1) }
    var pool by remember { mutableStateOf(Pool.HP) }
    var shock by remember { mutableStateOf<RollResult?>(null) }

    // Only HP has a Major Wound threshold: the rule is written against "half
    // your HP", and FP and SPI print no equivalent.
    val isMajor = pool == Pool.HP && Wounds.isMajorWound(amount, maxHp)

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
        title = { Text("Damage") },
        text = {
            // Scrolls: fifteen printed rules do not fit a phone, and an
            // AlertDialog does not scroll its own text slot.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text("Damage $amount", Modifier.weight(1f))
                    TextButton(
                        onClick = { step(amount, -5)?.let { amount = it; shock = null } },
                    ) { Text("−5") }
                    TextButton(
                        onClick = { step(amount, -1)?.let { amount = it; shock = null } },
                    ) { Text("−") }
                    TextButton(onClick = { amount += 1; shock = null }) { Text("+") }
                    TextButton(onClick = { amount += 5; shock = null }) { Text("+5") }
                }
                Row {
                    pools.forEach { option ->
                        FilterChip(
                            selected = pool == option,
                            // The roll belongs to the hit it was rolled for.
                            // Moving it to another pool makes it a different
                            // hit, so its shock result is no longer an answer.
                            onClick = { pool = option; shock = null },
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
                MajorWoundPanel(
                    isMajor = isMajor,
                    threshold = Wounds.majorWoundThreshold(maxHp),
                    ht = ht,
                    shock = shock,
                    onRoll = { shock = SuccessRoll.against(ht, dice) },
                )
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

/** The threshold, and the HT roll the book asks for once it is crossed. */
@Composable
private fun MajorWoundPanel(
    isMajor: Boolean,
    threshold: Int,
    ht: Int,
    shock: RollResult?,
    onRoll: () -> Unit,
) {
    Text(
        if (isMajor) "Major wound: $threshold or more in one hit" else "Major wound at $threshold+",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    if (!isMajor) return
    if (shock == null) {
        TextButton(onClick = onRoll) { Text("Roll HT $ht") }
        return
    }
    Text("3d6: ${shock.total} vs $ht", style = MaterialTheme.typography.titleMedium)
    val row = Wounds.majorWoundFor(shock)
    Text(
        "${row.result} — ${row.effect}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** One damage is the smallest hit there is; zero is not a hit at all. */
private fun step(amount: Int, delta: Int): Int? = (amount + delta).takeIf { it >= 1 }
