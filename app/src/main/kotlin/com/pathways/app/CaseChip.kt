package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The check a picked chip draws, its gap included. */
internal val CHIP_CHECK = 20.dp
private val CHECK_ICON = 16.dp

/** A chip's padding around its label, and the room its check takes. */
internal data class ChipInsets(val start: Dp, val check: Dp, val end: Dp) {
    val total: Dp get() = start + check + end
}

/**
 * Picked, the check takes the label's left padding; unpicked, that room is
 * split either side, so the label sits centred and the chip keeps its width.
 */
internal fun chipInsets(selected: Boolean): ChipInsets =
    if (selected) ChipInsets(start = 8.dp, check = CHIP_CHECK, end = 10.dp)
    else ChipInsets(start = 19.dp, check = 0.dp, end = 19.dp)

/**
 * The label and check colour of a chip, on its own fill. A picked chip keeps
 * the colour of its ink fill even when it cannot change (a fixed value): the
 * disabled ink on that fill was the fill itself, a blank box (issue #2).
 */
internal fun chipContent(selected: Boolean, enabled: Boolean, colors: ColorScheme): Color = when {
    selected -> colors.onSecondaryContainer.copy(alpha = if (enabled) 1f else 0.6f)
    !enabled -> colors.onSurface.copy(alpha = 0.38f)
    else -> colors.onSurface
}

/**
 * A choice as the Case File frames draw it (13-22): a ruled box with a mono
 * label, filled ink with a check when picked. FilterChip's own check widens
 * the chip, which can re-wrap a row of them; this one keeps its width.
 * Read as a checkbox, as FilterChip is, so a screen reader hears the pick.
 */
@Composable
internal fun CaseChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val content = chipContent(selected, enabled, colors)
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraSmall,
        color = if (selected) colors.secondaryContainer else colors.surface.copy(alpha = 0f),
        border = if (selected) null else BorderStroke(1.dp, if (enabled) colors.outlineVariant else colors.onSurface.copy(alpha = 0.12f)),
        modifier = modifier.semantics { role = Role.Checkbox },
    ) {
        val insets = chipInsets(selected)
        Row(
            Modifier.heightIn(min = 32.dp).padding(start = insets.start, end = insets.end, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Box(Modifier.padding(end = CHIP_CHECK - CHECK_ICON)) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = content, modifier = Modifier.size(CHECK_ICON))
                }
            }
            CompositionLocalProvider(LocalContentColor provides content) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontFamily = PlexMono), label)
            }
        }
    }
}
