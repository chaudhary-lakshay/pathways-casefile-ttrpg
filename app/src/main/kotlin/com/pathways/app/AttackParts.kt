package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.SkillTarget

/**
 * A counted attack option, Rapid Strike or Deceptive: its count, what each
 * costs, and a step either way. The plan decides what a step may reach.
 */
@Composable
internal fun OptionStepper(label: String, value: Int, note: String, onStep: (Int) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$label $value", style = MaterialTheme.typography.titleSmall)
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedIconButton(onClick = { onStep(-1) }) {
                Icon(Icons.Filled.Remove, contentDescription = "One less $label")
            }
            OutlinedIconButton(onClick = { onStep(1) }) {
                Icon(Icons.Filled.Add, contentDescription = "One more $label")
            }
        }
    }
}

/**
 * The foot of the options: every term the target is made of, the target, and
 * Roll, pinned below the scrolling choices so the number and the button that
 * rolls it are never scrolled apart. On the tablet Roll sat below the fold.
 */
@Composable
internal fun AttackFooter(
    lines: List<String>,
    standing: SkillTarget,
    attacks: Int,
    onRoll: () -> Unit,
) {
    Column {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (standing.forbidden) {
                    Text(FORBIDDEN_ROLL, color = MaterialTheme.colorScheme.error)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${standing.target}",
                            style = MaterialTheme.typography.displaySmall
                                .copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "3d6 ≤ ${standing.target}" + if (attacks > 1) ", each" else "",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 10.dp, bottom = 6.dp),
                        )
                    }
                }
                if (standing.requiresCritical) {
                    Text(
                        "Only a critical success counts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            // Nothing to roll is nothing to pay for: the handler charges the
            // plan's FP whatever the dice do.
            Button(onClick = onRoll, enabled = !standing.forbidden) {
                Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Roll", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
