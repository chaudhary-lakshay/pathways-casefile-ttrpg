package com.pathways.app

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.Languages
import com.pathways.rules.PowerSources
import com.pathways.rules.RitualPacing
import com.pathways.rules.content.LanguageProficiency
import com.pathways.rules.content.RitualComplexity
import com.pathways.rules.content.RitualRevelation
import com.pathways.rules.content.RitualWeight

/**
 * The fuel amounts to offer for [weight].
 *
 * A closed cell ("4-6 SPI") offers exactly what it prints. An open one
 * ("8+", "12+") offers three above its printed floor: the book names no top,
 * and a stepper that could run to a hundred is not a choice a GM needs made
 * for them at the table.
 * ponytail: widen it if a GM asks.
 */
internal fun spiChoices(weight: RitualWeight): List<Int> {
    val top = weight.spiMax ?: weight.spi
    return (weight.spi..(if (weight.spiOpen) top + OPEN_END_STEPS else top)).toList()
}

/**
 * The invocation bonus a ritual takes: none when the caster prays to
 * themselves, +2 for an orthodox deity in good standing (the owner's ruling,
 * 2026-10-19), else the GM's +1..+5.
 */
internal fun invocationApplied(invoked: Boolean, orthodox: Boolean, gmValue: Int): Int? = when {
    !invoked -> null
    orthodox -> PowerSources.ORTHODOX_BONUS
    else -> gmValue
}

/**
 * The HP sacrifices to offer against a [cost], as whole SPI bought.
 *
 * Stepped in SPI-sized pairs because that is the only granularity the book
 * sells: "every 2 HP spent counts as 1 SPI", so an odd point buys nothing and
 * a chip offering one would be a chip for wasting a hit point.
 * ponytail: four steps covers the rungs a caster survives.
 */
internal fun hpChoices(cost: Int): List<Int> =
    (0..minOf(cost, 4)).map { PowerSources.hpFor(it) }

/**
 * The GM's pick inside one cell, when the cell prints more than a number.
 *
 * "The GM determines the Preparation Modifier from the player's declarations"
 * (p. 63), and some cells of chapter 7's three tables hand them a span or a
 * floor to determine it from -- `ModifierRangeTest` holds the list. A cell that
 * prints one figure draws it as one disabled chip: drawing nothing there made
 * a span pick grow the dialog under the finger (play session 4, 6gn).
 *
 * [applied] is what the ENGINE says the roll is using, not what was tapped:
 * the chip showing selected before anything is tapped is a claim about the
 * default, and there is one authority on that (6dz).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CellValueChips(printed: String, values: List<Int>, applied: Int, onPick: (Int) -> Unit) {
    val isSpan = values.size >= 2
    Text(
        if (isSpan) "The book prints $printed — the GM picks:" else "The book prints $printed.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp),
    )
    FlowRow {
        (if (isSpan) values else listOf(applied)).forEach { value ->
            CaseChip(
                selected = applied == value,
                enabled = isSpan,
                onClick = { if (isSpan) onPick(value) },
                label = { Text(signed(value)) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/**
 * Ch. 9's declarations, from Fuel to Invocation: a view of [RitualDialog]'s
 * state, which it owns. Every setter here takes an absolute value or a step;
 * the toggles are defined by the dialog so they read the state live.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RitualPowerSourceChips(
    weight: RitualWeight,
    complexity: RitualComplexity,
    spiChoice: Int,
    cost: Int,
    hpSacrificed: Int,
    pacing: RitualPacing,
    assistants: Int,
    sacrifice: Int,
    suspended: Boolean,
    invoked: Boolean,
    orthodox: Boolean,
    invocation: Int,
    onSpiChoice: (Int) -> Unit,
    onHpSacrificed: (Int) -> Unit,
    onPacing: (RitualPacing) -> Unit,
    onAssistants: (Int) -> Unit,
    onSacrificeStep: (Int) -> Unit,
    onToggleSuspended: () -> Unit,
    onInvoked: (Boolean) -> Unit,
    onToggleOrthodox: () -> Unit,
    onInvocationStep: (Int) -> Unit,
) {
    // The weight's own SPI cell: "Light 2, Moderate 4-6, Heavy 8+, Major 12+".
    SectionLabel("Fuel")
    FlowRow {
        spiChoices(weight).forEach { option ->
            CaseChip(
                selected = option == spiChoice,
                onClick = { onSpiChoice(option) },
                label = { Text("$option SPI") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }

    // Fuel, never a bonus -- the panel says so out loud, because the
    // sentence next to it in the book is the one that gets misread.
    SectionLabel("Paid in HP (${PowerSources.HP_PER_SPI} HP = 1 SPI, no bonus to the roll)")
    FlowRow {
        hpChoices(cost).forEach { option ->
            CaseChip(
                selected = option == hpSacrificed,
                onClick = { onHpSacrificed(option) },
                label = { Text(if (option == 0) "None" else "$option HP") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }

    // The Time & Complexity table on the weight's own name: Rushed and
    // Extended differ BY RUNG, -3 at Light and -5 at Major.
    SectionLabel("Pacing")
    FlowRow {
        RitualPacing.entries.forEach { option ->
            val shift = PowerSources.pacingModifier(complexity, option)
            CaseChip(
                selected = option == pacing,
                onClick = { onPacing(option) },
                label = {
                    Text(
                        when (option) {
                            RitualPacing.RUSHED -> "Rushed ${signed(shift)}"
                            RitualPacing.NORMAL -> complexity.baseTime
                            RitualPacing.EXTENDED -> "Extended ${signed(shift)}"
                        },
                    )
                },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }

    // "Each assistant beyond the first gives +1 (max +3)": the caster is the
    // first (the owner's ruling, 2026-10-19).
    SectionLabel("Assistants")
    FlowRow {
        (0..4).forEach { option ->
            CaseChip(
                selected = option == assistants,
                onClick = { onAssistants(option) },
                label = {
                    Text(
                        if (option == 0) "None"
                        else "$option ${signed(PowerSources.participantBonus(option))}",
                    )
                },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    // Drawn from the start, so picking a fourth helper cannot grow the
    // dialog under the finger.
    Text(
        if (assistants > PowerSources.PARTICIPANT_CAP) {
            "Capped at ${signed(PowerSources.PARTICIPANT_CAP)}; the rest still pool their SPI."
        } else {
            "+1 each, capped at ${signed(PowerSources.PARTICIPANT_CAP)}."
        },
        style = MaterialTheme.typography.bodySmall,
    )

    // "Sacrifice quality ranges from -40 to +40 ... at GM's discretion."
    OptionStepper(
        label = "Sacrifice or catalyst",
        value = sacrifice,
        shown = signed(sacrifice),
        note = "the GM's call, ${PowerSources.SACRIFICE_LOWEST} to " +
            "${signed(PowerSources.SACRIFICE_HIGHEST)}",
        onStep = onSacrificeStep,
    )

    // Not a power source, but the same declaration: it moves the skill.
    CaseChip(
        selected = suspended,
        onClick = onToggleSuspended,
        label = {
            Text("Suspended part-way ${signed(PowerSources.SUSPENSION_PENALTY)}")
        },
        modifier = Modifier.padding(top = 8.dp),
    )

    SectionLabel("Power source")
    FlowRow {
        CaseChip(
            selected = !invoked,
            onClick = { onInvoked(false) },
            label = { Text("Personal SPI") },
            modifier = Modifier.padding(end = 8.dp),
        )
        CaseChip(
            selected = invoked,
            onClick = { onInvoked(true) },
            label = { Text("Invocation") },
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    // Both drawn under Personal SPI too, disabled: appearing on the
    // Invocation pick moved the rows below it (play session 4).
    CaseChip(
        selected = invoked && orthodox,
        enabled = invoked,
        onClick = { if (invoked) onToggleOrthodox() },
        label = { Text("Orthodox deity, in good standing ${signed(PowerSources.ORTHODOX_BONUS)}") },
    )
    OptionStepper(
        label = "Invocation",
        value = invocation,
        shown = signed(invocationApplied(invoked, orthodox, invocation) ?: 0),
        note = when {
            !invoked -> "praying to oneself adds nothing"
            orthodox -> "an orthodox deity's +${PowerSources.ORTHODOX_BONUS}"
            else -> "the GM's call, ${signed(PowerSources.INVOCATION_LOWEST)} to " +
                signed(PowerSources.INVOCATION_HIGHEST)
        },
        onStep = { if (invoked && !orthodox) onInvocationStep(it) },
    )
}

/**
 * Ch. 9's Revelation Penalties (v6.9i 4008): the entity a ritual petitions
 * for revelations. The sacrifice stepper is drawn from the start so a pick
 * moves no row, and steps only for the Seven Lights, whose row prints one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RevelationPicker(
    revelation: RitualRevelation?,
    sacrifices: Int,
    onRevelation: (RitualRevelation?) -> Unit,
    onSacrificeStep: (Int) -> Unit,
) {
    SectionLabel("Petitioning for revelations")
    FlowRow {
        CaseChip(
            selected = revelation == null,
            onClick = { onRevelation(null) },
            label = { Text("No entity") },
            modifier = Modifier.padding(end = 8.dp),
        )
        PowerSources.revelations().forEach { row ->
            CaseChip(
                selected = revelation == row,
                onClick = { onRevelation(row) },
                label = { Text("${row.entity} ${row.penalty}") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    val reduces = revelation?.perSacrifice != null
    OptionStepper(
        label = "Sacrifices to appease",
        value = sacrifices,
        shown = signed(revelation?.let { PowerSources.revelationPenalty(it, if (reduces) sacrifices else 0) } ?: 0),
        note = when {
            revelation == null -> "no entity petitioned"
            reduces -> revelation.reduction
            else -> "no sacrifice reduces it; Blessed by Deity is in no advantage table"
        },
        onStep = { if (reduces) onSacrificeStep(it) },
    )
}

/** The tongue a ritual or a prayer is said in: the GM's declaration. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MysticalLanguagePicker(
    language: LanguageProficiency?,
    boughtLanguages: List<String>,
    grantedLanguages: List<String>,
    onLanguage: (LanguageProficiency?) -> Unit,
) {
    // Ch. 3, p. 14. Only the MYSTICAL table speaks about
    // rituals; a common tongue is not offered, because the
    // book never grants it ritual power and offering it would
    // read as permission.
    SectionLabel("Mystical language")
    FlowRow {
        CaseChip(
            selected = language == null,
            onClick = { onLanguage(null) },
            label = { Text("Not stated") },
            modifier = Modifier.padding(end = 8.dp),
        )
        Languages.proficiencies(Languages.MYSTICAL).forEach { option ->
            CaseChip(
                selected = language?.proficiency == option.proficiency,
                onClick = { onLanguage(option) },
                label = { Text(option.proficiency) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    if (boughtLanguages.isNotEmpty() || grantedLanguages.isNotEmpty()) {
        Text(
            listOfNotNull(
                boughtLanguages.takeIf { it.isNotEmpty() }
                    ?.let { "Bought by this character: ${it.joinToString("; ")}." },
                grantedLanguages.takeIf { it.isNotEmpty() }
                    ?.let { "From the potion: ${it.joinToString("; ")}." },
            ).joinToString(" ") +
                " A trait may grant others, and which " +
                "language this ritual uses is yours to say.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
