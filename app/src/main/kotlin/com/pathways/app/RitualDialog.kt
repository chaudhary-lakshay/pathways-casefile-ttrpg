package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.Languages
import com.pathways.rules.RandomSource
import com.pathways.rules.RitualOutcome
import com.pathways.rules.RitualResolution
import com.pathways.rules.content.LanguageProficiency
import com.pathways.rules.content.RitualWeight

/** The skill every ritual is rolled against (Ch. 7). */
const val RITUAL_SKILL = "Ritualistic Magic"

/**
 * The fuel amounts to offer for [weight].
 *
 * A closed cell ("4-6 SPI") offers exactly what it prints. An open one ("8+")
 * offers the floor and three above it: the book names no ceiling, and a
 * stepper that could run to a hundred is not a choice a GM needs made for
 * them at the table.
 * ponytail: widen it if a GM asks.
 */
private fun spiChoices(weight: RitualWeight): List<Int> {
    val max = weight.spiMax
    return when {
        weight.spiOpen -> (weight.spi..weight.spi + 3).toList()
        max != null -> (weight.spi..max).toList()
        else -> listOf(weight.spi)
    }
}

private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"

/**
 * Cast a ritual (Ch. 7, p. 67-68).
 *
 * The caster's skill level is the character's own, so this dialog is only
 * reachable when they have the skill -- no premade prints it, which is why
 * buying it in the builder had to come first.
 *
 * Injury, fatigue and spiritual attrition arrive as [conditionPenalty] --
 * the same [com.pathways.rules.Conditions] the sheet's skill rolls use, minus
 * the corruption penalty, which [RitualResolution] applies itself.
 *
 * CoR is charged automatically on the three critical rows that print a cost
 * ("Corruption. Gain 1 CoR."), read by the extractor rather than scraped from
 * the text here. The rest of a critical result stays the GM's: an entity
 * noticing you is not a number.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RitualDialog(
    skillLevel: Int,
    conditionPenalty: Int,
    cor: Int,
    spiAvailable: Int,
    dice: RandomSource,
    onCorGained: (Int) -> Unit,
    onSpendSpi: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val weights = remember { RitualResolution.weights() }
    val preparations = remember { RitualResolution.preparations() }
    var weight by remember { mutableStateOf(weights.first()) }
    // Adequate (+0) is the default, not Excellent: a ritual nobody described
    // is an ordinary one, and starting at +3 would hand out the book's best
    // preparation for free.
    var preparation by remember { mutableStateOf(preparations.first { it.modifier == 0 }) }
    // Reset with the weight, or a Heavy ritual's 8 SPI would follow the caster
    // onto a Light one that costs 2.
    var spiChoice by remember(weight) { mutableIntStateOf(weight.spi) }
    var outcome by remember { mutableStateOf<RitualOutcome?>(null) }
    // Null by default -- "not stated" -- so a ritual described without a
    // language rolls exactly where it did before Ch. 3's table was read. No
    // premade prints a language and nothing buys one, so this is the GM's
    // declaration, like the fright trigger's stepper.
    var language by remember { mutableStateOf<LanguageProficiency?>(null) }
    val canSpeak = language?.let { Languages.canPerformRituals(it) } != false
    // Injury and fatigue reach a ritual like any other roll; the CoR penalty
    // is NOT included in [conditionPenalty] because RitualResolution applies
    // it itself, and counting it twice would penalise a corrupted caster
    // once for the ladder and once again for the same ladder.
    val effectiveSkill = skillLevel + conditionPenalty
    // Not computed at all when the book forbids the attempt: Broken prints
    // "Cannot perform rituals in the language", and targetFor refuses it
    // rather than inventing a penalty to roll at.
    val target = if (canSpeak) {
        RitualResolution.targetFor(effectiveSkill, weight, cor, preparation, language)
    } else {
        null
    }
    val cost = RitualResolution.spiFor(weight, spiChoice)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Text(
                outcome?.let { if (it.succeeded) "The ritual holds" else "The ritual fails" }
                    ?: "Cast a ritual",
            )
        },
        text = {
            // Scrolls: fuel and preparation add two chip rows, and an
            // AlertDialog does not scroll its own text slot -- the defect that
            // put the Roll button off the bottom of the attack dialog.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val result = outcome
                if (result == null) {
                    Row {
                        weights.forEach { option ->
                            FilterChip(
                                selected = option.weight == weight.weight,
                                onClick = { weight = option },
                                label = { Text(option.weight) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Text(
                        weight.categories,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    // Two of the three cells are a range, so the end is the
                    // GM's; an open one ("8+") offers the printed floor and
                    // three above it rather than pretending to know the top.
                    Text("Fuel", style = MaterialTheme.typography.titleSmall)
                    FlowRow {
                        spiChoices(weight).forEach { option ->
                            FilterChip(
                                selected = option == spiChoice,
                                onClick = { spiChoice = option },
                                label = { Text("$option SPI") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Text(
                        "Preparation (Phase I)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    FlowRow {
                        preparations.forEach { option ->
                            FilterChip(
                                selected = option.quality == preparation.quality,
                                onClick = { preparation = option },
                                label = { Text("${option.quality} ${option.printed}") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Text(preparation.looksLike, style = MaterialTheme.typography.bodySmall)

                    // Ch. 3, p. 14. Only the MYSTICAL table speaks about
                    // rituals; a common tongue is not offered, because the
                    // book never grants it ritual power and offering it would
                    // read as permission.
                    Text(
                        "Mystical language",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    FlowRow {
                        FilterChip(
                            selected = language == null,
                            onClick = { language = null },
                            label = { Text("Not stated") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Languages.proficiencies(Languages.MYSTICAL).forEach { option ->
                            FilterChip(
                                selected = language?.proficiency == option.proficiency,
                                onClick = { language = option },
                                label = { Text(option.proficiency) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    language?.let { Text(it.effect, style = MaterialTheme.typography.bodySmall) }

                    if (target == null) {
                        // Broken is offered as a chip and then refuses, rather
                        // than being hidden: a caster who only speaks it needs
                        // to see WHY there is no roll. Same reason the book's
                        // sentence is quoted instead of paraphrased.
                        Text(
                            "No roll — the book forbids this attempt.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        Text(
                            "3d6 ≤ $target",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            "$RITUAL_SKILL $skillLevel, ${weight.weight} ${weight.modifier}" +
                                ", preparation ${signed(preparation.modifier)}" +
                                (language?.ritualModifier?.takeIf { it != 0 }
                                    ?.let { ", $it for an accented tongue" } ?: "") +
                                (if (conditionPenalty != 0) ", $conditionPenalty hurt or tired" else "") +
                                (if (cor >= 3) ", -1 for $cor CoR" else "") +
                                ". Costs $cost SPI.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (cost > spiAvailable) {
                            Text(
                                "Only $spiAvailable SPI left — going below 0 is Soul Debt.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        TextButton(
                            onClick = {
                                // One Roll spends once: a second tap before
                                // the button leaves the tree would charge the
                                // fuel again, and below 0 that buys permanent
                                // CoR.
                                if (outcome != null) return@TextButton
                                val rolled = RitualResolution.attempt(
                                    effectiveSkill,
                                    weight,
                                    cor,
                                    dice,
                                    preparation,
                                    language,
                                )
                                outcome = rolled
                                // The fuel is spent whether or not it works:
                                // "No effect. SPI and materials spent" (p. 62).
                                onSpendSpi(cost)
                                rolled.critical?.corGained?.let(onCorGained)
                            },
                        ) { Text("Roll") }
                    }
                } else {
                    Text(
                        "3d6: ${result.roll.total} vs ${result.target}",
                        style = MaterialTheme.typography.titleLarge,
                    )
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
                            Text("+$it CoR charged.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
    )
}
