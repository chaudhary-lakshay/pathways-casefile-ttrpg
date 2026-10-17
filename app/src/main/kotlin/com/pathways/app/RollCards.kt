package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.rules.SkillRollOutcome
import com.pathways.rules.SkillTarget
import com.pathways.rules.SpiritualSenses
import com.pathways.rules.SuccessDegree
import com.pathways.rules.SuccessRoll
import com.pathways.rules.content.SpiritualSense

private const val TABULAR = "tnum"

/**
 * An AlertDialog's `text` slot at one height whatever it shows: the chips,
 * the verdict that replaces them, a picker filtering or a row opening.
 *
 * An AlertDialog re-centres on every height change, so a shrink moved the
 * buttons up under the finger (play session 6: Roll rose ~60dp once the
 * verdict replaced the chips, and a second tap where it had been could land
 * on the scrim and dismiss the verdict) and a growth moved the controls above
 * the change. Wrapping the whole slot, not one branch's Column, is what makes
 * it hold: chips and verdict are separate branches, so a size remembered by
 * one is gone when the other composes (Codex's design review).
 *
 * ponytail: a fixed cap, taller than any body measured on the SM-T570 in
 * landscape; the slot's own maximum wins on a shorter window. A short dialog
 * shows blank space under its content, the price of standing still.
 */
internal fun steadyBody(content: @Composable () -> Unit): @Composable () -> Unit = {
    Box(Modifier.heightIn(max = STEADY_BODY_MAX).fillMaxHeight()) { content() }
}

private val STEADY_BODY_MAX = 560.dp

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
internal fun TargetCard(standing: SkillTarget) =
    TargetCard(standing.target, standing.baseTarget, standing.forbidden)

/** The same card for a roll outside the ledger: the Fright Check's. */
@Composable
internal fun TargetCard(target: Int, baseTarget: Int, forbidden: Boolean = false) {
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
                if (forbidden) {
                    Text(
                        FORBIDDEN_ROLL,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    val moved = target != baseTarget
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$target",
                            style = MaterialTheme.typography.displayMedium
                                .copy(fontFeatureSettings = TABULAR),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "$baseTarget",
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
                SuccessRoll.printed(target),
                Modifier.then(if (forbidden) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier),
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
internal fun ResultCard(
    out: SkillRollOutcome,
    sense: SpiritualSense? = null,
    // Appendix A's success bands, which the skill roll prints. The attack
    // passes null: it never printed a band, and adding one is a rule claim.
    degree: SuccessDegree? = out.degree,
) {
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
                        label(shown, degree),
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

/**
 * A bare roll's verdict, for the dialogs outside the ledger: the word in its
 * own colour and the dice against the target they were rolled at. What the
 * verdict means goes in [content], below.
 */
@Composable
internal fun VerdictCard(
    verdict: String,
    passed: Boolean,
    roll: RollResult,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val accent = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, accent),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(verdict, style = MaterialTheme.typography.headlineMedium, color = accent)
                    Text(
                        "3d6: ${roll.total} vs ${roll.target}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TABULAR),
                    )
                }
                Tag("margin ${roll.margin}", accent)
            }
            content()
        }
    }
}

/**
 * The foot of a bare roll's choices: what moved the target, the target, and
 * Roll, pinned below the scrolling choices as in the Attack and ritual
 * dialogs. With nothing to list one empty line is still laid out, so the
 * first declared clause cannot grow the dialog under a finger.
 */
@Composable
internal fun RollFooter(
    lines: List<String>,
    caption: String,
    enabled: Boolean = true,
    label: String = "Roll",
    onRoll: () -> Unit,
) {
    Column {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        lines.ifEmpty { listOf("") }.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(caption, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Button(onClick = onRoll, enabled = enabled) {
                Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
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
internal fun Tag(text: String, color: Color) {
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
