package com.pathways.app

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.AttackPlan
import com.pathways.rules.DarknessCause
import com.pathways.rules.TraitEffects
import com.pathways.rules.Ranged
import com.pathways.rules.content.RangePenalty

/**
 * Distance and the scene, for a shot (Ch. 5, p. 39-40).
 *
 * The two Aim rows are selectable only with a weapon in hand: their modifier
 * is that weapon's Acc, and the cell says so ("see weapon table"). Without one
 * there is no number, and [Ranged.modifiers] refuses rather than applying
 * zero, so offering the chip would build a plan that throws on Roll.
 */
@Composable
internal fun RangedOptions(
    band: RangePenalty?,
    circumstances: Set<String>,
    armed: Armed?,
    aimCancelled: Boolean,
    aimRefusedBy: String?,
    darknessCause: DarknessCause,
    darknessReduction: Int,
    darknessSeverity: Int?,
    onBand: (RangePenalty) -> Unit,
    onCircumstance: (String) -> Unit,
    onAimCancelled: () -> Unit,
    onDarknessCause: (DarknessCause) -> Unit,
    onDarknessSeverity: (Int) -> Unit,
) {
    val bands = remember { Ranged.bands() }
    // What the book still allows, decided in rules: a screen filtering its own
    // list is a second copy of the refusal (6ag, 6ai, 6be).
    val conditions = remember(aimCancelled, aimRefusedBy) {
        Ranged.offeredConditions(aimCancelled || aimRefusedBy != null)
    }
    Text("Distance", style = MaterialTheme.typography.titleSmall)
    FlowRow {
        bands.forEach { option ->
            FilterChip(
                selected = band?.printed == option.printed,
                onClick = { onBand(option) },
                label = { Text("${option.printed.substringBefore(" meters")} ${signed(option.penalty)}") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    Text(
        "Conditions",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow {
        conditions.filterNot { it.weaponAcc }.forEach { option ->
            FilterChip(
                selected = option.condition in circumstances,
                onClick = { onCircumstance(option.condition) },
                label = { Text("${option.condition} ${option.printed}") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    // Offered only to a character the trait can help, and only once the row
    // it modifies is actually selected: nobody else has anything to declare,
    // and the row itself is never withdrawn from them.
    if (DARKNESS_ROW in circumstances) {
        val row = Ranged.conditions().single { it.condition == DARKNESS_ROW }
        Text(
            "How bad is it?",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        FlowRow {
            endsBetween(row.modifier!!, row.worstCase).forEach { value ->
                FilterChip(
                    // Unset shows the row's own figure selected, because that
                    // is the one the roll is using.
                    selected = (darknessSeverity ?: row.modifier) == value,
                    onClick = { onDarknessSeverity(value) },
                    label = { Text(signed(value)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
    // Offered only to a character the trait can help, and only once the row
    // it modifies is actually selected.
    if (darknessReduction > 0 && DARKNESS_ROW in circumstances) {
        Text(
            "What is it?",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        FlowRow {
            DarknessCause.entries.forEach { option ->
                FilterChip(
                    selected = option == darknessCause,
                    onClick = { onDarknessCause(option) },
                    label = { Text(causeLabel(option)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        Text(
            "${TraitEffects.NIGHT_EYES}: \"${TraitEffects.printedNightEyes()}\"",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (aimRefusedBy != null) {
        // Withdrawn with the cell's own words, like a cancelled aim (6bj),
        // and no chip: there is no aim to cancel.
        Text(
            "$aimRefusedBy: \"${TraitEffects.AIM_REFUSAL}\"",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    Text("Since taking aim", style = MaterialTheme.typography.titleSmall)
    FlowRow {
        FilterChip(
            selected = aimCancelled,
            onClick = { onAimCancelled() },
            label = { Text("Defended or was hurt") },
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    if (aimCancelled) {
        // Withdrawn with the reason SAID: rows that simply vanish read as a
        // screen that lost them (6bj), and the aim cost a turn to take.
        Text(
            "Aim (Ranged): \"${Ranged.printedAim()}\"",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    val acc = armed.aimAcc
    conditions.filter { it.weaponAcc }.forEach { option ->
        if (acc == null) {
            Text(
                // A FIRED weapon, not merely a held one: the toggle above is
                // the player's, so a knife can be carried into a ranged plan,
                // and the Hand Axe prints Acc 1 -- enough to aim a hatchet for
                // +3 over three turns if the chip were offered (Codex).
                "${option.condition}: ${option.printed} — choose a firearm above.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            FilterChip(
                selected = option.condition in circumstances,
                // One aim, one length of time: Ranged.modifiers refuses both
                // rows together, because each pays the weapon's Acc and two
                // would break the second row's own "max Acc +2 total".
                onClick = { onCircumstance(option.condition) },
                label = { Text("${option.condition} ${signed(acc + option.accBonus)}") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

internal fun rangedModifiers(
    plan: AttackPlan,
    band: RangePenalty?,
    circumstances: Set<String>,
    weaponAcc: Int?,
    aimCancelled: Boolean,
    darknessCause: DarknessCause,
    darknessReduction: Int,
    darknessSeverity: Int?,
): List<com.pathways.model.Modifier> {
    if (!plan.isRanged) return emptyList()
    // The chosen band is passed whole, never re-looked-up by its lower bound:
    // "100+" begins at 100 and bandFor(100) is the -5 row (Codex).
    return Ranged.modifiers(
        band = band,
        conditions = Ranged.conditions().filter { it.condition in circumstances },
        // Keyed by the row's own condition, and only when the GM has said --
        // an empty map is what the engine already treats as "take the row's
        // figure", so there is one authority on that default and it is not
        // this screen.
        gmValue = darknessSeverity?.let { mapOf(DARKNESS_ROW to it) } ?: emptyMap(),
        weaponAcc = weaponAcc,
        aimCancelled = aimCancelled,
        darknessCause = darknessCause,
        darknessReduction = darknessReduction,
    )
}

/**
 * The two rows whose modifier is the weapon's Acc.
 *
 * Read off the content rather than spelled out here, so a re-extraction that
 * renames a row cannot leave this holding a string nothing matches -- which
 * would silently stop clearing an aim the character can no longer take.
 */
internal val AIM_ROWS: Set<String> by lazy {
    Ranged.conditions().filter { it.weaponAcc }.map { it.condition }.toSet()
}

/**
 * The one row a [DarknessCause] can change, found by the range it prints
 * rather than by its name -- a re-extraction that reworded the condition would
 * otherwise leave this holding a string nothing matches, silently withdrawing
 * the chips from the character who owns the trait.
 */
private val DARKNESS_ROW: String by lazy {
    Ranged.conditions().single { it.worstCase != null }.condition
}

/** The GM's three answers, in the trait's own words. */
private fun causeLabel(cause: DarknessCause) = when (cause) {
    DarknessCause.HEAVY_FOG -> "Heavy fog"
    DarknessCause.DARKNESS -> "Darkness"
    DarknessCause.DIM_LIGHT -> "Dim gaslight or moonlight"
}

/**
 * The Acc to aim with, or null when nothing in hand is fired.
 *
 * Not simply the held weapon's Acc (Codex): the "At range" toggle stays the
 * player's, so a knife can be carried into a ranged plan, and the Hand Axe
 * prints **Acc 1**. Gating on the weapon merely being present let a hatchet
 * be aimed for +1, or +3 over three turns -- a modifier the book gives only
 * to something fired.
 */
internal val Armed?.aimAcc: Int? get() = if (isFired) this?.weapon?.acc else null
