package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.util.Locale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathways.rules.PenaltyScope
import com.pathways.rules.Pool
import com.pathways.rules.PoolStatus

/**
 * What a pool's state costs, as tags: the book's own name for the row
 * ("Reeling", "Spiritual Attrition"), or its printed level where the row has
 * no name, then the penalty and any CoR it charged. Empty at normal function.
 *
 * Never a rewording: a name is the text before the colon in the printed
 * effect, and the full row is [poolLine], one tap away.
 */
internal fun poolTags(status: PoolStatus): List<String> {
    if (status.isDead) return listOf("Dead")
    val hurt = status.penalty != 0 || status.isUnconscious || status.abilitiesOffline ||
        status.rollsToStayConscious || status.corGained > 0
    if (!hurt) return emptyList()
    return buildList {
        when {
            status.isUnconscious -> add("Unconscious")
            ':' in status.effect -> add(status.effect.substringBefore(':'))
            // FP prints no names, and its levels are thresholds ("1/3 FP or
            // below") rather than states; the penalty below says it.
            status.pool != Pool.FP -> add(status.level)
        }
        if (status.penalty != 0) {
            val what = when (status.scope) {
                PenaltyScope.SPI_ROLLS -> "SPI rolls"
                PenaltyScope.ALL_ROLLS -> "all rolls"
            }
            add("−${-status.penalty} $what")
        }
        if (status.corGained > 0) add("+${status.corGained} CoR")
    }
}

/**
 * The printed row in full, for the tap behind the tags.
 *
 * The penalty is said with what it applies to: -3 for Spiritual Attrition is
 * SPI rolls only, and a GM reading it as -3 to everything would be the whole
 * point of carrying the scope. CoR is reported as DONE, not as a chore:
 * [BeyonderSession.spend] charges it (6fd).
 */
internal fun poolLine(status: PoolStatus): String = buildList {
    add(status.level)
    if (status.penalty != 0) {
        val what = when (status.scope) {
            PenaltyScope.SPI_ROLLS -> "SPI rolls and ability checks"
            PenaltyScope.ALL_ROLLS -> "all rolls"
        }
        add("${status.penalty} to $what")
    }
    if (status.corGained > 0) add("${status.corGained} CoR charged")
    add(status.effect)
}.joinToString(" — ")

/**
 * HP, FP and SPI, above the sheet's tabs, in the two forms the frames draw:
 * [stacked] on Vitals, a full-width row per pool with its name spelled out
 * and the stepper beside it (Case File, frame 02), and otherwise three
 * columns with the stepper under each (frames 04 and 08).
 *
 * The state tags sit on the label's own line, cut short rather than wrapped:
 * a threshold appearing or clearing must not move the stepper under a finger
 * (play session 4). The full row is a tap on the pool away.
 *
 * ponytail: -1/+1 only, as before. Damage is rolled off dice this app does
 * not own, and a "take N" field is a keyboard in the middle of a sheet.
 */
@Composable
internal fun PoolStrip(
    pools: List<PoolStatus>,
    onSpend: (Pool, Int) -> Unit,
    stacked: Boolean,
    modifier: Modifier = Modifier,
) {
    var opened by remember { mutableStateOf<Pool?>(null) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        // The card, a shade off the paper (frames 02 and 04).
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            if (stacked) {
                pools.forEachIndexed { i, status ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PoolRow(status, onTags = { opened = status.pool.takeIf { it != opened } }, onSpend = { onSpend(status.pool, it) })
                }
            } else {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    pools.forEachIndexed { i, status ->
                        if (i > 0) VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outline)
                        PoolCell(
                            status,
                            Modifier.weight(1f),
                            onTags = { opened = status.pool.takeIf { it != opened } },
                            onSpend = { onSpend(status.pool, it) },
                        )
                    }
                }
            }
            // Only while the pool still has tags: one healed while open would
            // otherwise leave its line up with nothing left to tap closed.
            pools.firstOrNull { it.pool == opened && poolTags(it).isNotEmpty() }?.let {
                Text(
                    poolLine(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                )
            }
        }
    }
}

/** A pool's name as the stacked rows spell it (frame 02). */
private fun spelled(pool: Pool) = when (pool) {
    Pool.HP -> "Hit points"
    Pool.FP -> "Fatigue points"
    Pool.SPI -> "Spirituality"
}

/** Frame 02's row: the name, the count and the pips, the stepper at the right. */
@Composable
private fun PoolRow(status: PoolStatus, onTags: () -> Unit, onSpend: (Int) -> Unit) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).then(tagsTap(status, onTags)), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PoolLabel(spelled(status.pool), status)
            PoolCount(status, MaterialTheme.typography.headlineMedium)
            PoolBar(status, largest = 18.dp, gap = 4.dp, round = status.pool == Pool.SPI)
        }
        PoolStepper(status, onSpend, Modifier.padding(start = 12.dp).width(100.dp))
    }
}

/** Frames 04 and 08's cell: the short name, the count, the pips, the stepper under them. */
@Composable
private fun PoolCell(status: PoolStatus, modifier: Modifier, onTags: () -> Unit, onSpend: (Int) -> Unit) {
    Column(modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.padding(horizontal = 4.dp).then(tagsTap(status, onTags)), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PoolLabel(status.pool.name, status)
            PoolCount(status, MaterialTheme.typography.titleLarge)
            PoolBar(status, largest = 10.dp, gap = 2.dp, round = false)
        }
        PoolStepper(status, onSpend, Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

/** A tap on the pool opens its printed row, while it has one. */
private fun tagsTap(status: PoolStatus, onTags: () -> Unit): Modifier =
    if (poolTags(status).isEmpty()) {
        Modifier
    } else {
        Modifier.clickable(onClickLabel = "Show the rule", role = Role.Button, onClick = onTags)
    }

/** The pool's name, then its state tags on the same line, cut short rather than wrapped. */
@Composable
private fun PoolLabel(name: String, status: PoolStatus) {
    val tags = poolTags(status)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Caps(
            name,
            color = if (status.pool == Pool.SPI) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        if (tags.isNotEmpty()) {
            Text(
                tags.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * "09/15": the count two digits wide in mono, the maximum smaller and muted.
 * One line that shrinks rather than wraps: "15 / 15" wrapped in a third of
 * the 600 dp column on the SM-T570 (6ic). Red once the book names a state:
 * a Dead pool must not read like a full one.
 */
@Composable
private fun PoolCount(status: PoolStatus, style: TextStyle) {
    val current = style.tabular().copy(fontWeight = FontWeight.SemiBold)
    val maxSpan = SpanStyle(
        fontSize = 0.65.em,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    BasicText(
        buildAnnotatedString {
            append(twoDigits(status.current))
            withStyle(maxSpan) { append("/%02d".format(status.max)) }
        },
        style = current.copy(
            color = if (poolTags(status).isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        ),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = current.fontSize),
        modifier = Modifier.semantics { contentDescription = "${status.current} of ${status.max}" },
    )
}

/** "09": two digits, in Latin numerals whatever the device's locale. */
private fun twoDigits(n: Int): String = String.format(Locale.ROOT, "%02d", n)

/**
 * Lose and regain as two joined cells in one ink box. "Lose" is first
 * because spending is what happens at the table; regaining is filled while
 * there is anything to regain, and greyed at the maximum, as drawn.
 */
@Composable
private fun PoolStepper(status: PoolStatus, onSpend: (Int) -> Unit, modifier: Modifier) {
    val canRegain = status.current < status.max
    val ink = MaterialTheme.colorScheme.outline
    Row(
        modifier
            .height(IntrinsicSize.Min)
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, ink, MaterialTheme.shapes.small),
    ) {
        StepCell(Icons.Filled.Remove, "Lose 1 ${status.pool.name}", enabled = true, filled = false, Modifier.weight(1f)) { onSpend(1) }
        VerticalDivider(Modifier.fillMaxHeight(), color = ink)
        StepCell(Icons.Filled.Add, "Regain 1 ${status.pool.name}", enabled = canRegain, filled = canRegain, Modifier.weight(1f)) { onSpend(-1) }
    }
}

@Composable
private fun StepCell(icon: ImageVector, label: String, enabled: Boolean, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .background(if (filled) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                filled -> MaterialTheme.colorScheme.onPrimary
                enabled -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.outlineVariant
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The pools as one thin line under the bar, once the strip has scrolled
 * away (Case File, frame 03): the count and the pips, no stepper.
 */
@Composable
internal fun PoolLine(pools: List<PoolStatus>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        pools.forEach { status ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val spi = status.pool == Pool.SPI
                val color = if (spi) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                Text(
                    buildAnnotatedString {
                        append(status.pool.name + " ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(twoDigits(status.current)) }
                        withStyle(SpanStyle(color = if (spi) color else MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append("/%02d".format(status.max))
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (poolTags(status).isEmpty()) color else MaterialTheme.colorScheme.error,
                    maxLines = 1,
                )
                PoolBar(status, largest = 8.dp, gap = 2.dp, round = false, crossed = false)
            }
        }
    }
}

private val PIP_GAP = 2.dp
private val PIP_MAX = 14.dp
private val PIP_MIN = 6.dp

/**
 * How big a pool's pips can be in [width]: as large as fits, never above
 * [largest], and null below [PIP_MIN], where a row of squares stops being
 * countable at a glance and the plain bar says more.
 */
internal fun pipSize(max: Int, width: Dp, largest: Dp = PIP_MAX, gap: Dp = PIP_GAP): Dp? {
    if (max <= 0) return null
    val size = minOf((width - gap * (max - 1)) / max, largest)
    return size.takeIf { it >= PIP_MIN }
}

/**
 * The pool as one pip per point (Case File, frames 02 and 04): filled for
 * what is left, an empty box for what is spent, crossed in red where there
 * is room. SPI's pips are round on the Vitals rows. A pool too large for
 * its cell falls back to a bar.
 */
@Composable
private fun PoolBar(status: PoolStatus, largest: Dp, gap: Dp, round: Boolean, crossed: Boolean = true) {
    val color = poolColor(status.pool)
    val left = status.current.coerceIn(0, status.max)
    val spent = MaterialTheme.colorScheme.error
    val rule = MaterialTheme.colorScheme.outlineVariant
    // One Canvas, not a Row of boxes in a BoxWithConstraints: the strip's
    // cells share a height through IntrinsicSize.Min, and a SubcomposeLayout
    // throws when asked for an intrinsic (it crashed the sheet on the tablet).
    Canvas(Modifier.fillMaxWidth().height(largest)) {
        val stroke = 1.dp.toPx()
        val pip = pipSize(status.max, size.width.toDp(), largest, gap)?.toPx()
        if (pip == null) {
            val bar = minOf(size.height, 8.dp.toPx())
            val top = (size.height - bar) / 2
            drawRect(color, Offset(0f, top), Size(size.width * left / status.max, bar))
            drawRect(color, Offset(0f, top), Size(size.width, bar), style = Stroke(stroke))
            return@Canvas
        }
        val top = (size.height - pip) / 2
        val step = pip + gap.toPx()
        repeat(status.max) { i ->
            val at = Offset(i * step, top)
            val centre = at + Offset(pip / 2, pip / 2)
            when {
                i < left && round -> drawCircle(color, pip / 2, centre)
                i < left -> drawRect(color, at, Size(pip, pip))
                round -> drawCircle(rule, pip / 2 - stroke / 2, centre, style = Stroke(stroke))
                else -> drawRect(rule, at, Size(pip, pip), style = Stroke(stroke))
            }
            if (i >= left && crossed && pip >= 12.dp.toPx()) {
                val inset = pip * 0.3f
                drawLine(spent, at + Offset(inset, inset), at + Offset(pip - inset, pip - inset), stroke)
                drawLine(spent, at + Offset(pip - inset, inset), at + Offset(inset, pip - inset), stroke)
            }
        }
    }
}

/**
 * The pool's own colour (Case File): HP and FP in ink, SPI in the stamp red
 * that marks the Beyonder. Harm is told apart by `error` on the number and
 * the state tags, never by the pool.
 */
@Composable
private fun poolColor(pool: Pool): Color = when (pool) {
    Pool.HP, Pool.FP -> MaterialTheme.colorScheme.primary
    Pool.SPI -> MaterialTheme.colorScheme.secondary
}
