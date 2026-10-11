package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pathways.model.Outcome
import com.pathways.rules.SkillRollOutcome
import com.pathways.rules.SkillTarget
import com.pathways.rules.SpiritualSenses
import com.pathways.rules.content.SpiritualSense

private const val TABULAR = "tnum"

/**
 * The target as the chips stand, before the dice: the number Roll rolls.
 *
 * The same height whatever is picked, so the chips below cannot move under
 * a finger: the struck-out base is always laid out and only shown once the
 * target differs from it. What moved it is listed under the chips, where it
 * can grow: a declared clause adds a line, and above the chips that line
 * would push them down mid-tap.
 */
@Composable
internal fun TargetCard(standing: SkillTarget) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Target",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.heightIn(min = 64.dp), contentAlignment = Alignment.Center) {
                if (standing.forbidden) {
                    Text(
                        FORBIDDEN_ROLL,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    val moved = standing.target != standing.baseTarget
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${standing.target}",
                            style = MaterialTheme.typography.displayMedium
                                .copy(fontFeatureSettings = TABULAR),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "${standing.baseTarget}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFeatureSettings = TABULAR,
                                textDecoration = TextDecoration.LineThrough,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 8.dp, bottom = 6.dp)
                                .then(if (moved) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}),
                        )
                    }
                }
            }
            // Laid out when forbidden too, so a forbidding clause cannot
            // shrink the card under a finger.
            Pill(
                "3d6 ≤ ${standing.target}",
                Modifier.then(if (standing.forbidden) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier),
            )
        }
    }
}

/**
 * The dice, once cast: the verdict in its own colour, the total against the
 * target it was rolled at, and the margin. Every line that moved the target
 * is listed below it, the chips being gone by then.
 */
@Composable
internal fun ResultCard(out: SkillRollOutcome, sense: SpiritualSense? = null) {
    val roll = out.roll
    // effectiveOutcome, not roll.outcome: RequireCrit demotes an ordinary
    // success, and showing "Success" with a note underneath would misreport
    // the result.
    val shown = roll?.let { out.effectiveOutcome ?: it.outcome }
    val accent = shown?.let { colorFor(it) } ?: MaterialTheme.colorScheme.error
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, accent),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (roll == null || shown == null) {
                Text(FORBIDDEN_ROLL, color = accent)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label(shown, out.degree),
                        style = MaterialTheme.typography.headlineMedium,
                        color = accent,
                    )
                    Text(
                        "3d6: ${roll.total} vs ${roll.target}",
                        style = MaterialTheme.typography.titleMedium
                            .copy(fontFeatureSettings = TABULAR),
                    )
                }
                Tag("margin ${roll.margin}", accent)
            }
            // The skill's own table, when its chapter prints one. Read from
            // the EFFECTIVE outcome, so a roll demoted by requiresCritical
            // cannot come back carrying a success row -- the defect
            // SkillRollOutcome.degree avoids one object over. A failure lands
            // on no row, because chapter 4 prints none.
            if (sense != null) {
                val row = SpiritualSenses.rowFor(sense, shown, roll.margin)
                Text(
                    if (row != null) "${row.result} — ${row.effect}"
                    else "${sense.skill} prints no row for this result.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    Column(Modifier.padding(top = 8.dp)) {
        if (out.target != out.baseTarget) {
            Text(
                "base ${out.baseTarget}, modified to ${out.target}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LedgerLines(out.lines(), out.requiresCritical)
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR),
        modifier = modifier
            .padding(vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR),
        color = color,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
internal fun colorFor(outcome: Outcome) = when (outcome) {
    Outcome.CRITICAL_FAILURE, Outcome.FAILURE -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}
