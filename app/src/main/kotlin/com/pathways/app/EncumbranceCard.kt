package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pathways.rules.Encumbrance

/** "BL n lbs" without a trailing ".0" on a whole number. */
private fun lbs(value: Double): String =
    if (value == Math.floor(value)) "${value.toLong()} lbs" else "${"%.1f".format(value)} lbs"

/** What [carried] lbs means against [basicLift]: the level and its penalties, or none typed. */
internal fun encumbranceLine(carried: Double?, basicLift: Double): String {
    val head = "BL ${lbs(basicLift)} · "
    if (carried == null) return head + "none"
    val level = Encumbrance.levelFor(carried, basicLift)
        ?: return head + "over 15 × BL: the table prints no level, so it cannot be carried"
    if (level.movePenalty == 0) return head + "${level.name}, no penalty"
    return head + "${level.name}: Move ${level.movePenalty}, Dodge ${level.dodgePenalty}" +
        if (level.cannotRun) ", cannot run" else ""
}

/** The Dodge penalty [carried] lbs puts on Defend; 0 where nothing is typed or the load has no level. */
internal fun encumbranceDodge(carried: Double?, basicLift: Double): Int =
    carried?.let { Encumbrance.levelFor(it, basicLift)?.dodgePenalty } ?: 0

/**
 * The weight carried, typed by the player (owner's ruling 9), and what it
 * costs Move and Dodge. Held in [SheetScene]: a load is the scene's, not a purchase.
 */
@Composable
internal fun EncumbranceCard(carried: Double?, basicLift: Double, onCarried: (Double?) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionLabel("Carrying")
        OutlinedTextField(
            value = carried?.let { if (it == Math.floor(it)) "${it.toLong()}" else "$it" } ?: "",
            // Blank clears it; anything unreadable or negative is not taken.
            onValueChange = { typed ->
                if (typed.isBlank()) onCarried(null) else typed.toDoubleOrNull()?.takeIf { it >= 0 }?.let(onCarried)
            },
            label = { Text("Total weight carried (lbs)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(encumbranceLine(carried, basicLift), style = MaterialTheme.typography.bodySmall)
    }
}
