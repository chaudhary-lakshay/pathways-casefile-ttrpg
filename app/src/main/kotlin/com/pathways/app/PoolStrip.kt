package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pathways.rules.PenaltyScope
import com.pathways.rules.Pool
import com.pathways.rules.PoolStatus

/**
 * Sanzo Wada, *A Dictionary of Color Combinations* (1933), combination #269:
 * the four colours the redesign is drawn in (the owner's pick, 2026-10-11).
 *
 * ponytail: only the pool colours live here so far, read by [PoolStrip].
 * Moving the whole [ThemeSchemes] onto this set is the theme slice's job.
 */
internal object Wada269 {
    val RAW_SIENNA = Color(0xFFBB7125)
    val PALE_BURNT_LAKE = Color(0xFF802626)
    val ACONITE_VIOLET = Color(0xFFA36AA5)

    /** Pale Burnt Lake lifted for text on a dark page, where #802626 fails contrast. */
    val PALE_BURNT_LAKE_ON_DARK = Color(0xFFC25A5A)
}

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
 * HP, FP and SPI, always on screen above the sheet's tabs.
 *
 * One strip rather than three rows inside the Vitals tab: health is what a
 * player checks between every roll, and it sat behind a tab. Cells sit side
 * by side on a tablet and stack on a phone, where three steppers will not fit
 * in a row.
 *
 * ponytail: -1/+1 only, as before. Damage is rolled off dice this app does
 * not own, and a "take N" field is a keyboard in the middle of a sheet.
 */
@Composable
internal fun PoolStrip(
    pools: List<PoolStatus>,
    onSpend: (Pool, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var opened by remember { mutableStateOf<Pool?>(null) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            BoxWithConstraints {
                if (maxWidth >= 560.dp) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        pools.forEachIndexed { i, status ->
                            if (i > 0) VerticalDivider(Modifier.fillMaxHeight())
                            PoolCell(status, opened == status.pool, Modifier.weight(1f),
                                onTags = { opened = status.pool.takeIf { it != opened } },
                                onSpend = { onSpend(status.pool, it) })
                        }
                    }
                } else {
                    Column {
                        pools.forEach { status ->
                            PoolCell(status, opened == status.pool, Modifier.fillMaxWidth(),
                                onTags = { opened = status.pool.takeIf { it != opened } },
                                onSpend = { onSpend(status.pool, it) })
                        }
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

@Composable
private fun PoolCell(
    status: PoolStatus,
    isOpen: Boolean,
    modifier: Modifier,
    onTags: () -> Unit,
    onSpend: (Int) -> Unit,
) {
    val color = poolColor(status.pool)
    val tags = poolTags(status)
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            status.pool.name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // "Lose" is the first button because spending is what happens at
            // the table; regaining is the GM's call, after rest.
            OutlinedIconButton(onClick = { onSpend(1) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Lose 1 ${status.pool.name}")
            }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    "${status.current}",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    // In the pool's colour once the book names a state, as the
                    // old row went red: a Dead pool must not read like a full one.
                    color = if (tags.isEmpty()) MaterialTheme.colorScheme.onSurface else color,
                )
                Text(
                    " / ${status.max}",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            OutlinedIconButton(onClick = { onSpend(-1) }) {
                Icon(Icons.Filled.Add, contentDescription = "Regain 1 ${status.pool.name}")
            }
        }
        PoolBar(status, color)
        if (tags.isNotEmpty()) {
            // A Column, not a FlowRow: the cells share one height through
            // IntrinsicSize.Min, and a FlowRow under-reports its wrapped
            // lines there -- the second tag was clipped on the tablet.
            Column(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(
                        onClickLabel = if (isOpen) "Hide the rule" else "Show the rule",
                        role = Role.Button,
                        onClick = onTags,
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) { tags.forEach { PoolTag(it, color) } }
        }
    }
}

@Composable
private fun PoolBar(status: PoolStatus, color: Color) {
    val filled = status.current.coerceIn(0, status.max).toFloat() / status.max
    Box(
        Modifier.fillMaxWidth().height(6.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(3.dp)),
    ) {
        Box(Modifier.fillMaxWidth(filled).height(6.dp).background(color, RoundedCornerShape(3.dp)))
    }
}

@Composable
private fun PoolTag(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, color),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** The pool's colour, lifted for text where the page is dark. */
@Composable
private fun poolColor(pool: Pool): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (pool) {
        Pool.HP -> if (dark) Wada269.PALE_BURNT_LAKE_ON_DARK else Wada269.PALE_BURNT_LAKE
        Pool.FP -> Wada269.RAW_SIENNA
        Pool.SPI -> Wada269.ACONITE_VIOLET
    }
}
