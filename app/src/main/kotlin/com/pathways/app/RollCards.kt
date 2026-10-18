package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

/**
 * The same card for a roll outside the ledger: the Fright Check's.
 *
 * Case File: the target boxed, the base struck beside it, and the dice
 * notation at the right.
 */
@Composable
internal fun TargetCard(target: Int, baseTarget: Int, forbidden: Boolean = false) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        // The dice pill shares the caption's line, not the number's: beside
        // the number a long "3d6 ≤ 16, 17–18 fail" took the width the number
        // needed, and its column wrapped on a phone, growing the card under
        // the finger.
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caps("Target", Modifier.weight(1f).padding(end = 8.dp))
                // Laid out when forbidden too, so a forbidding clause cannot
                // shrink the card under a finger.
                Pill(
                    SuccessRoll.printed(target),
                    if (forbidden) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
                )
            }
            Box(Modifier.padding(top = 4.dp).heightIn(min = 56.dp), contentAlignment = Alignment.CenterStart) {
                if (forbidden) {
                    Text(FORBIDDEN_ROLL, color = MaterialTheme.colorScheme.error)
                } else {
                    val moved = target != baseTarget
                    Row(verticalAlignment = Alignment.Bottom) {
                        Box(
                            Modifier
                                .heightIn(min = 56.dp)
                                .widthIn(min = 56.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .border(2.dp, MaterialTheme.colorScheme.outline)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$target",
                                style = MaterialTheme.typography.headlineMedium.tabular()
                                    .copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                        Text(
                            "$baseTarget",
                            style = MaterialTheme.typography.titleLarge.tabular().copy(
                                textDecoration = TextDecoration.LineThrough,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 8.dp, bottom = 4.dp)
                                .then(if (moved) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The dice, once cast: the verdict as a stamp in its own colour, the total
 * against the target it was rolled at, and the margin. Every line that
 * moved the target is listed below it, the chips being gone by then.
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
    if (roll == null || shown == null) {
        StampSheet { Text(FORBIDDEN_ROLL, color = MaterialTheme.colorScheme.error) }
    } else {
        StampSheet {
            Stamp(label(shown, degree), colorFor(shown))
            DiceLine(roll)
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
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LedgerLines(out.lines(), out.requiresCritical)
    }
}

/**
 * A bare roll's verdict, for the dialogs outside the ledger: the word as a
 * stamp, ink for a pass and red for a failure, and the dice against the
 * target they were rolled at. What the verdict means goes in [content].
 */
@Composable
internal fun VerdictCard(
    verdict: String,
    passed: Boolean,
    roll: RollResult,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    StampSheet {
        Stamp(verdict, if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        DiceLine(roll)
        content()
    }
}

/** The ruled sheet a verdict is stamped on. */
@Composable
private fun StampSheet(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/**
 * The verdict, inked on at a tilt. The tilt is drawn, not laid out, so a
 * long verdict takes the height it always would.
 */
@Composable
internal fun Stamp(
    verdict: String,
    color: Color,
    style: TextStyle = MaterialTheme.typography.titleSmall.copy(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold),
) {
    Caps(
        verdict,
        Modifier
            .padding(top = 4.dp)
            .rotate(-3f)
            .border(2.dp, color)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = style,
        color = color,
    )
}

@Composable
private fun DiceLine(roll: RollResult) {
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "3d6: ${roll.total} vs ${roll.target}",
            style = MaterialTheme.typography.titleMedium.tabular(),
            modifier = Modifier.padding(end = 8.dp),
        )
        Tag("margin ${roll.margin}", MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.tabular(),
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** A fact in a ruled box, in mono: the margin, a budget's spare points. */
@Composable
internal fun Tag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.tabular(),
        color = color,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
internal fun colorFor(outcome: Outcome) = when (outcome) {
    Outcome.CRITICAL_FAILURE, Outcome.FAILURE -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}
