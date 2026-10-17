package com.pathways.app

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathways.rules.SkillTarget
import com.pathways.rules.SuccessRoll

/**
 * A counted attack option, Rapid Strike or Deceptive: its count, what each
 * costs, and a step either way. The plan decides what a step may reach.
 */
@Composable
internal fun OptionStepper(
    label: String,
    value: Int,
    note: String,
    shown: String = "$value",
    onStep: (Int) -> Unit,
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$label $shown", style = MaterialTheme.typography.titleSmall)
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            JoinedStepper(
                null,
                onMinus = { onStep(-1) },
                onPlus = { onStep(1) },
                minusLabel = "One less $label",
                plusLabel = "One more $label",
            )
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
        HorizontalDivider(Modifier.padding(vertical = 8.dp), thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
        lines.forEach {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (standing.forbidden) {
                    Text(FORBIDDEN_ROLL, color = MaterialTheme.colorScheme.error)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Boxed, as the target card above draws it.
                        Text(
                            "${standing.target}",
                            style = MaterialTheme.typography.headlineMedium.tabular()
                                .copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier
                                .border(2.dp, MaterialTheme.colorScheme.outline)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                        Text(
                            SuccessRoll.printed(standing.target) + if (attacks > 1) ", each" else "",
                            style = MaterialTheme.typography.labelLarge.tabular(),
                            modifier = Modifier.padding(start = 10.dp),
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
            Button(shape = ButtonShape, onClick = onRoll, enabled = !standing.forbidden) {
                Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Roll", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
