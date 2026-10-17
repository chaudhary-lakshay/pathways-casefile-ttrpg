package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.RitualOutcome
import com.pathways.rules.SuccessRoll

/**
 * The foot of the ritual's choices: every term of the target, the target, its
 * price and Cast, pinned below the scrolling chips so the number and the
 * button that rolls it are never scrolled apart.
 *
 * [refusal] withdraws Cast and says why; a null [target] is a ritual the book
 * forbids or the entity rejected, and prints no number at all.
 */
@Composable
internal fun RitualFooter(
    terms: List<String>,
    target: Int?,
    cost: String,
    refusal: String?,
    /** What the target reads as: [SuccessRoll.printed], which says a boon's -512 still wins on a 3 or 4. */
    targetText: String = target?.let(SuccessRoll::printed).orEmpty(),
    label: String = "Cast",
    onCast: () -> Unit,
) {
    Column {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (target != null) {
            Text(
                terms.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        refusal?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (target != null) {
                    Text(
                        "$target",
                        style = MaterialTheme.typography.displaySmall
                            .tabular(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(targetText, style = MaterialTheme.typography.labelLarge)
                        Text(
                            cost,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Button(onClick = onCast, enabled = target != null && refusal == null) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/**
 * The ritual's dice, once cast: whether it holds, the total against the
 * target it was rolled at, and the row of the book's table it landed on.
 */
@Composable
internal fun RitualResultCard(result: RitualOutcome) {
    val accent = if (result.succeeded) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, accent),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (result.succeeded) "The ritual holds" else "The ritual fails",
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
            )
            Text(
                "3d6: ${result.roll.total} vs ${result.target}",
                style = MaterialTheme.typography.titleMedium.tabular(),
            )
            // Step 4's grade, and the counterpart of the failure row below
            // it. Not coloured as an error -- the ritual worked.
            result.success?.let { success ->
                Text(
                    "${success.printed} — ${success.outcome}",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            RitualFailureLines(result)
        }
    }
}

/**
 * The failure row a ritual landed on, and the Critical Failure Table's row
 * with the CoR it charges on closing. Empty for a success.
 */
@Composable
internal fun RitualFailureLines(result: RitualOutcome) {
    result.consequence?.let { consequence ->
        Text(
            "${consequence.printed} — ${consequence.consequence}",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    result.critical?.let { critical ->
        Text(
            "Critical Failure Table ${result.criticalRoll} " +
                "(${critical.printed}): ${critical.outcome}",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
        critical.corGained?.let {
            Text("+$it CoR on closing.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
