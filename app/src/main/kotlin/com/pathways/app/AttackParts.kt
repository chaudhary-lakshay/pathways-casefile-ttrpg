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
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$label $shown", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The value between the steps as well as in the label, as the
            // frames draw it (13, 14, 15, 17).
            JoinedStepper(
                shown,
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
    /** Why no attack may be rolled at all ("Pinned"), or null. */
    refusal: String? = null,
    /** A spirit hit's pending points, which only the first attack takes. */
    spiritPending: Int = 0,
    onRoll: () -> Unit,
) {
    FooterBand {
        lines.forEach {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FooterRow(
            standing.target.takeUnless { standing.forbidden || refusal != null },
            // Nothing to roll is nothing to pay for: the handler charges the
            // plan's FP whatever the dice do.
            button = { RollButton("Roll", onRoll, enabled = !standing.forbidden && refusal == null) },
        ) {
            Column {
                if (refusal != null) {
                    Text(refusal, color = MaterialTheme.colorScheme.error)
                } else if (standing.forbidden) {
                    Text(FORBIDDEN_ROLL, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(
                        attackCaption(standing.target, attacks, spiritPending),
                        style = MaterialTheme.typography.labelLarge.tabular(),
                    )
                }
                if (standing.requiresCritical) {
                    Text(
                        "Only a critical success counts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** The footer's roll line: one target, "each" for several, or the first and the rest. */
internal fun attackCaption(target: Int, attacks: Int, spiritPending: Int): String {
    val first = SuccessRoll.printed(target)
    val later = SuccessRoll.printed(target + spiritPending)
    return when {
        attacks <= 1 -> first
        // Equal printings (a 3 then a 4, or both past 16) are one caption.
        first == later -> "$first, each"
        else -> "first: $first; after: $later"
    }
}

